package com.rootrecord.minecraft.rootavacore.powers;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired when Ava_Ivy judgment-kills a player (for logging bridges). */
public final class AvaJudgmentKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String victimName;
    private final UUID victimId;
    private final String reason;

    public AvaJudgmentKillEvent(String victimName, UUID victimId, String reason) {
        this.victimName = victimName;
        this.victimId = victimId;
        this.reason = reason;
    }

    public String victimName() {
        return victimName;
    }

    public UUID victimId() {
        return victimId;
    }

    public String reason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
