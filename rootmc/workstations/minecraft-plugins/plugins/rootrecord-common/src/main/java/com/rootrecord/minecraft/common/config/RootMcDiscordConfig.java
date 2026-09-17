package com.rootrecord.minecraft.common.config;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Discord bot + named channels from {@code plugins/RootMC/cloud.yml}.
 * Prefer {@code discord.*}; fall back to legacy {@code discord-chat.*}.
 * Worker/API Discord destinations are Cloudflare wrangler vars — not this file.
 */
public final class RootMcDiscordConfig {

    public static final String CHANNEL_INGAME_CHAT = "ingame-chat";
    public static final String CHANNEL_SERVER_LOGS = "server-logs";
    public static final String ROLE_LINKED = "linked";

    private RootMcDiscordConfig() {}

    public record DiscordSettings(
            String botToken,
            String guildId,
            String ingameChatChannelId,
            String serverLogsChannelId,
            String linkedRoleId) {

        public boolean hasBotToken() {
            return botToken != null && !botToken.isBlank();
        }

        public boolean hasGuild() {
            return guildId != null && !guildId.isBlank();
        }

        public boolean hasIngameChatChannel() {
            return ingameChatChannelId != null && !ingameChatChannelId.isBlank();
        }

        /** Token + guild + ingame-chat channel present. */
        public boolean hasChatBridgeCredentials() {
            return hasBotToken() && hasGuild() && hasIngameChatChannel();
        }

        public String channel(String name) {
            if (name == null) {
                return "";
            }
            return switch (name.trim().toLowerCase()) {
                case CHANNEL_INGAME_CHAT, "ingamechat", "ingame_chat" -> nullToEmpty(ingameChatChannelId);
                case CHANNEL_SERVER_LOGS, "serverlogs", "server_logs" -> nullToEmpty(serverLogsChannelId);
                default -> "";
            };
        }

        public String role(String name) {
            if (name == null) {
                return "";
            }
            return switch (name.trim().toLowerCase()) {
                case ROLE_LINKED -> nullToEmpty(linkedRoleId);
                default -> "";
            };
        }

        private static String nullToEmpty(String v) {
            return v == null ? "" : v;
        }
    }

    public static DiscordSettings resolve(JavaPlugin plugin) {
        File file = RootRecordFolders.configFile(plugin, RootRecordCloudConfig.FILE_NAME);
        if (!file.isFile()) {
            return empty();
        }
        return fromYaml(YamlConfiguration.loadConfiguration(file));
    }

    public static DiscordSettings fromYaml(FileConfiguration cloud) {
        if (cloud == null) {
            return empty();
        }
        String token = firstNonBlank(
                cloud.getString("discord.bot-token"),
                cloud.getString("discord-chat.bot-token"));
        String guild = firstNonBlank(
                cloud.getString("discord.guild-id"),
                cloud.getString("discord-chat.guild-id"));
        String ingame = firstNonBlank(
                cloud.getString("discord.channels.ingame-chat"),
                cloud.getString("discord-chat.channel-id"));
        String logs = firstNonBlank(cloud.getString("discord.channels.server-logs"));
        String linked = firstNonBlank(cloud.getString("discord.roles.linked"));
        return new DiscordSettings(token, guild, ingame, logs, linked);
    }

    public static DiscordSettings empty() {
        return new DiscordSettings("", "", "", "", "");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
