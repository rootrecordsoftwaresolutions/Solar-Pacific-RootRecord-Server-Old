package com.rootrecord.minecraft.rootgamble.game;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.rootgamble.GambleConfig;
import com.rootrecord.minecraft.rootgamble.GambleEconomy;
import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import com.rootrecord.minecraft.rootgamble.store.GambleStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class DiceGame {

    private final RootGamblePlugin plugin;

    public DiceGame(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    public void challenge(Player from, Player to, double amount) {
        GambleConfig c = plugin.config();
        GambleEconomy eco = plugin.economy();
        GambleStore store = plugin.store();
        if (!eco.available()) {
            from.sendMessage(plugin.msg("economy-unavailable"));
            return;
        }
        if (amount < c.diceMin() - 1e-9 || amount > c.diceMax() + 1e-9) {
            from.sendMessage(plugin.msg("stake-range")
                    .replace("{min}", GambleEconomy.money(c.diceMin()))
                    .replace("{max}", GambleEconomy.money(c.diceMax()))
                    .replace("{currency}", c.currency()));
            return;
        }
        boolean free = store.hasFreePlay(from.getUniqueId(), "dice");
        if (!free && eco.balance(from) + 1e-9 < amount) {
            from.sendMessage(plugin.msg("insufficient")
                    .replace("{amount}", GambleEconomy.money(amount))
                    .replace("{balance}", GambleEconomy.money(eco.balance(from)))
                    .replace("{currency}", c.currency()));
            return;
        }
        GambleStore.Challenge existing = store.pendingForTarget(to.getUniqueId());
        if (existing != null) {
            store.deleteChallenge(existing.id());
        }
        store.createChallenge(
                "dice",
                from.getUniqueId(),
                from.getName(),
                to.getUniqueId(),
                to.getName(),
                amount,
                free);
        from.sendMessage(plugin.msg("challenge-sent")
                .replace("{player}", to.getName())
                .replace("{amount}", GambleEconomy.money(amount))
                .replace("{currency}", c.currency()));
        to.sendMessage(plugin.msg("challenge-received")
                .replace("{player}", from.getName())
                .replace("{game}", "dice")
                .replace("{amount}", GambleEconomy.money(amount))
                .replace("{currency}", c.currency()));
        to.sendMessage(ChatLinks.confirmCancel("/dice accept", "/dice deny"));
    }

    public void accept(Player target) {
        resolve(target, true);
    }

    public void deny(Player target) {
        resolve(target, false);
    }

    private void resolve(Player target, boolean accept) {
        GambleStore store = plugin.store();
        GambleStore.Challenge ch = store.pendingForTarget(target.getUniqueId());
        if (ch == null || !"dice".equals(ch.game())) {
            target.sendMessage(plugin.msg("challenge-none"));
            return;
        }
        if (System.currentTimeMillis() - ch.createdAtMs()
                > plugin.config().challengeTimeoutSeconds() * 1000L) {
            store.deleteChallenge(ch.id());
            target.sendMessage(plugin.msg("challenge-expired"));
            return;
        }
        store.deleteChallenge(ch.id());
        if (!accept) {
            target.sendMessage(plugin.msg("challenge-denied"));
            Player challenger = Bukkit.getPlayer(ch.challenger());
            if (challenger != null) {
                challenger.sendMessage(plugin.msg("challenge-denied"));
            }
            return;
        }

        Player challenger = Bukkit.getPlayer(ch.challenger());
        if (challenger == null || !challenger.isOnline()) {
            target.sendMessage(plugin.msg("player-not-found").replace("{player}", ch.challengerName()));
            return;
        }

        GambleEconomy eco = plugin.economy();
        GambleConfig c = plugin.config();
        boolean targetFree = store.hasFreePlay(target.getUniqueId(), "dice");
        boolean challengerFree = ch.challengerFree();
        if (challengerFree && targetFree) {
            target.sendMessage(plugin.msg("challenge-need-paid"));
            challenger.sendMessage(plugin.msg("challenge-need-paid"));
            return;
        }

        double paidChallenger = 0;
        double paidTarget = 0;
        if (!challengerFree) {
            if (!eco.withdrawEscrow(challenger.getUniqueId(), ch.amount())) {
                target.sendMessage(plugin.colorize("&cChallenger cannot cover the stake."));
                return;
            }
            paidChallenger = ch.amount();
        } else {
            store.markFreeUsed(challenger.getUniqueId(), "dice");
        }
        if (!targetFree) {
            if (!eco.withdrawEscrow(target.getUniqueId(), ch.amount())) {
                if (paidChallenger > 0) {
                    eco.deposit(challenger.getUniqueId(), paidChallenger);
                }
                target.sendMessage(plugin.msg("insufficient")
                        .replace("{amount}", GambleEconomy.money(ch.amount()))
                        .replace("{balance}", GambleEconomy.money(eco.balance(target)))
                        .replace("{currency}", c.currency()));
                return;
            }
            paidTarget = ch.amount();
        } else {
            store.markFreeUsed(target.getUniqueId(), "dice");
        }

        double pot = paidChallenger + paidTarget;
        int sides = c.diceSides();
        int a = ThreadLocalRandom.current().nextInt(1, sides + 1);
        int b = ThreadLocalRandom.current().nextInt(1, sides + 1);
        challenger.sendMessage(plugin.colorize("&7You rolled &f" + a + "&7 · " + target.getName() + " rolled &f" + b));
        target.sendMessage(plugin.colorize("&7You rolled &f" + b + "&7 · " + challenger.getName() + " rolled &f" + a));

        if (a == b) {
            if (paidChallenger > 0) {
                eco.deposit(challenger.getUniqueId(), paidChallenger);
            }
            if (paidTarget > 0) {
                eco.deposit(target.getUniqueId(), paidTarget);
            }
            challenger.sendMessage(plugin.msg("dice-tie"));
            target.sendMessage(plugin.msg("dice-tie"));
            return;
        }

        Player winner = a > b ? challenger : target;
        Player loser = a > b ? target : challenger;
        eco.deposit(winner.getUniqueId(), pot);
        winner.sendMessage(plugin.msg("win")
                .replace("{payout}", GambleEconomy.money(pot))
                .replace("{currency}", c.currency()));
        loser.sendMessage(plugin.colorize("&cDice — you lose."));
        if (pot + 1e-9 >= c.broadcastWinsOver()) {
            Bukkit.broadcastMessage(plugin.msg("broadcast-win")
                    .replace("{player}", winner.getName())
                    .replace("{payout}", GambleEconomy.money(pot))
                    .replace("{currency}", c.currency())
                    .replace("{game}", "dice"));
        }
    }
}
