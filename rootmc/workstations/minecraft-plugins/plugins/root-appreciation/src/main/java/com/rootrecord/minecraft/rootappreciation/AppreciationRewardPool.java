package com.rootrecord.minecraft.rootappreciation;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Weighted redeem catalog loaded from {@code appreciation-rewards.yml}. */
public final class AppreciationRewardPool {

    public record Reward(
            String id,
            String entry,
            int rank,
            int qty,
            int weight,
            String kind,
            String material,
            String enchant,
            String potion) {}

    private final List<Reward> rewards;
    private final long weightSum;
    private final String formula;

    public AppreciationRewardPool(RootAppreciationPlugin plugin) {
        List<Reward> loaded = new ArrayList<>();
        long sum = 0;
        String formula = "(248-rank)^1.2";
        try (InputStream in = plugin.getResource("appreciation-rewards.yml")) {
            if (in == null) {
                plugin.getLogger().severe("Missing appreciation-rewards.yml in jar");
            } else {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                formula = yaml.getString("formula", formula);
                for (Map<?, ?> map : yaml.getMapList("rewards")) {
                    String id = str(map.get("id"));
                    String entry = str(map.get("entry"));
                    String kind = str(map.get("kind"));
                    int rank = num(map.get("rank"), 0);
                    int qty = Math.max(1, num(map.get("qty"), 1));
                    int weight = Math.max(0, num(map.get("weight"), 0));
                    if (id.isBlank() || entry.isBlank() || kind.isBlank() || weight <= 0) {
                        continue;
                    }
                    Reward r = new Reward(
                            id,
                            entry,
                            rank,
                            qty,
                            weight,
                            kind,
                            blankToNull(str(map.get("material"))),
                            blankToNull(str(map.get("enchant"))),
                            blankToNull(str(map.get("potion"))));
                    loaded.add(r);
                    sum += weight;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed loading appreciation-rewards.yml", ex);
        }
        this.rewards = List.copyOf(loaded);
        this.weightSum = Math.max(1, sum);
        this.formula = formula;
        plugin.getLogger().info("Appreciation redeem pool: " + rewards.size() + " entries, weightSum=" + weightSum);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static int num(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }

    public List<Reward> rewards() {
        return rewards;
    }

    public long weightSum() {
        return weightSum;
    }

    public String formula() {
        return formula;
    }

    public boolean ready() {
        return !rewards.isEmpty();
    }

    public Reward roll() {
        if (rewards.isEmpty()) {
            return null;
        }
        long pick = ThreadLocalRandom.current().nextLong(weightSum);
        long cursor = 0;
        for (Reward r : rewards) {
            cursor += r.weight();
            if (pick < cursor) {
                return r;
            }
        }
        return rewards.get(rewards.size() - 1);
    }
}
