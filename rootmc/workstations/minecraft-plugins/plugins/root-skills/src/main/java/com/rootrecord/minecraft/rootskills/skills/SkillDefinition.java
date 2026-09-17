package com.rootrecord.minecraft.rootskills.skills;

import com.rootrecord.minecraft.rootskills.api.SkillId;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Loaded from skills/*.yml */
public final class SkillDefinition {

    private final SkillId id;
    private final String displayName;
    private final boolean enabled;
    private final int maxLevel;
    private final Material icon;
    private final Map<String, Long> blockXp;
    private final long defaultBlockXp;
    private final long combatHitXp;
    private final long combatKillXp;
    private final long actionXp;
    private final List<String> talentIds;

    public SkillDefinition(
            SkillId id,
            String displayName,
            boolean enabled,
            int maxLevel,
            Material icon,
            Map<String, Long> blockXp,
            long defaultBlockXp,
            long combatHitXp,
            long combatKillXp,
            long actionXp,
            List<String> talentIds) {
        this.id = id;
        this.displayName = displayName == null || displayName.isBlank() ? id.key() : displayName;
        this.enabled = enabled;
        this.maxLevel = Math.max(1, maxLevel);
        this.icon = icon == null ? Material.BOOK : icon;
        this.blockXp = blockXp == null ? Map.of() : Map.copyOf(blockXp);
        this.defaultBlockXp = Math.max(0L, defaultBlockXp);
        this.combatHitXp = Math.max(0L, combatHitXp);
        this.combatKillXp = Math.max(0L, combatKillXp);
        this.actionXp = Math.max(0L, actionXp);
        this.talentIds = talentIds == null ? List.of() : List.copyOf(talentIds);
    }

    public SkillId id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public Material icon() {
        return icon;
    }

    public Map<String, Long> blockXp() {
        return Collections.unmodifiableMap(blockXp);
    }

    public long xpForBlock(Material material) {
        if (material == null) {
            return defaultBlockXp;
        }
        Long exact = blockXp.get(material.name());
        if (exact != null) {
            return exact;
        }
        return defaultBlockXp;
    }

    public long combatHitXp() {
        return combatHitXp;
    }

    public long combatKillXp() {
        return combatKillXp;
    }

    public long actionXp() {
        return actionXp;
    }

    public List<String> talentIds() {
        return talentIds;
    }
}
