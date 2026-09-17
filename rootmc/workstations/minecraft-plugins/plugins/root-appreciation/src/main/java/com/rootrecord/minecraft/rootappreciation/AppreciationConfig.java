package com.rootrecord.minecraft.rootappreciation;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class AppreciationConfig {

    public record DayReward(int tokens, double gold) {}

    private final boolean enabled;
    private final Material tokenMaterial;
    private final String displayName;
    private final List<String> lore;
    private final boolean bonusEnabled;
    private final long missAfterMs;
    private final long cooldownMs;
    private final List<DayReward> days;
    private final boolean motdRemind;
    private final long motdDelayTicks;
    private final boolean redeemEnabled;

    public AppreciationConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("enabled", true);
        Material mat = Material.matchMaterial(cfg.getString("token.material", "SUNFLOWER"));
        this.tokenMaterial = mat != null && !mat.isAir() ? mat : Material.SUNFLOWER;
        this.displayName = cfg.getString("token.display-name", "&6Appreciation Token");
        this.lore = List.copyOf(cfg.getStringList("token.lore"));
        this.bonusEnabled = cfg.getBoolean("bonus.enabled", true);
        this.missAfterMs = Math.max(24, cfg.getLong("bonus.miss-after-hours", 48)) * 3_600_000L;
        this.cooldownMs = Math.max(1, cfg.getLong("bonus.cooldown-hours", 20)) * 3_600_000L;
        List<DayReward> parsed = new ArrayList<>();
        for (java.util.Map<?, ?> map : cfg.getMapList("bonus.days")) {
            Object t = map.get("tokens");
            Object g = map.get("gold");
            int tokens = t instanceof Number n ? Math.max(1, n.intValue()) : 1;
            double gold = g instanceof Number n ? Math.max(0, n.doubleValue()) : 0;
            parsed.add(new DayReward(tokens, gold));
        }
        if (parsed.isEmpty()) {
            parsed.add(new DayReward(1, 5));
            parsed.add(new DayReward(2, 10));
            parsed.add(new DayReward(3, 15));
            parsed.add(new DayReward(4, 20));
            parsed.add(new DayReward(5, 25));
        }
        this.days = List.copyOf(parsed);
        this.motdRemind = cfg.getBoolean("motd.remind-if-bonus-available", true);
        this.motdDelayTicks = Math.max(1, cfg.getLong("motd.delay-ticks", 40));
        this.redeemEnabled = cfg.getBoolean("redeem.enabled", true);
    }

    public boolean enabled() {
        return enabled;
    }

    public Material tokenMaterial() {
        return tokenMaterial;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public boolean bonusEnabled() {
        return bonusEnabled;
    }

    public long missAfterMs() {
        return missAfterMs;
    }

    public long cooldownMs() {
        return cooldownMs;
    }

    public List<DayReward> days() {
        return days;
    }

    public DayReward rewardForDay(int day) {
        int idx = Math.max(1, day) - 1;
        if (idx >= days.size()) {
            return days.get(days.size() - 1);
        }
        return days.get(idx);
    }

    public int maxStreakDay() {
        return days.size();
    }

    public boolean motdRemind() {
        return motdRemind;
    }

    public long motdDelayTicks() {
        return motdDelayTicks;
    }

    public boolean redeemEnabled() {
        return redeemEnabled;
    }
}
