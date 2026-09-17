package com.rootrecord.minecraft.rootbonds.gui;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class BondCreateListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final int SLOT_BACK = 0;
    private static final int[] AMOUNT_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final double[] AMOUNT_PRESETS = {1, 5, 10, 25, 50, 100};
    private static final int SLOT_CUSTOM = 22;
    private static final int SLOT_CONFIRM = 26;

    private final RootBondsPlugin plugin;
    private final BondCreateSessions sessions = new BondCreateSessions();
    private final NamespacedKey amountInputKey;

    public BondCreateListener(RootBondsPlugin plugin) {
        this.plugin = plugin;
        this.amountInputKey = new NamespacedKey(plugin, "bond_amount_input");
    }

    public BondCreateSessions sessions() {
        return sessions;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof BondCreateMenuHolder holder) {
            handleCreateMenuClick(event, player, holder);
            return;
        }
        if (event.getInventory().getType() == InventoryType.ANVIL && sessions.get(player.getUniqueId()) != null) {
            handleAnvilClick(event, player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BondCreateMenuHolder) {
            event.setCancelled(true);
        }
        if (event.getInventory().getType() == InventoryType.ANVIL && event.getWhoClicked() instanceof Player player) {
            if (sessions.get(player.getUniqueId()) != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        BondCreateSessions.Session session = sessions.get(player.getUniqueId());
        if (session == null || session.anvilMode() == null) {
            return;
        }
        event.getInventory().clear();
        session.setAnvilMode(null);
        Bukkit.getScheduler().runTask(plugin, () -> {
            removeAmountInputItems(player);
            if (player.isOnline() && sessions.get(player.getUniqueId()) != null) {
                plugin.menuRegistry().openCreateMenu(player);
            }
        });
    }

    private void handleCreateMenuClick(InventoryClickEvent event, Player player, BondCreateMenuHolder holder) {
        if (!player.getUniqueId().equals(holder.playerId())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        BondCreateSessions.Session session = sessions.session(player.getUniqueId());
        if (slot == SLOT_BACK) {
            sessions.clear(player.getUniqueId());
            plugin.menuRegistry().open(player);
            return;
        }
        if (slot == SLOT_CUSTOM) {
            openAmountAnvil(player, session);
            return;
        }
        for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
            if (slot == AMOUNT_SLOTS[i]) {
                session.setAmount(AMOUNT_PRESETS[i]);
                plugin.menuRegistry().openCreateMenu(player);
                return;
            }
        }
        if (slot == SLOT_CONFIRM) {
            confirmCreate(player, session);
        }
    }

    private void handleAnvilClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getRawSlot() != 2) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta()) {
            return;
        }
        BondCreateSessions.Session session = sessions.get(player.getUniqueId());
        if (session == null || session.anvilMode() == null) {
            return;
        }
        String text = readPlainName(result)
                .replaceFirst("(?i)^Amount \\(G\\)\\s*", "");
        if (text.isBlank()) {
            return;
        }
        double amount;
        try {
            amount = GoldMoney.round(Double.parseDouble(text.trim()));
        } catch (NumberFormatException ex) {
            player.sendMessage(BondsMenuRegistry.legacyColor(plugin.msg("create-amount-invalid")));
            return;
        }
        if (amount + 1e-9 < plugin.bonds().config().minPrincipalG()) {
            player.sendMessage(BondsMenuRegistry.legacyColor(plugin.msg("create-min")
                    .replace("{min}", GoldMoney.format(plugin.bonds().config().minPrincipalG()))));
            return;
        }
        session.setAmount(amount);
        session.setAnvilMode(null);
        event.getView().getTopInventory().clear();
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            removeAmountInputItems(player);
            plugin.menuRegistry().openCreateMenu(player);
        });
    }

    private void confirmCreate(Player player, BondCreateSessions.Session session) {
        if (session.amount() + 1e-9 < plugin.bonds().config().minPrincipalG()) {
            player.sendMessage(BondsMenuRegistry.legacyColor(plugin.msg("create-amount-missing")));
            return;
        }
        var economy = RootMcEconomyResolver.resolve(plugin);
        double balance = economy == null ? 0 : economy.balance(player.getUniqueId());
        if (balance + 1e-9 < session.amount()) {
            player.sendMessage(BondsMenuRegistry.legacyColor(plugin.msg("create-insufficient")
                    .replace("{amount}", GoldMoney.format(session.amount()))
                    .replace("{balance}", GoldMoney.format(balance))));
            return;
        }
        if (plugin.bonds().createBond(player, session.amount())) {
            player.sendMessage(BondsMenuRegistry.legacyColor(plugin.msg("create-success")
                    .replace("{amount}", GoldMoney.format(session.amount()))));
            sessions.clear(player.getUniqueId());
            player.closeInventory();
            plugin.menuRegistry().open(player);
        } else {
            player.sendMessage(BondsMenuRegistry.legacyColor(plugin.msg("create-failed")));
        }
    }

    private void openAmountAnvil(Player player, BondCreateSessions.Session session) {
        session.setAnvilMode(BondCreateSessions.AnvilMode.AMOUNT);
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            InventoryView view = player.openAnvil(player.getLocation(), true);
            if (view == null) {
                return;
            }
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            if (meta != null) {
                String seed = session.amount() > 0 ? GoldMoney.format(session.amount()) : "Amount (G)";
                meta.displayName(Component.text(seed).decoration(TextDecoration.ITALIC, false));
                meta.getPersistentDataContainer().set(amountInputKey, PersistentDataType.BYTE, (byte) 1);
                paper.setItemMeta(meta);
            }
            view.setItem(0, paper);
        });
    }

    private static String readPlainName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return "";
        }
        return LEGACY.serialize(meta.displayName()).replaceAll("§.", "").trim();
    }

    private void removeAmountInputItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack != null
                    && stack.hasItemMeta()
                    && stack.getItemMeta().getPersistentDataContainer()
                            .has(amountInputKey, PersistentDataType.BYTE)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }
}
