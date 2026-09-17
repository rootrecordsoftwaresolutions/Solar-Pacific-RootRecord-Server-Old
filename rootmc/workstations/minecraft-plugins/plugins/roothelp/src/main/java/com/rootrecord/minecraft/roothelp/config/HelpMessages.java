package com.rootrecord.minecraft.roothelp.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public final class HelpMessages {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("prefix", ""),
            Map.entry("rules-header", "&7Server rules:"),
            Map.entry("rules-footer", "&7Full guide: &bhttps://rootmc.net/wiki/player/"),
            Map.entry("cmds-header", "&7Commands &8(&f{page}&7/&f{pages}&7) &8— &7click a page or Next"),
            Map.entry("cmds-empty", "&eNo commands configured."),
            Map.entry("cmds-page-invalid", "&ePage &f{page}&e is out of range (&f1&7–&f{pages}&e)."),
            Map.entry("discord-header", "&7RootMC Discord & link status"),
            Map.entry("discord-invite", "&7Join the community:"),
            Map.entry("discord-mc-unlinked", "&7Minecraft: &eNot linked &8— &7use &f/link"),
            Map.entry("discord-mc-linked", "&7Minecraft: &aLinked &7(&f{account}&7)"),
            Map.entry("discord-discord-unlinked", "&7Discord: &eNot linked &8— &7open verify, run &f/link&7 in-game, then &fLink with Discord&7 to chat"),
            Map.entry("discord-discord-linked", "&7Discord: &aLinked &7as &f{user}"),
            Map.entry("discord-both-linked", "&aYour game and Discord profiles are linked to RootRecord."),
            Map.entry("discord-stats", "&7Public stats:"),
            Map.entry("discord-no-cloud", "&cRootRecord cloud credentials missing — ask staff."),
            Map.entry("discord-fetch-fail", "&cCould not load link status: &f{error}"),
            Map.entry("map-header", "&7RootMC live map &8— &7click to open in your browser:"),
            Map.entry("map-unconfigured", "&cLive map unavailable — ask staff to check server.map-url in rootmc.yml."),
            Map.entry("feedback-usage", "&7Usage: &f/feedback <message>&7 from your claim or territory — bugs, suggestions, or praise."),
            Map.entry("feedback-sent", "&aThanks - your feedback was sent to staff."),
            Map.entry("feedback-too-long", "&eFeedback is limited to &f{max}&e characters."),
            Map.entry("feedback-no-cloud", "&cFeedback is unavailable - cloud credentials missing. Tell staff."),
            Map.entry("feedback-fail", "&cCould not send feedback: &f{error}"),
            Map.entry("feedback-not-in-land", "&cStand in &fyour claim or territory&c to send feedback."),
            Map.entry("feedback-claims-offline", "&cClaims are offline — feedback is paused. Tell staff."));

    private final String prefix;
    private final Map<String, String> byKey;

    private HelpMessages(String prefix, Map<String, String> byKey) {
        this.prefix = prefix;
        this.byKey = byKey;
    }

    public static HelpMessages from(FileConfiguration cfg) {
        Map<String, String> merged = new HashMap<>(DEFAULTS);
        if (cfg != null) {
            var section = cfg.getConfigurationSection("messages");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    merged.put(key, section.getString(key, merged.get(key)));
                }
            }
        }
        return new HelpMessages(merged.getOrDefault("prefix", ""), merged);
    }

    public String prefix() {
        return prefix;
    }

    public String get(String key) {
        return byKey.getOrDefault(key, "");
    }
}
