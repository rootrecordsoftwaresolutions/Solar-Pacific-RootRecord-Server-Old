package com.rootrecord.minecraft.rootskills.talents;

import com.rootrecord.minecraft.rootskills.api.SkillId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TalentDefinition {

    private final String id;
    private final String displayName;
    private final String description;
    private final SkillId skill;
    private final int unlockLevel;
    private final int maxRank;
    private final double manaCost;
    private final int cooldownSeconds;
    private final int durationSeconds;
    private final List<String> triggers;
    private final List<Map<String, Object>> effects;
    private final List<Map<String, Object>> conditions;

    public TalentDefinition(
            String id,
            String displayName,
            String description,
            SkillId skill,
            int unlockLevel,
            int maxRank,
            double manaCost,
            int cooldownSeconds,
            int durationSeconds,
            List<String> triggers,
            List<Map<String, Object>> effects,
            List<Map<String, Object>> conditions) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.skill = skill;
        this.unlockLevel = unlockLevel;
        this.maxRank = Math.max(1, maxRank);
        this.manaCost = manaCost;
        this.cooldownSeconds = cooldownSeconds;
        this.durationSeconds = durationSeconds;
        this.triggers = triggers == null ? List.of() : List.copyOf(triggers);
        this.effects = effects == null ? List.of() : List.copyOf(effects);
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public SkillId skill() {
        return skill;
    }

    public int unlockLevel() {
        return unlockLevel;
    }

    public int maxRank() {
        return maxRank;
    }

    public double manaCost() {
        return manaCost;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public List<String> triggers() {
        return triggers;
    }

    public List<Map<String, Object>> effects() {
        return effects;
    }

    public List<Map<String, Object>> conditions() {
        return conditions;
    }

    public List<Map<String, Object>> mutableEffects() {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> e : effects) {
            copy.add(e);
        }
        return copy;
    }
}
