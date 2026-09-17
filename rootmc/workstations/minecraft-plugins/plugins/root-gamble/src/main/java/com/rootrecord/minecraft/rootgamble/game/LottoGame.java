package com.rootrecord.minecraft.rootgamble.game;

import com.rootrecord.minecraft.rootgamble.GambleConfig;
import com.rootrecord.minecraft.rootgamble.GambleEconomy;
import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import com.rootrecord.minecraft.rootgamble.store.GambleStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LottoGame {

    private final RootGamblePlugin plugin;

    public LottoGame(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    public void buy(Player player, int number) {
        GambleConfig c = plugin.config();
        GambleEconomy eco = plugin.economy();
        GambleStore store = plugin.store();
        if (!store.ready()) {
            player.sendMessage(plugin.msg("store-unavailable"));
            return;
        }
        if (!eco.available()) {
            player.sendMessage(plugin.msg("economy-unavailable"));
            return;
        }
        if (number < c.lottoMin() || number > c.lottoMax()) {
            plugin.help().lotto(player);
            return;
        }
        if (store.ticketCount(player.getUniqueId()) >= c.lottoMaxTicketsPerPlayer()) {
            player.sendMessage(plugin.colorize("&eMax tickets for this draw: &f" + c.lottoMaxTicketsPerPlayer()));
            return;
        }
        if (!plugin.allowReservePlay(player)) {
            return;
        }

        boolean free = store.hasFreePlay(player.getUniqueId(), "lotto");
        double price = c.lottoTicketPrice();
        if (!free) {
            double bal = eco.balance(player);
            if (bal + 1e-9 < price || !eco.takeWager(player, price, "lotto")) {
                player.sendMessage(plugin.msg("insufficient")
                        .replace("{amount}", GambleEconomy.money(price))
                        .replace("{balance}", GambleEconomy.money(bal))
                        .replace("{currency}", c.currency()));
                return;
            }
            store.addJackpot(price);
        } else {
            store.markFreeUsed(player.getUniqueId(), "lotto");
            if (c.lottoFreeAddsToPot()) {
                // promo would need Reserve inject — skip unless configured paid path
            }
        }
        plugin.recordReservePlay(player);

        store.addTicket(player.getUniqueId(), player.getName(), number);
        String freeTag = free ? plugin.rawMsg("free-used") : "";
        String line = plugin.colorize(plugin.rawMsg("lotto-bought")
                .replace("{player}", player.getName())
                .replace("{number}", String.valueOf(number))
                .replace("{free}", freeTag)
                .replace("{pot}", GambleEconomy.money(store.jackpot()))
                .replace("{currency}", c.currency()));
        Bukkit.broadcastMessage(line);
    }

    public void status(Player player) {
        GambleStore store = plugin.store();
        player.sendMessage(plugin.msg("lotto-status")
                .replace("{pot}", GambleEconomy.money(store.jackpot()))
                .replace("{currency}", plugin.config().currency())
                .replace("{tickets}", store.ticketsSummary(player.getUniqueId())));
        plugin.help().lotto(player);
    }

    /** McDay auto-draw. */
    public void drawForDay(long mcDayId) {
        GambleStore store = plugin.store();
        GambleConfig c = plugin.config();
        if (!store.ready()) {
            return;
        }
        long last = store.lastDrawnMcDayId();
        if (mcDayId <= last) {
            return;
        }
        // Catch-up: one draw then sync
        runDraw();
        store.setLastDrawnMcDayId(mcDayId);
    }

    private void runDraw() {
        GambleStore store = plugin.store();
        GambleConfig c = plugin.config();
        GambleEconomy eco = plugin.economy();
        int min = c.lottoMin();
        int max = c.lottoMax();
        int winning = ThreadLocalRandom.current().nextInt(min, max + 1);

        Bukkit.broadcastMessage(plugin.msg("lotto-draw").replace("{number}", String.valueOf(winning)));

        List<GambleStore.LottoTicket> winners = store.ticketsForNumber(winning);
        store.clearTickets();

        if (winners.isEmpty()) {
            Bukkit.broadcastMessage(plugin.msg("lotto-no-winners"));
            return;
        }

        double pot = store.jackpot();
        // Group by player for equal share per ticket
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (GambleStore.LottoTicket t : winners) {
            counts.merge(t.uuid(), 1, Integer::sum);
            names.put(t.uuid(), t.name());
        }
        int totalTickets = winners.size();
        List<String> nameList = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : counts.entrySet()) {
            double share = pot * (e.getValue() / (double) totalTickets);
            String name = names.get(e.getKey());
            eco.payWinOffline(e.getKey(), name, share, "gamble:lotto-win");
            nameList.add(name + " (" + GambleEconomy.money(share) + ")");
            Player online = Bukkit.getPlayer(e.getKey());
            if (online != null) {
                online.sendMessage(plugin.msg("win")
                        .replace("{payout}", GambleEconomy.money(share))
                        .replace("{currency}", c.currency()));
            }
        }
        Bukkit.broadcastMessage(plugin.msg("lotto-winners")
                .replace("{pot}", GambleEconomy.money(pot))
                .replace("{currency}", c.currency())
                .replace("{names}", String.join(", ", nameList)));
        store.setJackpot(c.lottoStartingJackpot());
    }
}
