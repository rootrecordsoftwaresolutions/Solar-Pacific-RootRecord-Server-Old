package com.rootrecord.minecraft.rootessentials.placeholder;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * Lightweight Essentials placeholders. Balance/baltop live on Root-Economy's expansion when present.
 */
public final class RootEssentialsExpansion extends PlaceholderExpansion {

    private final RootEssentialsPlugin plugin;

    public RootEssentialsExpansion(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "rootessentials";
    }

    @Override
    public String getAuthor() {
        return "Root Record";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT);
        if (player == null) {
            return "";
        }
        try {
            RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
            if (eco == null) {
                return "";
            }
            double balance = eco.balance(player.getUniqueId());
            return switch (key) {
                case "balance", "balance_formatted" -> plugin.money(balance);
                case "balance_int" -> String.valueOf((long) Math.floor(balance));
                default -> "";
            };
        } catch (Exception ex) {
            return "";
        }
    }
}
