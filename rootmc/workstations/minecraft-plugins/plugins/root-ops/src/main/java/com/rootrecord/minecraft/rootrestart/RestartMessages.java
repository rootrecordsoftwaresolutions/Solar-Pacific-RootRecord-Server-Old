package com.rootrecord.minecraft.rootrestart;

import org.bukkit.ChatColor;

public final class RestartMessages {

    private RestartMessages() {}

    public static String formatDuration(int totalSeconds) {
        if (totalSeconds >= 120) {
            int minutes = totalSeconds / 60;
            return minutes + " minutes";
        }
        if (totalSeconds == 60) {
            return "1 minute";
        }
        if (totalSeconds == 1) {
            return "1 second";
        }
        return totalSeconds + " seconds";
    }

    public static String stripColor(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.stripColor(input);
    }
}
