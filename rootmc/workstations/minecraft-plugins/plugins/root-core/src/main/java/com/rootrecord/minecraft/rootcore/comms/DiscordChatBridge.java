package com.rootrecord.minecraft.rootcore.comms;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Single JDA session: chat bridge + reachout embeds + server-log uploads. */
public final class DiscordChatBridge implements Listener {

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)[§&][0-9a-fk-or]");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String BRIDGE_FOOTER = "rootmc-bridge:";
    private static final int MAX_QUEUE = 200;
    private static final int MAX_DISCORD_TEXT = 1800;
    private static final long RETRY_BACKOFF_MS = 30_000L;

    private final JavaPlugin plugin;
    private final CommsConfig config;
    private final ConcurrentLinkedQueue<OutboundChat> outbound = new ConcurrentLinkedQueue<>();
    private final AtomicLong lastDropWarningMs = new AtomicLong();

    private volatile JDA jda;
    private volatile long retryAfterMs;
    private BukkitTask flushTask;

    public DiscordChatBridge(JavaPlugin plugin, CommsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public CommsConfig config() {
        return config;
    }

    public boolean isReady() {
        JDA current = jda;
        return current != null && current.getStatus() == JDA.Status.CONNECTED;
    }

    public void start() {
        stop();
        if (!config.shouldConnectBot()) {
            plugin.getLogger().warning(
                    "Root-Core comms: bot-token / guild-id missing in plugins/RootMC/cloud.yml — Discord offline.");
            return;
        }
        if (config.chatEnabled() && config.channelId().isBlank()) {
            plugin.getLogger().warning(
                    "Root-Core comms chat enabled but channels.ingame-chat is blank in cloud.yml.");
        }
        if (config.chatEnabled()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
        try {
            jda = JDABuilder.createLight(
                            config.botToken(),
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordListener())
                    .build();
            flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                    plugin, this::flushOutboundSafe, 10L, 10L);
            plugin.getLogger().info(
                    "Root-Core comms connecting as [" + config.serverTag() + "] guild " + config.guildId() + ".");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Root-Core comms failed to start: " + ex.getMessage());
            stop();
        }
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        JDA current = jda;
        jda = null;
        if (current != null) {
            current.shutdown();
            try {
                if (!current.awaitShutdown(3, TimeUnit.SECONDS)) {
                    current.shutdownNow();
                    current.awaitShutdown(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                current.shutdownNow();
            }
        }
        retryAfterMs = 0L;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config.chatEnabled() || !config.relayChat()) {
            return;
        }
        String message = stripColors(serialize(event.message()));
        if (message.isBlank() || message.startsWith("/")) {
            return;
        }
        Player player = event.getPlayer();
        enqueue(player.getName(), message, "chat");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (config.chatEnabled() && config.relayJoin()) {
            enqueue(event.getPlayer().getName(), "joined the server", "join");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!config.chatEnabled() || !config.relayLeave()) {
            return;
        }
        String detail = event.quitMessage() == null
                ? "left the server"
                : stripColors(serialize(event.quitMessage()));
        enqueue(event.getPlayer().getName(), detail.isBlank() ? "left the server" : detail, "leave");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!config.chatEnabled() || !config.relayDeath()) {
            return;
        }
        String detail = stripColors(serialize(event.deathMessage()));
        enqueue(event.getEntity().getName(), detail.isBlank() ? "died" : detail, "death");
    }

    public void relayReachout(String username, String uuid, String message, String kind) {
        if (!config.relayReachout() || message == null || message.isBlank()) {
            return;
        }
        enqueue(username == null ? "Server" : username, message, kind == null ? "reachout" : kind);
    }

    public void postChatLine(String username, String message, String kind) {
        if (message == null || message.isBlank()) {
            return;
        }
        enqueue(username == null ? "Server" : username, message, kind == null ? "chat" : kind);
    }

    public void uploadServerLog(File file, String caption) {
        if (file == null || !file.isFile()) {
            return;
        }
        String content = caption == null || caption.isBlank()
                ? "[" + config.serverTag() + "] server logs"
                : caption;

        // Prefer Slack Incoming Webhook (#server-logs) — no Worker required.
        String slackWebhook = config.slackServerLogsWebhook();
        if (SlackServerLogsClient.isWebhook(slackWebhook)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean ok = SlackServerLogsClient.postLogFile(plugin, slackWebhook, content, file);
                if (ok && !file.delete()) {
                    plugin.getLogger().fine("Root-Core comms: could not delete uploaded log " + file.getName());
                }
            });
            return;
        }

        JDA current = jda;
        if (current == null || current.getStatus() != JDA.Status.CONNECTED) {
            plugin.getLogger().warning(
                    "Root-Core comms: server-logs skipped (no slack.server-logs-webhook and Discord offline).");
            return;
        }
        String channelId = config.serverLogsChannelId();
        if (channelId.isBlank()) {
            plugin.getLogger().warning(
                    "Root-Core comms: set slack.server-logs-webhook (preferred) or discord.channels.server-logs.");
            return;
        }
        TextChannel channel = current.getTextChannelById(channelId);
        if (channel == null || !channel.getGuild().getId().equals(config.guildId())) {
            plugin.getLogger().warning("Root-Core comms: server-logs channel/guild mismatch; skip upload.");
            return;
        }
        channel.sendMessage(content)
                .addFiles(FileUpload.fromData(file))
                .queue(
                        ok -> {
                            plugin.getLogger().info(
                                    "Root-Core comms: uploaded server log " + file.getName()
                                            + " to Discord #" + channel.getName());
                            if (!file.delete()) {
                                plugin.getLogger().fine("Root-Core comms: could not delete uploaded log " + file.getName());
                            }
                        },
                        err -> plugin.getLogger().warning(
                                "Root-Core comms log upload failed: " + err.getMessage()));
    }

    private void enqueue(String username, String message, String kind) {
        if (config.channelId().isBlank()) {
            return;
        }
        String cleanedMessage = cleanDiscordText(message);
        if (cleanedMessage.isBlank()) {
            return;
        }
        while (outbound.size() >= MAX_QUEUE) {
            outbound.poll();
            long now = System.currentTimeMillis();
            long previous = lastDropWarningMs.get();
            if (now - previous >= 60_000L && lastDropWarningMs.compareAndSet(previous, now)) {
                plugin.getLogger().warning("Root-Core comms queue full; dropped oldest message.");
            }
        }
        outbound.add(new OutboundChat(
                cleanName(username),
                cleanedMessage,
                normalizeKind(kind),
                Instant.now()));
    }

    private void flushOutboundSafe() {
        if (System.currentTimeMillis() < retryAfterMs) {
            return;
        }
        JDA current = jda;
        if (current == null || current.getStatus() != JDA.Status.CONNECTED) {
            return;
        }
        if (config.channelId().isBlank()) {
            return;
        }
        TextChannel channel = current.getTextChannelById(config.channelId());
        if (channel == null || !channel.getGuild().getId().equals(config.guildId())) {
            return;
        }
        for (int sent = 0; sent < 20; sent++) {
            OutboundChat row = outbound.poll();
            if (row == null) {
                return;
            }
            MessageEmbed embed = new EmbedBuilder()
                    .setAuthor("[" + config.serverTag() + "] " + row.username())
                    .setDescription(row.message())
                    .setFooter(BRIDGE_FOOTER + config.serverTag() + ":" + row.kind())
                    .setTimestamp(row.createdAt())
                    .build();
            channel.sendMessageEmbeds(embed)
                    .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                    .queue(
                            ignored -> retryAfterMs = 0L,
                            error -> {
                                outbound.add(row);
                                retryAfterMs = System.currentTimeMillis() + RETRY_BACKOFF_MS;
                                plugin.getLogger().warning(
                                        "Root-Core comms send failed; retrying after backoff: " + error.getMessage());
                            });
        }
    }

    private void handleDiscordMessage(MessageReceivedEvent event) {
        if (!config.chatEnabled()) {
            return;
        }
        if (!event.isFromGuild()
                || !event.getGuild().getId().equals(config.guildId())
                || !event.getChannel().getId().equals(config.channelId())) {
            return;
        }
        Message message = event.getMessage();
        if (event.getAuthor().isBot()) {
            if (event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
                handleBridgeMessage(message);
            }
            return;
        }
        if (message.isWebhookMessage()) {
            return;
        }
        if (!config.allowedRoleId().isBlank()
                && (event.getMember() == null
                || event.getMember().getRoles().stream()
                        .noneMatch(role -> role.getId().equals(config.allowedRoleId())))) {
            return;
        }
        String text = stripColors(message.getContentDisplay());
        if (!message.getAttachments().isEmpty()) {
            StringBuilder withAttachments = new StringBuilder(text);
            message.getAttachments().stream().limit(3).forEach(attachment -> {
                if (!withAttachments.isEmpty()) {
                    withAttachments.append(' ');
                }
                withAttachments.append(attachment.getUrl());
            });
            text = withAttachments.toString();
        }
        if (!text.isBlank()) {
            broadcastInbound(event.getAuthor().getEffectiveName(), text);
        }
    }

    private void handleBridgeMessage(Message message) {
        for (MessageEmbed embed : message.getEmbeds()) {
            MessageEmbed.Footer footer = embed.getFooter();
            if (footer == null || footer.getText() == null || !footer.getText().startsWith(BRIDGE_FOOTER)) {
                continue;
            }
            String[] marker = footer.getText().substring(BRIDGE_FOOTER.length()).split(":", 2);
            if (marker.length != 2
                    || marker[0].equalsIgnoreCase(config.serverTag())
                    || !"chat".equalsIgnoreCase(marker[1])) {
                return;
            }
            String username = "Player";
            if (embed.getAuthor() != null && embed.getAuthor().getName() != null) {
                String author = embed.getAuthor().getName();
                String prefix = "[" + marker[0].toUpperCase(Locale.ROOT) + "] ";
                username = author.startsWith(prefix) ? author.substring(prefix.length()) : author;
            }
            broadcastInbound(marker[0].toUpperCase(Locale.ROOT), username, embed.getDescription());
            return;
        }
    }

    private void broadcastInbound(String user, String message) {
        broadcastInbound("Discord", user, message);
    }

    private void broadcastInbound(String source, String user, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String format = config.inboundFormat();
        if (format.contains("{source}")) {
            format = format.replace("{source}", cleanName(source));
        } else {
            int discordAt = format.toLowerCase(Locale.ROOT).indexOf("discord");
            if (discordAt >= 0) {
                format = format.substring(0, discordAt)
                        + cleanName(source)
                        + format.substring(discordAt + "discord".length());
            }
        }
        String line = format
                .replace("{user}", cleanName(user))
                .replace("{message}", stripColors(truncate(message, MAX_DISCORD_TEXT)));
        String colored = ChatColor.translateAlternateColorCodes('&', line);
        plugin.getServer().getScheduler().runTask(
                plugin, () -> Bukkit.broadcastMessage(colored));
    }

    private static String cleanDiscordText(String value) {
        return truncate(stripColors(value), MAX_DISCORD_TEXT)
                .replace("@", "@\u200B")
                .trim();
    }

    private static String cleanName(String value) {
        return truncate(stripColors(value == null ? "Server" : value), 80)
                .replace("@", "")
                .replace("{", "")
                .replace("}", "")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();
    }

    private static String normalizeKind(String value) {
        String kind = value == null ? "chat" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return kind.isBlank() ? "chat" : truncate(kind, 32);
    }

    private static String serialize(Component component) {
        return component == null ? "" : LEGACY.serialize(component);
    }

    private static String stripColors(String value) {
        return value == null ? "" : COLOR_CODES.matcher(value).replaceAll("").trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private record OutboundChat(String username, String message, String kind, Instant createdAt) {}

    private final class DiscordListener extends ListenerAdapter {
        @Override
        public void onReady(ReadyEvent event) {
            if (!config.channelId().isBlank()) {
                TextChannel channel = event.getJDA().getTextChannelById(config.channelId());
                if (channel == null) {
                    plugin.getLogger().severe(
                            "Root-Core comms connected, but ingame-chat channel " + config.channelId()
                                    + " is unavailable.");
                } else if (!channel.getGuild().getId().equals(config.guildId())) {
                    plugin.getLogger().severe(
                            "Root-Core comms channel guild mismatch: " + channel.getGuild().getId()
                                    + " vs " + config.guildId() + ".");
                } else if (!channel.getGuild().getSelfMember().hasPermission(
                        channel,
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_SEND,
                        Permission.MESSAGE_EMBED_LINKS)) {
                    plugin.getLogger().severe(
                            "Root-Core comms lacks View/Send/Embed in #" + channel.getName() + ".");
                } else {
                    plugin.getLogger().info(
                            "Root-Core comms ready as [" + config.serverTag() + "] ("
                                    + event.getJDA().getSelfUser().getName() + ") in #"
                                    + channel.getName() + ".");
                }
            } else {
                plugin.getLogger().info(
                        "Root-Core comms bot ready (" + event.getJDA().getSelfUser().getName()
                                + ") — chat channel blank; log uploads still available.");
            }
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            handleDiscordMessage(event);
        }
    }
}
