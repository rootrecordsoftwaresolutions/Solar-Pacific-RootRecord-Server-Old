package com.rootrecord.minecraft.rootskills.api.events;

import com.rootrecord.minecraft.rootskills.api.SkillId;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a skill levels up.
 */
public final class RootSkillsLevelUpEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkillId skill;
    private final int oldLevel;
    private final int newLevel;

    public RootSkillsLevelUpEvent(
            @NotNull Player player, @NotNull SkillId skill, int oldLevel, int newLevel) {
        super(player);
        this.skill = skill;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public SkillId getSkill() {
        return skill;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
