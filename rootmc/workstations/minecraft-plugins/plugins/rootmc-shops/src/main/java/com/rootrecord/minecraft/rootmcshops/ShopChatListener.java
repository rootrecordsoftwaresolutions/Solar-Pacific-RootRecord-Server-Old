package com.rootrecord.minecraft.rootmcshops;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ShopChatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ShopInputManager inputs;

    public ShopChatListener(RootMcShopsPlugin plugin, ShopStore store, ShopInputManager inputs) {
        this.plugin = plugin;
        this.store = store;
        this.inputs = inputs;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ShopInputManager.Pending pending = inputs.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String message = LEGACY.serialize(event.message()).replaceAll("(?i)[§&][0-9a-fk-or]", "").trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> handleInput(player, pending, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ShopInputManager.Pending pending = inputs.remove(event.getPlayer().getUniqueId());
        if (pending != null && pending.kind() == ShopInputManager.Kind.CREATE_PRICE) {
            ShopService.abortCreate(plugin, store, event.getPlayer(), pending);
        }
    }

    private void handleInput(Player player, ShopInputManager.Pending pending, String message) {
        if (message.equalsIgnoreCase("cancel")) {
            inputs.remove(player.getUniqueId());
            if (pending.kind() == ShopInputManager.Kind.CREATE_PRICE) {
                ShopService.abortCreate(plugin, store, player, pending);
            } else {
                player.sendMessage(plugin.msg("edit-cancelled"));
            }
            return;
        }

        if (pending.kind() == ShopInputManager.Kind.EDIT_SALE_QTY) {
            int qty;
            try {
                qty = Integer.parseInt(message.trim());
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.msg("edit-invalid-qty"));
                return;
            }
            if (ShopService.finishEditSaleQty(plugin, store, player, pending, qty)) {
                inputs.remove(player.getUniqueId());
            }
            return;
        }

        double price;
        try {
            price = Double.parseDouble(message.replace(",", "."));
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("create-invalid-price"));
            return;
        }

        boolean done = switch (pending.kind()) {
            case CREATE_PRICE -> ShopService.finishCreate(plugin, store, player, pending, price);
            case EDIT_PRICE -> ShopService.finishEditPrice(plugin, store, player, pending, price);
            case EDIT_SALE_QTY -> false;
        };
        if (done) {
            inputs.remove(player.getUniqueId());
        }
    }
}
