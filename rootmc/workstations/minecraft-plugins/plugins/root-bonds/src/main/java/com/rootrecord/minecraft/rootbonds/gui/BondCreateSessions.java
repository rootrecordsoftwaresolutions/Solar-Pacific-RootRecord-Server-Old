package com.rootrecord.minecraft.rootbonds.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BondCreateSessions {

    public enum AnvilMode {
        AMOUNT
    }

    public static final class Session {
        private double amount = -1;
        private AnvilMode anvilMode;

        public double amount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public AnvilMode anvilMode() {
            return anvilMode;
        }

        public void setAnvilMode(AnvilMode anvilMode) {
            this.anvilMode = anvilMode;
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public Session session(UUID playerId) {
        return sessions.computeIfAbsent(playerId, id -> new Session());
    }

    public Session get(UUID playerId) {
        return sessions.get(playerId);
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }
}
