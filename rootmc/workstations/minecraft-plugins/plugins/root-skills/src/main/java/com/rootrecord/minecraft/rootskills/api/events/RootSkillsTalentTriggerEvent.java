package com.rootrecord.minecraft.rootskills.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a talent trigger is about to run. Cancellable.
 */
public final class RootSkillsTalentTriggerEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String talentId;
    private final String triggerId;
    private boolean cancelled;

    public RootSkillsTalentTriggerEvent(
            @NotNull Player player, @NotNull String talentId, @NotNull String triggerId) {
        super(player);
        this.talentId = talentId;
        this.triggerId = triggerId;
    }

    public String getTalentId() {
        return talentId;
    }

    public String getTriggerId() {
        return triggerId;
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
