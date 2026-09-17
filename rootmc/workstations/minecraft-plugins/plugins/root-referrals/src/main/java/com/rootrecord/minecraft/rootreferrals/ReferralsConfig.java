package com.rootrecord.minecraft.rootreferrals;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ReferralsConfig {

    private final int codeLength;
    private final long playtimeSeconds;
    private final int minActiveDays;
    private final int claimWithinHours;
    private final boolean requireReferrerLinked;
    private final int maxDepth;
    private final boolean rewardsEnabled;
    private final double qualifyReferrerG;
    private final double qualifyRecruitG;
    private final double lifetimeCapG;
    private final List<Milestone> milestones;
    private final boolean softIp;

    public ReferralsConfig(FileConfiguration cfg) {
        this.codeLength = Math.max(4, cfg.getInt("code.length", 6));
        this.playtimeSeconds = Math.max(0L, cfg.getLong("gates.playtime-seconds", 10800L));
        this.minActiveDays = Math.max(0, cfg.getInt("gates.min-active-days", 0));
        this.claimWithinHours = Math.max(0, cfg.getInt("gates.claim-within-hours", 48));
        this.requireReferrerLinked = cfg.getBoolean("gates.require-referrer-linked", true);
        this.maxDepth = Math.max(1, cfg.getInt("network.max-depth", 1));
        this.rewardsEnabled = cfg.getBoolean("rewards.enabled", true);
        this.qualifyReferrerG = Math.max(0, cfg.getDouble("rewards.qualify-referrer-g", 75));
        this.qualifyRecruitG = Math.max(0, cfg.getDouble("rewards.qualify-recruit-g", 0));
        this.lifetimeCapG = Math.max(0, cfg.getDouble("rewards.lifetime-cap-g", 2000));
        this.softIp = cfg.getBoolean("abuse.soft-ip", true);
        this.milestones = parseMilestones(cfg);
    }

    private static List<Milestone> parseMilestones(FileConfiguration cfg) {
        List<Milestone> out = new ArrayList<>();
        for (var raw : cfg.getMapList("milestones")) {
            Object q = raw.get("qualified");
            Object r = raw.get("reward-g");
            int qualified = q instanceof Number n ? n.intValue() : 0;
            double rewardG = r instanceof Number n ? n.doubleValue() : 0;
            addMilestone(out, qualified, rewardG);
        }
        ConfigurationSection section = cfg.getConfigurationSection("milestones");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection row = section.getConfigurationSection(key);
                if (row == null) {
                    continue;
                }
                addMilestone(out, row.getInt("qualified", 0), row.getDouble("reward-g", 0));
            }
        }
        if (out.isEmpty()) {
            return defaultMilestones();
        }
        out.sort(Comparator.comparingInt(Milestone::qualified));
        return Collections.unmodifiableList(out);
    }

    private static void addMilestone(List<Milestone> out, int qualified, double rewardG) {
        if (qualified > 0 && rewardG > 0) {
            out.add(new Milestone(qualified, rewardG));
        }
    }

    private static List<Milestone> defaultMilestones() {
        return List.of(
                new Milestone(5, 50),
                new Milestone(15, 100),
                new Milestone(30, 200));
    }

    public int codeLength() {
        return codeLength;
    }

    public long playtimeSeconds() {
        return playtimeSeconds;
    }

    public int minActiveDays() {
        return minActiveDays;
    }

    public int claimWithinHours() {
        return claimWithinHours;
    }

    public boolean requireReferrerLinked() {
        return requireReferrerLinked;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public boolean rewardsEnabled() {
        return rewardsEnabled;
    }

    public double qualifyReferrerG() {
        return qualifyReferrerG;
    }

    public double qualifyRecruitG() {
        return qualifyRecruitG;
    }

    public double lifetimeCapG() {
        return lifetimeCapG;
    }

    public List<Milestone> milestones() {
        return milestones;
    }

    public boolean softIp() {
        return softIp;
    }

    public record Milestone(int qualified, double rewardG) {
        public String key() {
            return qualified + "_qualified";
        }
    }
}
