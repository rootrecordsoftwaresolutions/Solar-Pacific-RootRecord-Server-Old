package com.rootrecord.minecraft.rootgamble.game;

import com.rootrecord.minecraft.rootgamble.GambleConfig;
import com.rootrecord.minecraft.rootgamble.GambleEconomy;
import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class RouletteGame {

    private final RootGamblePlugin plugin;

    public RouletteGame(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, double stake, RouletteWheel.Bet bet) {
        GambleConfig c = plugin.config();
        GambleEconomy eco = plugin.economy();
        if (!eco.available()) {
            player.sendMessage(plugin.msg("economy-unavailable"));
            return;
        }
        if (stake < c.rouletteMin() - 1e-9 || stake > c.rouletteMax() + 1e-9) {
            player.sendMessage(plugin.msg("stake-range")
                    .replace("{min}", GambleEconomy.money(c.rouletteMin()))
                    .replace("{max}", GambleEconomy.money(c.rouletteMax()))
                    .replace("{currency}", c.currency()));
            return;
        }
        if (!plugin.allowReservePlay(player)) {
            return;
        }
        boolean free = plugin.store().hasFreePlay(player.getUniqueId(), "roulette");
        if (!free) {
            double bal = eco.balance(player);
            if (bal + 1e-9 < stake) {
                player.sendMessage(plugin.msg("insufficient")
                        .replace("{amount}", GambleEconomy.money(stake))
                        .replace("{balance}", GambleEconomy.money(bal))
                        .replace("{currency}", c.currency()));
                return;
            }
            if (!eco.takeWager(player, stake, "roulette")) {
                player.sendMessage(plugin.msg("insufficient")
                        .replace("{amount}", GambleEconomy.money(stake))
                        .replace("{balance}", GambleEconomy.money(bal))
                        .replace("{currency}", c.currency()));
                return;
            }
        } else {
            plugin.store().markFreeUsed(player.getUniqueId(), "roulette");
        }
        plugin.recordReservePlay(player);

        int spun = RouletteWheel.spin();
        String color = RouletteWheel.isGreen(spun) ? "green"
                : RouletteWheel.isRed(spun) ? "red" : "black";
        boolean won = bet.wins(spun);
        String freeTag = free ? " " + plugin.rawMsg("free-used") : "";

        if (won) {
            double mult = bet.isEvenMoney() ? c.roulettePayoutEven() : c.roulettePayoutStraight();
            double payout = stake * mult;
            eco.payWin(player, payout, "gamble:roulette-win");
            player.sendMessage(plugin.colorize("&7Spin: &f" + spun + " &7(" + color + ") · bet &f" + bet.label()
                    + freeTag));
            player.sendMessage(plugin.msg("win")
                    .replace("{payout}", GambleEconomy.money(payout))
                    .replace("{currency}", c.currency()));
            maybeBroadcast(player, "roulette", payout);
        } else {
            player.sendMessage(plugin.colorize("&7Spin: &f" + spun + " &7(" + color + ") · bet &f" + bet.label()
                    + freeTag));
            player.sendMessage(plugin.msg("lose")
                    .replace("{stake}", GambleEconomy.money(free ? 0 : stake))
                    .replace("{currency}", c.currency()));
        }
    }

    private void maybeBroadcast(Player player, String game, double payout) {
        if (payout + 1e-9 < plugin.config().broadcastWinsOver()) {
            return;
        }
        String line = plugin.msg("broadcast-win")
                .replace("{player}", player.getName())
                .replace("{payout}", GambleEconomy.money(payout))
                .replace("{currency}", plugin.config().currency())
                .replace("{game}", game);
        Bukkit.broadcastMessage(line);
    }
}
