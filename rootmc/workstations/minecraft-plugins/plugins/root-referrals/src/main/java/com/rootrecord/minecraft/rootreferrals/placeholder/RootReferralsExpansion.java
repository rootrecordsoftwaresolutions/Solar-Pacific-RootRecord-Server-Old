package com.rootrecord.minecraft.rootreferrals.placeholder;

import com.rootrecord.minecraft.rootreferrals.RootReferralsPlugin;
import com.rootrecord.minecraft.rootreferrals.store.ReferralStore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.Optional;

public final class RootReferralsExpansion extends PlaceholderExpansion {

    private final RootReferralsPlugin plugin;

    public RootReferralsExpansion(RootReferralsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "rootreferrals";
    }

    @Override
    public String getAuthor() {
        return "Root Record";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || player == null || player.getUniqueId() == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        Optional<ReferralStore.ReferralPlayer> profileOpt = plugin.store().findPlayer(player.getUniqueId());
        return switch (key) {
            case "qualified" -> profileOpt.map(p -> String.valueOf(p.totalQualified())).orElse("0");
            case "code" -> profileOpt.map(ReferralStore.ReferralPlayer::code).orElse("");
            case "pending" -> String.valueOf(plugin.store().countPendingByReferrer(player.getUniqueId()));
            default -> null;
        };
    }
}
