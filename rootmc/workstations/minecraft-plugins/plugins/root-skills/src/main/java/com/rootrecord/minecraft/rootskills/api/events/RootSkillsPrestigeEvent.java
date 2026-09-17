package com.rootrecord.minecraft.rootskills.api.events;

import com.rootrecord.minecraft.rootskills.api.SkillId;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a skill is prestiged.
 */
public final class RootSkillsPrestigeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkillId skill;
    private final int oldPrestige;
    private final int newPrestige;

    public RootSkillsPrestigeEvent(
            @NotNull Player player, @NotNull SkillId skill, int oldPrestige, int newPrestige) {
        super(player);
        this.skill = skill;
        this.oldPrestige = oldPrestige;
        this.newPrestige = newPrestige;
    }

    public SkillId getSkill() {
        return skill;
    }

    public int getOldPrestige() {
        return oldPrestige;
    }

    public int getNewPrestige() {
        return newPrestige;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
