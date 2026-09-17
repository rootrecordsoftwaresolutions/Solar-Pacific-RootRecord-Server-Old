package com.rootrecord.minecraft.rootgamble;

import com.rootrecord.minecraft.rootgamble.game.RouletteWheel;
import org.bukkit.entity.Player;

/** Verbose per-game help. */
public final class GambleHelp {

    private final RootGamblePlugin plugin;

    public GambleHelp(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    public void roulette(Player player) {
        GambleConfig c = plugin.config();
        boolean free = plugin.store().hasFreePlay(player.getUniqueId(), "roulette");
        player.sendMessage(plugin.colorize("&6—— Roulette (vs Server Reserve) ——"));
        player.sendMessage(plugin.colorize("&7European wheel &f0–36&7 (37 pockets). One fair spin; house edge ~&f2.7%&7."));
        player.sendMessage(plugin.colorize("&eUsage: &f/roulette <amount> <red|black|green|0-36>"));
        player.sendMessage(plugin.colorize("&7Stake: &f" + GambleEconomy.money(c.rouletteMin())
                + "&7–&f" + GambleEconomy.money(c.rouletteMax()) + " " + c.currency()));
        player.sendMessage(plugin.colorize("&7Bets:"));
        player.sendMessage(plugin.colorize("  &cred&7/&8black &7— 18/37 (~48.6%) · payout &f"
                + GambleEconomy.money(c.roulettePayoutEven()) + "×"));
        player.sendMessage(plugin.colorize("  &agreen&7 or &f0–36 &7— 1/37 (~2.7%) · payout &f"
                + GambleEconomy.money(c.roulettePayoutStraight()) + "×"));
        player.sendMessage(plugin.colorize(free ? plugin.rawMsg("free-available") : plugin.rawMsg("free-spent")));
        player.sendMessage(plugin.colorize("&7Example: &f/roulette 10 red &7· &f/roulette 25 17"));
        player.sendMessage(plugin.colorize("&7Menu: &f/gamble"));
    }

    public void hilo(Player player) {
        GambleConfig c = plugin.config();
        boolean free = plugin.store().hasFreePlay(player.getUniqueId(), "hilo");
        player.sendMessage(plugin.colorize("&6—— HiLo (vs Server Reserve) ——"));
        player.sendMessage(plugin.colorize("&7Run &f/hilo &7to deal a starting card, then bet if the &fnext card&7 is &fhigh&7 or &flow&7 (Ace high)."));
        player.sendMessage(plugin.colorize("&7Same rank → stake refunded. After each round the revealed card becomes your next starting card."));
        player.sendMessage(plugin.colorize("&eUsage: &f/hilo &7· &f/hilo <amount> <high|low> &7· &f/hilo new"));
        player.sendMessage(plugin.colorize("&7Stake: &f" + GambleEconomy.money(c.hiloMin())
                + "&7–&f" + GambleEconomy.money(c.hiloMax()) + " " + c.currency()
                + " &7· payout &f" + GambleEconomy.money(c.hiloPayout()) + "×"));
        player.sendMessage(plugin.colorize(free ? plugin.rawMsg("free-available") : plugin.rawMsg("free-spent")));
        player.sendMessage(plugin.colorize("&7Example: &f/hilo &7then &f/hilo 10 high"));
        player.sendMessage(plugin.colorize("&7Menu: &f/gamble"));
    }

    public void lotto(Player player) {
        GambleConfig c = plugin.config();
        boolean free = plugin.store().hasFreePlay(player.getUniqueId(), "lotto");
        double pot = plugin.store().jackpot();
        player.sendMessage(plugin.colorize("&6—— Lotto (vs Server Reserve) ——"));
        player.sendMessage(plugin.colorize("&7Pick a number &f" + c.lottoMin() + "–" + c.lottoMax()
                + "&7. Ticket &f" + GambleEconomy.money(c.lottoTicketPrice()) + " " + c.currency()
                + "&7. Draw &feach Minecraft day&7 (automatic)."));
        player.sendMessage(plugin.colorize("&eUsage: &f/lotto <" + c.lottoMin() + "-" + c.lottoMax() + "> &7| &f/lotto &7(status)"));
        player.sendMessage(plugin.colorize("&7Jackpot: &f" + GambleEconomy.money(pot) + " " + c.currency()
                + " &7· your tickets: &f" + plugin.store().ticketsSummary(player.getUniqueId())));
        player.sendMessage(plugin.colorize("&7Max tickets/player: &f" + c.lottoMaxTicketsPerPlayer()));
        player.sendMessage(plugin.colorize("&7Winners of the drawn number split the pot; no winners → pot carries."));
        player.sendMessage(plugin.colorize(free ? plugin.rawMsg("free-available") : plugin.rawMsg("free-spent")));
        player.sendMessage(plugin.colorize("&7Example: &f/lotto 42"));
        player.sendMessage(plugin.colorize("&7Menu: &f/gamble"));
    }

    public void coin(Player player) {
        GambleConfig c = plugin.config();
        boolean free = plugin.store().hasFreePlay(player.getUniqueId(), "coin");
        player.sendMessage(plugin.colorize("&6—— Coin (vs player or Ava) ——"));
        player.sendMessage(plugin.colorize("&7Challenge another player to a &f50/50&7 coin flip. Winner takes the pot."));
        player.sendMessage(plugin.colorize("&bAva &7always accepts if /reserve is &f0+&7 and she can cover — she uses the Server Reserve (gambling problem)."));
        player.sendMessage(plugin.colorize("&eUsage: &f/coin <player|Ava> <amount>"));
        player.sendMessage(plugin.colorize("&eAccept: &f/coin accept &7· &eDeny: &f/coin deny &7(players only — Ava auto-accepts)"));
        player.sendMessage(plugin.colorize("&7Stake: &f" + GambleEconomy.money(c.coinMin())
                + "&7–&f" + GambleEconomy.money(c.coinMax()) + " " + c.currency()));
        player.sendMessage(plugin.colorize("&7Timeout: &f" + c.challengeTimeoutSeconds() + "s &7(player duels)"));
        player.sendMessage(plugin.colorize("&7At least one side must pay (both free plays cannot duel)."));
        player.sendMessage(plugin.colorize(free ? plugin.rawMsg("free-available") : plugin.rawMsg("free-spent")));
        player.sendMessage(plugin.colorize("&7Example: &f/coin Ava 50 &7· &f/coin Steve 50"));
        player.sendMessage(plugin.colorize("&7Menu: &f/gamble"));
    }

    public void dice(Player player) {
        GambleConfig c = plugin.config();
        boolean free = plugin.store().hasFreePlay(player.getUniqueId(), "dice");
        player.sendMessage(plugin.colorize("&6—— Dice (vs player) ——"));
        player.sendMessage(plugin.colorize("&7Each rolls &f1–" + c.diceSides() + "&7. Higher wins the pot. Tie → refund."));
        player.sendMessage(plugin.colorize("&eUsage: &f/dice <player> <amount>"));
        player.sendMessage(plugin.colorize("&eAccept: &f/dice accept &7· &eDeny: &f/dice deny"));
        player.sendMessage(plugin.colorize("&7Stake: &f" + GambleEconomy.money(c.diceMin())
                + "&7–&f" + GambleEconomy.money(c.diceMax()) + " " + c.currency()));
        player.sendMessage(plugin.colorize("&7Timeout: &f" + c.challengeTimeoutSeconds() + "s"));
        player.sendMessage(plugin.colorize("&7At least one side must pay (both free plays cannot duel)."));
        player.sendMessage(plugin.colorize(free ? plugin.rawMsg("free-available") : plugin.rawMsg("free-spent")));
        player.sendMessage(plugin.colorize("&7Example: &f/dice Steve 25"));
        player.sendMessage(plugin.colorize("&7Menu: &f/gamble"));
    }

    public void show(Player player, String game) {
        switch (game == null ? "" : game.toLowerCase()) {
            case "roulette" -> roulette(player);
            case "hilo" -> hilo(player);
            case "lotto" -> lotto(player);
            case "coin" -> coin(player);
            case "dice" -> dice(player);
            default -> {
                player.sendMessage(plugin.colorize("&6—— RootMC Gamble ——"));
                player.sendMessage(plugin.colorize("&7Open &f/gamble &7or try &f/roulette &7· &f/hilo &7· &f/lotto &7· &f/coin &7· &f/dice"));
            }
        }
    }
}
