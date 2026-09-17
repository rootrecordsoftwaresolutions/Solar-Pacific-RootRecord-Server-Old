package com.rootrecord.minecraft.roothelp.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record HelpConfig(
        String discordInviteUrl,
        String discordGuildName,
        String verifyUrl,
        String wikiCommandsUrl,
        String mapBaseUrl,
        int linesPerPage,
        List<String> rules) {

    public static HelpConfig from(FileConfiguration cfg) {
        if (cfg == null) {
            return defaults();
        }
        List<String> rules = cfg.getStringList("rules");
        if (rules.isEmpty()) {
            rules = defaults().rules;
        }
        return new HelpConfig(
                cfg.getString("discord.invite-url", defaults().discordInviteUrl),
                cfg.getString("discord.guild-name", defaults().discordGuildName),
                cfg.getString("discord.verify-url", defaults().verifyUrl),
                cfg.getString("discord.wiki-commands-url", defaults().wikiCommandsUrl),
                cfg.getString("map.base-url", ""),
                Math.max(4, cfg.getInt("commands.lines-per-page", 10)),
                List.copyOf(rules));
    }

    private static HelpConfig defaults() {
        return new HelpConfig(
                "https://discord.gg/rFFQYrNaqS",
                "RootMC Discord",
                "https://rootmc.net/verify",
                "https://rootmc.net/wiki/player/#commands",
                "",
                10,
                List.of(
                        "&cNo cheating. &7No x-ray, dupes, macros, hacked clients, automation, or exploit abuse. Report bugs instead of using them.",
                        "&cNo harassment. &7No slurs, threats, sexual content toward players, targeted toxicity, or trying to drive people off the server.",
                        "&eRespect land. &7Do not grief claims/towns, bypass protections, lava/water grief, or abuse wilderness edges to damage builds.",
                        "&eLand matters. &7Claim or join protected land. Local rules apply as long as they follow server rules.",
                        "&6Gold economy. &7Gold (G) is player-earned through mining, trade, shops, loans, bonds, and taxes. Do not fake markets or manipulate bugs.",
                        "&6Fair shops. &7Shop prices must stay within the configured market cap. Predatory pricing or bypass tricks can be removed.",
                        "&bPvP and conflict. &7PvP is limited by server settings and consent systems. Do not use traps or mechanics to bypass intended protections.",
                        "&bStaff tools. &7Do not ask for command cheats like /heal, /feed, /repair, or item spawning. Staff-only means staff-only.",
                        "&aGet help. &7Use chat or Discord for questions. Link with &f/link &7for stats, app features, and Discord verification."));
    }
}
