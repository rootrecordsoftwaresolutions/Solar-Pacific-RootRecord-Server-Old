package com.rootrecord.minecraft.rootskills.model;

import com.rootrecord.minecraft.rootskills.api.SkillId;

public final class SkillProgress {

    private final SkillId skillId;
    private long xp;
    private int level;
    private int prestige;
    private String buffsJson = "{}";
    private double prestigeBuff = 1.0;

    public SkillProgress(SkillId skillId) {
        this.skillId = skillId;
    }

    public SkillId skillId() {
        return skillId;
    }

    public long xp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0L, xp);
    }

    public void addXp(long amount) {
        if (amount > 0) {
            this.xp += amount;
        }
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, level);
    }

    public int prestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = Math.max(0, prestige);
    }

    public String buffsJson() {
        return buffsJson;
    }

    public void setBuffsJson(String buffsJson) {
        this.buffsJson = buffsJson == null ? "{}" : buffsJson;
    }

    public double prestigeBuff() {
        return prestigeBuff;
    }

    public void setPrestigeBuff(double prestigeBuff) {
        this.prestigeBuff = Math.max(1.0, prestigeBuff);
    }
}
