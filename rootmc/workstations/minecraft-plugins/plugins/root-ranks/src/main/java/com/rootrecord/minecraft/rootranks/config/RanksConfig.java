package com.rootrecord.minecraft.rootranks.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record RanksConfig(
        boolean treasurySink,
        boolean purchasesEnabled,
        List<RankTier> ranks,
        String prefix,
        String noPermission,
        String playersOnly,
        String disabled,
        String noEconomy,
        String noLuckPerms,
        String maxRank,
        String nextRank,
        String rankListHeader,
        String rankListRow,
        String rankOwnedTag,
        String rankLockedTag,
        String insufficient,
        String purchaseSuccess,
        String purchaseBroadcast,
        String purchaseFailed,
        String bypassGranted,
        String reloadDone,
        String explorerBenefits,
        String rankBenefitsLine) {

    public static RanksConfig from(FileConfiguration cfg) {
        List<RankTier> ranks = new ArrayList<>();
        for (var map : cfg.getMapList("ranks")) {
            Object idRaw = map.get("id");
            String id = idRaw == null ? "" : String.valueOf(idRaw).trim().toLowerCase();
            if (id.isEmpty()) {
                continue;
            }
            Object priceRaw = map.get("price");
            double price = priceRaw instanceof Number n ? n.doubleValue() : 0.0;
            Object displayRaw = map.get("display");
            String display = displayRaw == null ? capitalize(id) : String.valueOf(displayRaw);
            Object benefitsRaw = map.get("benefits");
            String benefits = benefitsRaw == null ? "" : String.valueOf(benefitsRaw).trim();
            ranks.add(new RankTier(id, display, Math.max(0.0, price), benefits));
        }
        if (ranks.isEmpty()) {
            ranks = new ArrayList<>(defaultTiers());
        }
        return new RanksConfig(
                cfg.getBoolean("treasury_sink", true),
                cfg.getBoolean("purchases_enabled", true),
                Collections.unmodifiableList(ranks),
                cfg.getString("messages.prefix", ""),
                cfg.getString("messages.no-permission", "&cYou do not have permission."),
                cfg.getString("messages.players-only", "&cPlayers only."),
                cfg.getString("messages.disabled", "&eRank purchases are disabled."),
                cfg.getString("messages.no-economy", "&cEconomy unavailable."),
                cfg.getString("messages.no-luckperms", "&cRank system unavailable (LuckPerms missing)."),
                cfg.getString("messages.max-rank", "&aYou already hold the highest rank: &f{rank}&a."),
                cfg.getString("messages.next-rank",
                        "&7Current: &f{current}&7 · Next: &f{next} &7— &f{price} G &7(you have &f{balance} G&7)"),
                cfg.getString("messages.rank-list-header", "&5Player ranks &7— buy in order with &f/rank buy"),
                cfg.getString("messages.rank-list-row", "&7{index}. &f{display} &8— &f{price} G {owned}"),
                cfg.getString("messages.rank-owned-tag", "&a✓"),
                cfg.getString("messages.rank-locked-tag", "&8"),
                cfg.getString("messages.insufficient", "&cNeed &f{price} G&c — you have &f{balance} G&c."),
                cfg.getString("messages.purchase-success", "&aRank up! You are now &f{rank}&a. &7(-{price} G)"),
                cfg.getString("messages.purchase-broadcast", "&5{player} &7purchased rank &f{rank}&7!"),
                cfg.getString("messages.purchase-failed",
                        "&cCould not apply rank — contact staff. Your gold was not taken."),
                cfg.getString("messages.bypass-granted", "&aRank &f{rank} &agranted (bypass — no charge)."),
                cfg.getString("messages.reload-done", "&aRoot-Ranks config reloaded."),
                cfg.getString("messages.explorer-benefits",
                        "&7Explorer (default): &floan cap 100 G&7, chat prefix, all base commands."),
                cfg.getString("messages.rank-benefits-line", "&7  &8→ &f{benefits}"));
    }

    private static List<RankTier> defaultTiers() {
        return List.of(
                new RankTier("wanderer", "Wanderer", 25_000,
                        "Wanderer chat prefix; loan cap 500 G"),
                new RankTier("settler", "Settler", 75_000, "Settler prefix; loan cap 1,500 G"),
                new RankTier("pioneer", "Pioneer", 200_000, "Pioneer prefix; loan cap 4,000 G"),
                new RankTier("citizen", "Citizen", 500_000, "Citizen prefix; loan cap 10,000 G"),
                new RankTier("veteran", "Veteran", 1_250_000, "Veteran prefix; loan cap 25,000 G"),
                new RankTier("elite", "Elite", 3_000_000, "Elite prefix; loan cap 60,000 G"),
                new RankTier("champion", "Champion", 7_500_000, "Champion prefix; loan cap 150,000 G"));
    }

    private static String capitalize(String id) {
        if (id.isEmpty()) {
            return id;
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    public Optional<RankTier> tierById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase();
        return ranks.stream().filter(t -> t.id().equals(key)).findFirst();
    }

    public int indexOf(String groupId) {
        if (groupId == null) {
            return -1;
        }
        String key = groupId.trim().toLowerCase();
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).id().equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
