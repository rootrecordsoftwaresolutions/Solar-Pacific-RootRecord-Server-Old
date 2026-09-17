package com.rootrecord.minecraft.rootloans.service;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Borrowing cap = price of highest purchased player-track rank (root-ranks.yml). */
public final class RankLoanLimitService {

    public record RankTier(String id, String display, double price) {}

    private static final List<RankTier> FALLBACK_TIERS = List.of(
            new RankTier("wanderer", "Wanderer", 500),
            new RankTier("settler", "Settler", 1500),
            new RankTier("pioneer", "Pioneer", 4000),
            new RankTier("citizen", "Citizen", 10000),
            new RankTier("veteran", "Veteran", 25000),
            new RankTier("elite", "Elite", 60000),
            new RankTier("champion", "Champion", 150000));

    private final double defaultMaxLoan;
    private final boolean enabled;
    private final List<RankTier> tiers;
    private LuckPerms luckPerms;

    public RankLoanLimitService(double defaultMaxLoan, boolean enabled, List<RankTier> tiers) {
        this.defaultMaxLoan = Math.max(0, defaultMaxLoan);
        this.enabled = enabled;
        this.tiers = tiers == null || tiers.isEmpty() ? FALLBACK_TIERS : List.copyOf(tiers);
    }

    public static RankLoanLimitService fromConfigs(FileConfiguration loansCfg, File ranksFile) {
        double defaultMax = loansCfg.getDouble("loan.default-max-loan",
                loansCfg.getDouble("loan.starting-max-loan", 100.0));
        boolean rankLimits = loansCfg.getBoolean("loan.rank-limits-enabled", true);
        List<RankTier> tiers = ranksFile != null && ranksFile.isFile()
                ? parseRanks(YamlConfiguration.loadConfiguration(ranksFile))
                : List.of();
        return new RankLoanLimitService(defaultMax, rankLimits, tiers);
    }

    public void bindLuckPerms(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public boolean enabled() {
        return enabled && !tiers.isEmpty() && luckPerms != null;
    }

    public double defaultMaxLoan() {
        return defaultMaxLoan;
    }

    public List<RankTier> tiers() {
        return tiers;
    }

    /** Highest purchasable player rank only — staff/donor groups are ignored. */
    public double limitFor(UUID playerId) {
        if (!enabled()) {
            return defaultMaxLoan;
        }
        User user = luckPerms.getUserManager().getUser(playerId);
        if (user == null) {
            return defaultMaxLoan;
        }
        int highest = highestOwnedIndex(user);
        if (highest < 0) {
            return defaultMaxLoan;
        }
        return tiers.get(highest).price();
    }

    private int highestOwnedIndex(User user) {
        int highest = -1;
        for (int i = 0; i < tiers.size(); i++) {
            if (hasDirectGroup(user, tiers.get(i).id())) {
                highest = i;
            }
        }
        // Match root-ranks chat/prefix: groups inherited via parents/tracks count too.
        for (Group group : user.getInheritedGroups(QueryOptions.nonContextual())) {
            String name = group.getName().toLowerCase(Locale.ROOT);
            for (int i = 0; i < tiers.size(); i++) {
                if (tiers.get(i).id().equalsIgnoreCase(name)) {
                    highest = Math.max(highest, i);
                }
            }
        }
        return highest;
    }

    private static boolean hasDirectGroup(User user, String groupId) {
        return user.getNodes().stream()
                .filter(InheritanceNode.class::isInstance)
                .map(InheritanceNode.class::cast)
                .anyMatch(node -> node.getGroupName().equalsIgnoreCase(groupId));
    }

    private static List<RankTier> parseRanks(FileConfiguration cfg) {
        List<RankTier> ranks = new ArrayList<>();
        for (var map : cfg.getMapList("ranks")) {
            Object idRaw = map.get("id");
            String id = idRaw == null ? "" : String.valueOf(idRaw).trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            Object priceRaw = map.get("price");
            double price = priceRaw instanceof Number n ? n.doubleValue() : 0.0;
            Object displayRaw = map.get("display");
            String display = displayRaw == null ? id : String.valueOf(displayRaw);
            ranks.add(new RankTier(id, display, Math.max(0.0, price)));
        }
        return Collections.unmodifiableList(ranks);
    }
}
