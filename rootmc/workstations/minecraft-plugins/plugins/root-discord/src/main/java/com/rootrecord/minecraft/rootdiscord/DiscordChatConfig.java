package com.rootrecord.minecraft.rootdiscord;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDiscordConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** Chat + secret settings for Root-Discord (YAML + cloud.yml). */
public final class DiscordChatConfig {

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
    private final String serverTag;
    private final String allowedRoleId;

    private DiscordChatConfig(
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
        this.serverTag = serverTag;
        this.allowedRoleId = allowedRoleId;
    }

    public static DiscordChatConfig from(JavaPlugin plugin, FileConfiguration discordYaml) {
        FileConfiguration cfg = mergeWithLegacyRootMc(plugin, discordYaml);
        RootMcDiscordConfig.DiscordSettings discord = RootMcDiscordConfig.resolve(plugin);
        File cloudFile = RootRecordFolders.configFile(plugin, "cloud.yml");
        FileConfiguration cloudYaml = cloudFile.isFile()
                ? YamlConfiguration.loadConfiguration(cloudFile)
                : new YamlConfiguration();
        String slackWebhook = SlackServerLogsClient.resolveWebhook(plugin, cloudYaml);
        String roleFromYaml = trim(cfg.getString("chat.allowed-role-id"));
        if (roleFromYaml.isBlank()) {
            roleFromYaml = trim(cfg.getString("discord-chat.allowed-role-id"));
        }
        String allowedRole = !discord.linkedRoleId().isBlank() ? discord.linkedRoleId() : roleFromYaml;
        boolean chatEnabled = sectionBool(cfg, "chat.enabled", "discord-chat.enabled", true);
        return new DiscordChatConfig(
                chatEnabled,
                sectionString(
                        cfg,
                        "chat.inbound-format",
                        "discord-chat.inbound-format",
                        "&8▎ &9Discord&8│ &f{user}&8 &7»&f {message}"),
                sectionBool(cfg, "chat.relay-chat", "discord-chat.relay-chat", true),
                sectionBool(cfg, "chat.relay-join", "discord-chat.relay-join", true),
                sectionBool(cfg, "chat.relay-leave", "discord-chat.relay-leave", true),
                sectionBool(cfg, "chat.relay-death", "discord-chat.relay-death", true),
                sectionBool(cfg, "chat.relay-reachout", "discord-chat.relay-reachout", true),
                discord.botToken(),
                discord.guildId(),
                discord.ingameChatChannelId(),
                discord.serverLogsChannelId(),
                slackWebhook,
                normalizeServerTag(sectionString(cfg, "chat.server-tag", "discord-chat.server-tag", "T")),
                allowedRole);
    }

    /** Prefer root-discord.yml; overlay missing keys from legacy rootmc.yml discord-chat.*. */
    private static FileConfiguration mergeWithLegacyRootMc(JavaPlugin plugin, FileConfiguration discordYaml) {
        FileConfiguration cfg = discordYaml != null ? discordYaml : new YamlConfiguration();
        File legacyFile = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOTMC_CONFIG);
        if (!legacyFile.isFile()) {
            return cfg;
        }
        FileConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        if (!legacy.isConfigurationSection("discord-chat")) {
            return cfg;
        }
        // Only used when root-discord chat section is empty / first boot with old hosts.
        if (!cfg.isConfigurationSection("chat")
                || cfg.getConfigurationSection("chat") == null
                || cfg.getConfigurationSection("chat").getKeys(false).isEmpty()) {
            var section = legacy.getConfigurationSection("discord-chat");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    cfg.set("discord-chat." + key, section.get(key));
                }
            }
        }
        return cfg;
    }

    private static boolean sectionBool(FileConfiguration cfg, String primary, String legacy, boolean def) {
        if (cfg.contains(primary)) {
            return cfg.getBoolean(primary);
        }
        if (cfg.contains(legacy)) {
            return cfg.getBoolean(legacy);
        }
        return def;
    }

    private static String sectionString(FileConfiguration cfg, String primary, String legacy, String def) {
        if (cfg.contains(primary)) {
            String v = cfg.getString(primary);
            return v != null ? v : def;
        }
        if (cfg.contains(legacy)) {
            String v = cfg.getString(legacy);
            return v != null ? v : def;
        }
        return def;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
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

    /** Connect JDA when credentials exist (chat and/or log uploads). */
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

    public String serverTag() {
        return serverTag;
    }

    public String allowedRoleId() {
        return allowedRoleId;
    }
}
