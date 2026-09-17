package com.rootrecord.minecraft.rootskills.model;

import com.rootrecord.minecraft.rootskills.api.SkillId;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerSkillsProfile {

    private final UUID playerId;
    private String username;
    private final Map<SkillId, SkillProgress> skills = new EnumMap<>(SkillId.class);
    private final Map<String, TalentState> talents = new HashMap<>();
    private String classId;
    private UUID partyId;
    private double mana;
    private double maxMana = 100.0;
    private String prefsJson = "{}";
    private String migratedFrom;
    private Long migratedAt;
    private volatile boolean dirty;

    public PlayerSkillsProfile(UUID playerId) {
        this.playerId = playerId;
        for (SkillId id : SkillId.values()) {
            skills.put(id, new SkillProgress(id));
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public String username() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        markDirty();
    }

    public SkillProgress skill(SkillId id) {
        return skills.computeIfAbsent(id, SkillProgress::new);
    }

    public Map<SkillId, SkillProgress> skills() {
        return skills;
    }

    public Map<String, TalentState> talents() {
        return talents;
    }

    public TalentState talent(String talentId) {
        return talents.computeIfAbsent(talentId, TalentState::new);
    }

    public String classId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
        markDirty();
    }

    public UUID partyId() {
        return partyId;
    }

    public void setPartyId(UUID partyId) {
        this.partyId = partyId;
        markDirty();
    }

    public double mana() {
        return mana;
    }

    public void setMana(double mana) {
        this.mana = Math.max(0, Math.min(maxMana, mana));
        markDirty();
    }

    public double maxMana() {
        return maxMana;
    }

    public void setMaxMana(double maxMana) {
        this.maxMana = Math.max(1, maxMana);
        markDirty();
    }

    public String prefsJson() {
        return prefsJson;
    }

    public void setPrefsJson(String prefsJson) {
        this.prefsJson = prefsJson == null ? "{}" : prefsJson;
        markDirty();
    }

    public String migratedFrom() {
        return migratedFrom;
    }

    public void setMigratedFrom(String migratedFrom) {
        this.migratedFrom = migratedFrom;
        markDirty();
    }

    public Long migratedAt() {
        return migratedAt;
    }

    public void setMigratedAt(Long migratedAt) {
        this.migratedAt = migratedAt;
        markDirty();
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public int powerLevel() {
        int sum = 0;
        for (SkillProgress p : skills.values()) {
            sum += p.level();
        }
        return sum;
    }
}
