package com.rootrecord.minecraft.rootadmin.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Rolling in-memory log of chat and commands per player (for /report evidence). */
public final class PlayerActivityLog {

    public enum Kind { CHAT, COMMAND }

    public record Entry(Instant at, Kind kind, String text) {}

    private final long retentionMillis;
    private final Map<UUID, Deque<Entry>> byPlayer = new ConcurrentHashMap<>();

    public PlayerActivityLog(int lookbackMinutes) {
        this.retentionMillis = Math.max(5, lookbackMinutes) * 60_000L;
    }

    public void recordChat(UUID playerId, String message) {
        record(playerId, Kind.CHAT, message);
    }

    public void recordCommand(UUID playerId, String commandLine) {
        record(playerId, Kind.COMMAND, commandLine);
    }

    private void record(UUID playerId, Kind kind, String text) {
        if (playerId == null || text == null || text.isBlank()) return;
        Deque<Entry> deque = byPlayer.computeIfAbsent(playerId, id -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        deque.addLast(new Entry(now, kind, text.trim()));
        prune(deque, now);
    }

    private void prune(Deque<Entry> deque, Instant now) {
        long cutoff = now.toEpochMilli() - retentionMillis;
        while (!deque.isEmpty() && deque.peekFirst().at().toEpochMilli() < cutoff) {
            deque.pollFirst();
        }
        while (deque.size() > 512) {
            deque.pollFirst();
        }
    }

    public List<Entry> snapshot(UUID playerId) {
        Deque<Entry> deque = byPlayer.get(playerId);
        if (deque == null) return List.of();
        Instant now = Instant.now();
        prune(deque, now);
        return new ArrayList<>(deque);
    }
}
