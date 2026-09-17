package com.rootrecord.minecraft.rootskills.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Skills party stub. */
public final class Party {

    private final UUID id;
    private UUID leaderId;
    private final Set<UUID> members = new LinkedHashSet<>();
    private int maxSize = 4;
    private double xpSharePercent = 0.1;

    public Party(UUID id, UUID leaderId) {
        this.id = id;
        this.leaderId = leaderId;
        if (leaderId != null) {
            members.add(leaderId);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public void setLeaderId(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public Set<UUID> members() {
        return members;
    }

    public int maxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
    }

    public double xpSharePercent() {
        return xpSharePercent;
    }

    public void setXpSharePercent(double xpSharePercent) {
        this.xpSharePercent = Math.max(0, Math.min(1, xpSharePercent));
    }

    public boolean add(UUID playerId) {
        if (members.size() >= maxSize) {
            return false;
        }
        return members.add(playerId);
    }

    public boolean remove(UUID playerId) {
        return members.remove(playerId);
    }
}
