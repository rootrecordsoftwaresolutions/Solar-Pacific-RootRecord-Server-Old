package com.rootrecord.minecraft.rootgamble.game;

import com.rootrecord.minecraft.rootgamble.GambleConfig;
import com.rootrecord.minecraft.rootgamble.GambleEconomy;
import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class HiLoGame {

    private static final long SESSION_MS = 5L * 60L * 1000L;

    private final RootGamblePlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public HiLoGame(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    /** Deal (or re-show) the starting card before any bet. */
    public void start(Player player, boolean forceNew) {
        GambleConfig c = plugin.config();
        if (!plugin.economy().available()) {
            player.sendMessage(plugin.msg("economy-unavailable"));
            return;
        }
        Session existing = sessionFor(player.getUniqueId());
        if (existing != null && !forceNew) {
            player.sendMessage(plugin.msg("hilo-current")
                    .replace("{card}", existing.card.legacyDisplay()));
            return;
        }
        PlayingCard card = PlayingCard.draw();
        sessions.put(player.getUniqueId(), new Session(card, System.currentTimeMillis()));
        player.sendMessage(plugin.msg("hilo-started")
                .replace("{card}", card.legacyDisplay())
                .replace("{min}", GambleEconomy.money(c.hiloMin()))
                .replace("{max}", GambleEconomy.money(c.hiloMax()))
                .replace("{currency}", c.currency()));
    }

    public void clearSession(UUID uuid) {
        if (uuid != null) {
            sessions.remove(uuid);
        }
    }

    public void play(Player player, double stake, boolean high) {
        GambleConfig c = plugin.config();
        GambleEconomy eco = plugin.economy();
        if (!eco.available()) {
            player.sendMessage(plugin.msg("economy-unavailable"));
            return;
        }
        Session session = sessionFor(player.getUniqueId());
        if (session == null) {
            player.sendMessage(plugin.msg("hilo-no-session"));
            return;
        }
        if (stake < c.hiloMin() - 1e-9 || stake > c.hiloMax() + 1e-9) {
            player.sendMessage(plugin.msg("stake-range")
                    .replace("{min}", GambleEconomy.money(c.hiloMin()))
                    .replace("{max}", GambleEconomy.money(c.hiloMax()))
                    .replace("{currency}", c.currency()));
            return;
        }
        if (!plugin.allowReservePlay(player)) {
            return;
        }
        boolean free = plugin.store().hasFreePlay(player.getUniqueId(), "hilo");
        if (!free) {
            double bal = eco.balance(player);
            if (bal + 1e-9 < stake || !eco.takeWager(player, stake, "hilo")) {
                player.sendMessage(plugin.msg("insufficient")
                        .replace("{amount}", GambleEconomy.money(stake))
                        .replace("{balance}", GambleEconomy.money(bal))
                        .replace("{currency}", c.currency()));
                return;
            }
        } else {
            plugin.store().markFreeUsed(player.getUniqueId(), "hilo");
        }
        plugin.recordReservePlay(player);

        PlayingCard current = session.card;
        PlayingCard next;
        Outcome outcome;
        if (c.hiloFairRoll()) {
            next = PlayingCard.draw();
            outcome = resolve(current, next, high);
        } else {
            boolean won = ThreadLocalRandom.current().nextDouble() < c.hiloPlayerWinChance();
            next = pickNextCard(current, high, won);
            outcome = won ? Outcome.WIN : Outcome.LOSE;
        }

        sessions.put(player.getUniqueId(), new Session(next, System.currentTimeMillis()));

        String freeTag = free ? " " + plugin.rawMsg("free-used") : "";
        String pick = high ? "high" : "low";
        String line = plugin.colorize("&7HiLo &f" + pick
                + " &7· &f" + current.legacyDisplay()
                + " &7→ &f" + next.legacyDisplay()
                + freeTag);

        switch (outcome) {
            case WIN -> {
                double payout = stake * c.hiloPayout();
                eco.payWin(player, payout, "gamble:hilo-win");
                player.sendMessage(line);
                player.sendMessage(plugin.msg("win")
                        .replace("{payout}", GambleEconomy.money(payout))
                        .replace("{currency}", c.currency()));
                if (payout + 1e-9 >= c.broadcastWinsOver()) {
                    Bukkit.broadcastMessage(plugin.msg("broadcast-win")
                            .replace("{player}", player.getName())
                            .replace("{payout}", GambleEconomy.money(payout))
                            .replace("{currency}", c.currency())
                            .replace("{game}", "hilo"));
                }
            }
            case PUSH -> {
                if (!free) {
                    eco.payWin(player, stake, "gamble:hilo-push");
                }
                player.sendMessage(line);
                player.sendMessage(plugin.msg("hilo-tie")
                        .replace("{stake}", GambleEconomy.money(free ? 0 : stake))
                        .replace("{currency}", c.currency()));
            }
            case LOSE -> {
                player.sendMessage(line);
                player.sendMessage(plugin.msg("lose")
                        .replace("{stake}", GambleEconomy.money(free ? 0 : stake))
                        .replace("{currency}", c.currency()));
            }
        }
        player.sendMessage(plugin.msg("hilo-next")
                .replace("{card}", next.legacyDisplay()));
    }

    private Session sessionFor(UUID uuid) {
        Session session = sessions.get(uuid);
        if (session == null) {
            return null;
        }
        if (System.currentTimeMillis() - session.startedAtMs > SESSION_MS) {
            sessions.remove(uuid);
            return null;
        }
        return session;
    }

    private record Session(PlayingCard card, long startedAtMs) {}

    private enum Outcome { WIN, LOSE, PUSH }

    private static Outcome resolve(PlayingCard current, PlayingCard next, boolean high) {
        int cmp = current.compareRank(next);
        if (cmp == 0) {
            return Outcome.PUSH;
        }
        if (high) {
            return cmp < 0 ? Outcome.WIN : Outcome.LOSE;
        }
        return cmp > 0 ? Outcome.WIN : Outcome.LOSE;
    }

    /** Pick a next card that matches a forced win/loss (admin override mode). */
    private static PlayingCard pickNextCard(PlayingCard current, boolean high, boolean won) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 64; i++) {
            PlayingCard candidate = PlayingCard.draw();
            Outcome o = resolve(current, candidate, high);
            if (won && o == Outcome.WIN) {
                return candidate;
            }
            if (!won && o == Outcome.LOSE) {
                return candidate;
            }
        }
        int rank = current.rank();
        if (won) {
            rank = high ? Math.min(14, rank + 1 + r.nextInt(3)) : Math.max(2, rank - 1 - r.nextInt(3));
            if (rank == current.rank()) {
                rank = high ? Math.min(14, rank + 1) : Math.max(2, rank - 1);
            }
        } else {
            rank = high ? Math.max(2, rank - 1 - r.nextInt(3)) : Math.min(14, rank + 1 + r.nextInt(3));
            if (rank == current.rank()) {
                rank = high ? Math.max(2, rank - 1) : Math.min(14, rank + 1);
            }
        }
        return new PlayingCard(rank, PlayingCard.Suit.values()[r.nextInt(PlayingCard.Suit.values().length)]);
    }
}
