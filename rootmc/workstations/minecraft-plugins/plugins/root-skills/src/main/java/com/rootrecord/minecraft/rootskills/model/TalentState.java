package com.rootrecord.minecraft.rootskills.model;

public final class TalentState {

    private final String talentId;
    private int rank;
    private boolean unlocked;
    private boolean equipped;
    private int slot = -1;
    private long cooldownUntilMs;

    public TalentState(String talentId) {
        this.talentId = talentId;
    }

    public String talentId() {
        return talentId;
    }

    public int rank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = Math.max(0, rank);
    }

    public boolean unlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public boolean equipped() {
        return equipped;
    }

    public void setEquipped(boolean equipped) {
        this.equipped = equipped;
    }

    public int slot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public long cooldownUntilMs() {
        return cooldownUntilMs;
    }

    public void setCooldownUntilMs(long cooldownUntilMs) {
        this.cooldownUntilMs = cooldownUntilMs;
    }

    public boolean onCooldown(long nowMs) {
        return nowMs < cooldownUntilMs;
    }
}
