package com.rootrecord.minecraft.rootskills.api.events;

import com.rootrecord.minecraft.rootskills.api.SkillId;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before XP is applied. Cancellable.
 */
public final class RootSkillsXpGainEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SkillId skill;
    private long amount;
    private boolean cancelled;

    public RootSkillsXpGainEvent(@NotNull Player player, @NotNull SkillId skill, long amount) {
        super(player);
        this.skill = skill;
        this.amount = amount;
    }

    public SkillId getSkill() {
        return skill;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = Math.max(0L, amount);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
