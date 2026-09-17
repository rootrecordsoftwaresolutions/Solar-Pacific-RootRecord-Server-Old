package com.rootrecord.minecraft.rootpotions.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PotionsMessages(
        String prefix,
        java.util.List<String> help,
        String notFound,
        String listHeader,
        String listEntry,
        String listFooter,
        String listPrevFooter,
        String recipeHeader,
        String reloadDone,
        String noPermission) {

    public static PotionsMessages from(FileConfiguration config) {
        return new PotionsMessages(
                config.getString("messages.prefix", ""),
                config.getStringList("messages.help"),
                config.getString("messages.not-found", "&cNo potion matched &f{query}&c."),
                config.getString("messages.list-header", "&7Potion recipes"),
                config.getString("messages.list-entry", "&8• &f{name}"),
                config.getString("messages.list-footer", ""),
                config.getString("messages.list-prev-footer", ""),
                config.getString("messages.recipe-header", "&a{name}"),
                config.getString("messages.reload-done", "&aRoot-Potions reloaded."),
                config.getString("messages.no-permission", "&cYou do not have permission."));
    }

    public String get(String key, String fallback) {
        return switch (key) {
            case "not-found" -> notFound;
            case "list-header" -> listHeader;
            case "list-entry" -> listEntry;
            case "list-footer" -> listFooter;
            case "list-prev-footer" -> listPrevFooter;
            case "recipe-header" -> recipeHeader;
            case "reload-done" -> reloadDone;
            case "no-permission" -> noPermission;
            default -> fallback;
        };
    }
}
