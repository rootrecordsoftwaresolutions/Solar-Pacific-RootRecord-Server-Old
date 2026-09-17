package com.rootrecord.minecraft.rootessentials.web;

import org.bukkit.Bukkit;

/**
 * In-game web links. Singular live RootMC — one economy page, no Towny/Claims/g2 split.
 */
public final class RootMcEconomyWeb {

    private static final String BASE = "https://rootmc.net";

    private RootMcEconomyWeb() {}

    /** Towny plugin present (legacy). Live production is claims /c only. */
    public static boolean townyHost() {
        return Bukkit.getPluginManager().getPlugin("Towny") != null;
    }

    public static boolean claimsHost() {
        return !townyHost();
    }

    public static String economy() {
        return BASE + "/economy";
    }

    public static String reserve() {
        return economy() + "#server-reserve";
    }

    public static String market() {
        return BASE + "/market/";
    }

    public static String leaderboard() {
        return BASE + "/leaderboard/";
    }

    public static String bonds() {
        return economy();
    }

    public static String list() {
        return economy();
    }

    public static String constitution() {
        return BASE + "/wiki/constitution/";
    }
}
