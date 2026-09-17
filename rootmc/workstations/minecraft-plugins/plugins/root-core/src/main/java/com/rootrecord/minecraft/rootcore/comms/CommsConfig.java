package com.rootrecord.minecraft.rootcore.comms;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDiscordConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Discord chat + Slack server-logs settings.
 * Secrets from cloud.yml; chat flags from root-core.yml {@code comms} (with root-discord.yml migrate).
 */
public final class CommsConfig {

    private final boolean chatEnabled;
    private final String inboundFormat;
    private final boolean relayChat;
    private final boolean relayJoin;
    private final boolean relayLeave;
    private final boolean relayDeath;
    private final boolean relayReachout;
    private final String botToken;
    private final String guildId;
    private final String channelId;
    private final String serverLogsChannelId;
    private final String slackServerLogsWebhook;
    private final boolean slackServerLogsEnabled;
    private final String serverTag;
    private final String allowedRoleId;

    private CommsConfig(
            boolean chatEnabled,
            String inboundFormat,
            boolean relayChat,
            boolean relayJoin,
            boolean relayLeave,
            boolean relayDeath,
            boolean relayReachout,
            String botToken,
            String guildId,
            String channelId,
            String serverLogsChannelId,
            String slackServerLogsWebhook,
            boolean slackServerLogsEnabled,
            String serverTag,
            String allowedRoleId) {
        this.chatEnabled = chatEnabled;
        this.inboundFormat = inboundFormat;
        this.relayChat = relayChat;
        this.relayJoin = relayJoin;
        this.relayLeave = relayLeave;
        this.relayDeath = relayDeath;
        this.relayReachout = relayReachout;
        this.botToken = botToken;
        this.guildId = guildId;
        this.channelId = channelId;
        this.serverLogsChannelId = serverLogsChannelId;
        this.slackServerLogsWebhook = slackServerLogsWebhook;
        this.slackServerLogsEnabled = slackServerLogsEnabled;
        this.serverTag = serverTag;
        this.allowedRoleId = allowedRoleId;
    }

    public static CommsConfig from(JavaPlugin plugin, FileConfiguration coreYaml) {
        migrateLegacyDiscordYaml(plugin, coreYaml);
        FileConfiguration chatSrc = mergeChatSources(plugin, coreYaml);
        RootMcDiscordConfig.DiscordSettings discord = RootMcDiscordConfig.resolve(plugin);
        File cloudFile = RootRecordFolders.configFile(plugin, "cloud.yml");
        FileConfiguration cloudYaml = cloudFile.isFile()
                ? YamlConfiguration.loadConfiguration(cloudFile)
                : new YamlConfiguration();
        String slackWebhook = SlackServerLogsClient.resolveWebhook(plugin, cloudYaml);
        String roleFromYaml = firstNonBlank(
                chatSrc.getString("comms.discord.chat.allowed-role-id"),
                chatSrc.getString("chat.allowed-role-id"),
                chatSrc.getString("discord-chat.allowed-role-id"));
        String allowedRole = !discord.linkedRoleId().isBlank() ? discord.linkedRoleId() : roleFromYaml;
        boolean chatEnabled = firstBool(chatSrc, true,
                "comms.discord.chat.enabled", "chat.enabled", "discord-chat.enabled");
        boolean slackLogs = firstBool(chatSrc, true,
                "comms.slack.server-logs.enabled", "slack.server-logs.enabled");
        return new CommsConfig(
                chatEnabled,
                firstString(chatSrc,
                        "&8▎ &9Discord&8│ &f{user}&8 &7»&f {message}",
                        "comms.discord.chat.inbound-format",
                        "chat.inbound-format",
                        "discord-chat.inbound-format"),
                firstBool(chatSrc, true, "comms.discord.chat.relay-chat", "chat.relay-chat", "discord-chat.relay-chat"),
                firstBool(chatSrc, true, "comms.discord.chat.relay-join", "chat.relay-join", "discord-chat.relay-join"),
                firstBool(chatSrc, true, "comms.discord.chat.relay-leave", "chat.relay-leave", "discord-chat.relay-leave"),
                firstBool(chatSrc, true, "comms.discord.chat.relay-death", "chat.relay-death", "discord-chat.relay-death"),
                firstBool(chatSrc, true,
                        "comms.discord.chat.relay-reachout", "chat.relay-reachout", "discord-chat.relay-reachout"),
                discord.botToken(),
                discord.guildId(),
                discord.ingameChatChannelId(),
                discord.serverLogsChannelId(),
                slackWebhook,
                slackLogs,
                normalizeServerTag(firstString(chatSrc, "T",
                        "comms.discord.chat.server-tag", "chat.server-tag", "discord-chat.server-tag")),
                allowedRole);
    }

    /**
     * One-time: copy root-discord.yml chat flags into root-core.yml {@code comms} when missing.
     */
    public static void migrateLegacyDiscordYaml(JavaPlugin plugin, FileConfiguration coreYaml) {
        if (plugin == null || coreYaml == null) {
            return;
        }
        File legacy = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_DISCORD_CONFIG);
        if (!legacy.isFile()) {
            return;
        }
        ConfigurationSection existing = coreYaml.getConfigurationSection("comms.discord.chat");
        if (existing != null && !existing.getKeys(false).isEmpty()) {
            return;
        }
        FileConfiguration discordYaml = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection chat = discordYaml.getConfigurationSection("chat");
        if (chat == null || chat.getKeys(false).isEmpty()) {
            return;
        }
        for (String key : chat.getKeys(false)) {
            coreYaml.set("comms.discord.chat." + key, chat.get(key));
        }
        if (!coreYaml.contains("comms.slack.server-logs.enabled")) {
            coreYaml.set("comms.slack.server-logs.enabled", true);
        }
        try {
            File coreFile = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_CORE_CONFIG);
            coreYaml.save(coreFile);
            File migrated = new File(legacy.getParentFile(), legacy.getName() + ".migrated");
            Files.move(legacy.toPath(), migrated.toPath());
            plugin.getLogger().info("Migrated root-discord.yml chat flags → root-core.yml comms (backup "
                    + migrated.getName() + ").");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Comms migrate save failed: " + ex.getMessage());
        }
    }

    private static FileConfiguration mergeChatSources(JavaPlugin plugin, FileConfiguration coreYaml) {
        FileConfiguration cfg = coreYaml != null ? coreYaml : new YamlConfiguration();
        File legacyDiscord = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_DISCORD_CONFIG);
        if (legacyDiscord.isFile()) {
            FileConfiguration discordYaml = YamlConfiguration.loadConfiguration(legacyDiscord);
            ConfigurationSection chat = discordYaml.getConfigurationSection("chat");
            if (chat != null) {
                for (String key : chat.getKeys(false)) {
                    String path = "chat." + key;
                    if (!cfg.contains("comms.discord.chat." + key) && !cfg.contains(path)) {
                        cfg.set(path, chat.get(key));
                    }
                }
            }
        }
        File legacyRootMc = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOTMC_CONFIG);
        if (legacyRootMc.isFile()) {
            FileConfiguration legacy = YamlConfiguration.loadConfiguration(legacyRootMc);
            ConfigurationSection section = legacy.getConfigurationSection("discord-chat");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if (!cfg.contains("comms.discord.chat." + key)
                            && !cfg.contains("chat." + key)
                            && !cfg.contains("discord-chat." + key)) {
                        cfg.set("discord-chat." + key, section.get(key));
                    }
                }
            }
        }
        return cfg;
    }

    private static boolean firstBool(FileConfiguration cfg, boolean def, String... paths) {
        for (String path : paths) {
            if (cfg.contains(path)) {
                return cfg.getBoolean(path);
            }
        }
        return def;
    }

    private static String firstString(FileConfiguration cfg, String def, String... paths) {
        for (String path : paths) {
            if (cfg.contains(path)) {
                String v = cfg.getString(path);
                if (v != null) {
                    return v;
                }
            }
        }
        return def;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String normalizeServerTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return "T";
        }
        String t = raw.trim().toUpperCase();
        if (t.equals("1") || t.equals("GEN1") || t.equals("G1") || t.equals("TOWNY")) {
            return "T";
        }
        if (t.equals("2") || t.equals("GEN2") || t.equals("G2") || t.equals("CLAIMS")) {
            return "C";
        }
        return t;
    }

    public boolean shouldConnectBot() {
        return !botToken.isBlank() && !guildId.isBlank();
    }

    public boolean chatEnabled() {
        return chatEnabled;
    }

    public String inboundFormat() {
        return inboundFormat;
    }

    public boolean relayChat() {
        return relayChat;
    }

    public boolean relayJoin() {
        return relayJoin;
    }

    public boolean relayLeave() {
        return relayLeave;
    }

    public boolean relayDeath() {
        return relayDeath;
    }

    public boolean relayReachout() {
        return relayReachout;
    }

    public String botToken() {
        return botToken;
    }

    public String guildId() {
        return guildId;
    }

    public String channelId() {
        return channelId;
    }

    public String serverLogsChannelId() {
        return serverLogsChannelId;
    }

    public String slackServerLogsWebhook() {
        return slackServerLogsWebhook;
    }

    public boolean slackServerLogsEnabled() {
        return slackServerLogsEnabled;
    }

    public String serverTag() {
        return serverTag;
    }

    public String allowedRoleId() {
        return allowedRoleId;
    }
}
