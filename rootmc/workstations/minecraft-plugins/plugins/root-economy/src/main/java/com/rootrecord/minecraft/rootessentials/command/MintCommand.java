package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.common.SystemGoldPayout;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType;
import com.rootrecord.minecraft.rootessentials.chat.HostPowerInfo;
import com.rootrecord.minecraft.rootessentials.treasury.EconomyNoteSupplyDisplay;
import com.rootrecord.minecraft.rootessentials.util.GoldItemValue;
import com.rootrecord.minecraft.rootessentials.util.GoldMintHelper;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MintCommand implements CommandExecutor, TabCompleter {

    private final RootEconomyPlugin plugin;

    public MintCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!Permissions.has(player, "mint")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sendUsage(player);
            return true;
        }
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "hand" -> mintHand(player);
                case "all" -> mintAll(player);
                case "gold" -> mintGold(player, args);
                case "info", "help" -> {
                    sendUsage(player);
                    yield true;
                }
                default -> {
                    sendUsage(player);
                    yield true;
                }
            };
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cMint failed: &f" + ex.getMessage()));
            return true;
        }
    }

    private void sendUsage(Player player) {
        ChatUi.banner(player, "Mint");
        ChatUi.tip(player, "/mint hand | all | gold <amount|max>");
        try {
            var snap = plugin.reserveSnapshot(player.getUniqueId());
            String cur = plugin.currency();
            var supply = snap.noteSupply();
            ChatUi.gold(player, "Reserve", plugin.money(supply.reserveNotesG()), cur);
            ChatUi.gold(player, "Minted", plugin.money(supply.goldMinedG()), cur);
            ChatUi.gold(player, "Notes", plugin.money(supply.totalNotesG()), cur);
            EconomyNoteSupplyDisplay.sendBackingLine(player, supply);
            ChatUi.row(player, "Peg", "nugget 1/9 | ingot 1 | block 9");
            EconomyNoteSupplyDisplay.sendMintTaxLine(plugin, player, plugin.treasury());
        } catch (Exception ex) {
            ChatUi.row(player, "Peg", "nugget 1/9 | ingot 1 | block 9");
            EconomyNoteSupplyDisplay.sendMintTaxLine(plugin, player, plugin.treasury());
        }
        HostPowerInfo.sendMintRules(player);
        ChatUi.links(
                player,
                "Reserve", RootMcEconomyWeb.reserve(),
                "Economy", RootMcEconomyWeb.economy());
        ChatUi.tip(player, "/gold for mine rates  |  /tax  |  /reserve");
    }

    private boolean mintHand(Player player) throws Exception {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(plugin.msg("sell-empty-hand"));
            return true;
        }
        Double each = plugin.mintRate(hand.getType());
        if (each == null || each <= 0) {
            player.sendMessage(plugin.msg("mint-not-gold").replace("{item}", hand.getType().name()));
            return true;
        }
        int amount = hand.getAmount();
        double total = each * amount;
        recordMintToWallet(player, hand, "MINT_HAND");
        hand.setAmount(0);
        RootMcIncomeSweepResult sweep = plugin.mintIncomeAfterTax(player.getUniqueId(), player.getName(), total);
        sendMintSummary(player, amount, total, sweep);
        return true;
    }

    private boolean mintAll(Player player) throws Exception {
        int minted = 0;
        double total = 0;
        var inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Double each = plugin.mintRate(stack.getType());
            if (each == null || each <= 0) {
                continue;
            }
            recordMintToWallet(player, stack, "MINT_ALL");
            minted += stack.getAmount();
            total += each * stack.getAmount();
            inv.setItem(slot, null);
        }
        if (minted <= 0 || total <= 0) {
            player.sendMessage(plugin.msg("mint-nothing"));
            return true;
        }
        RootMcIncomeSweepResult sweep = plugin.mintIncomeAfterTax(player.getUniqueId(), player.getName(), total);
        sendMintSummary(player, minted, total, sweep);
        return true;
    }

    private boolean mintGold(Player player, String[] args) throws Exception {
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: &f/mint gold <amount|max>"));
            return true;
        }
        double balance = plugin.balance(player.getUniqueId(), player.getName());
        int walletUnits = GoldMintHelper.toNuggetUnits(balance);
        int units;
        if (args[1].equalsIgnoreCase("max")) {
            units = GoldMintHelper.maxUnitsForStorage(player.getInventory(), walletUnits);
        } else {
            double requested;
            try {
                requested = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.colorize("&eUsage: &f/mint gold <amount|max>"));
                return true;
            }
            if (requested <= 0) {
                player.sendMessage(plugin.msg("mint-gold-invalid"));
                return true;
            }
            units = GoldMintHelper.toNuggetUnits(requested);
            if (units < 1) {
                player.sendMessage(plugin.msg("mint-gold-too-small"));
                return true;
            }
            double cost = GoldMintHelper.goldCost(units);
            if (balance + 1e-9 < cost) {
                player.sendMessage(plugin.msg("mint-gold-insufficient")
                        .replace("{amount}", plugin.money(cost))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
                return true;
            }
            if (!GoldMintHelper.fitsInStorage(player.getInventory(), units)) {
                player.sendMessage(plugin.msg("mint-gold-no-space"));
                return true;
            }
        }
        if (units < 1) {
            if (walletUnits < 1) {
                player.sendMessage(plugin.msg("mint-gold-insufficient")
                        .replace("{amount}", plugin.money(1.0 / 9.0))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
            } else {
                player.sendMessage(plugin.msg("mint-gold-no-space"));
            }
            return true;
        }
        double cost = GoldMintHelper.goldCost(units);
        ItemStack[] inventoryBefore = player.getInventory().getContents().clone();
        GoldMintHelper.Condensed condensed = GoldMintHelper.condense(units);
        ItemStack[] stacks = GoldMintHelper.toItemStacks(condensed).toArray(ItemStack[]::new);
        SystemGoldPayout.mark(stacks);
        var leftover = player.getInventory().addItem(stacks);
        if (!leftover.isEmpty()) {
            player.sendMessage(plugin.msg("mint-gold-no-space"));
            return true;
        }
        if (!plugin.redeemMintGold(player.getUniqueId(), player.getName(), cost)) {
            player.getInventory().setContents(inventoryBefore);
            player.sendMessage(plugin.msg("mint-gold-insufficient")
                    .replace("{amount}", plugin.money(cost))
                    .replace("{balance}", plugin.money(balance))
                    .replace("{currency}", plugin.currency()));
            return true;
        }
        recordMintToItems(player, condensed, cost);
        player.sendMessage(plugin.msg("mint-gold-success")
                .replace("{amount}", plugin.money(cost))
                .replace("{currency}", plugin.currency())
                .replace("{summary}", formatCondensed(condensed)));
        return true;
    }

    private static String formatCondensed(GoldMintHelper.Condensed condensed) {
        List<String> parts = new ArrayList<>();
        if (condensed.blocks() > 0) {
            parts.add(condensed.blocks() + " block" + (condensed.blocks() == 1 ? "" : "s"));
        }
        if (condensed.ingots() > 0) {
            parts.add(condensed.ingots() + " ingot" + (condensed.ingots() == 1 ? "" : "s"));
        }
        if (condensed.nuggets() > 0) {
            parts.add(condensed.nuggets() + " nugget" + (condensed.nuggets() == 1 ? "" : "s"));
        }
        return parts.isEmpty() ? "0 items" : String.join(", ", parts);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !Permissions.has(sender, "mint")) {
            return List.of();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("hand", "all", "gold", "info", "help"), args[0]);
        }
        if (args.length == 2 && "gold".equalsIgnoreCase(args[0])) {
            return filterPrefix(List.of("max", "1", "9", "10"), args[1]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    private void recordMintToWallet(Player player, ItemStack stack, String obtainedVia) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        plugin.recordGoldItemEvent(
                player.getUniqueId(),
                player.getName(),
                GoldItemEventType.MINT_TO_WALLET,
                obtainedVia,
                stack.getType(),
                stack.getAmount(),
                GoldItemValue.stackValue(stack),
                player.getLocation(),
                null);
    }

    private void recordMintToItems(Player player, GoldMintHelper.Condensed condensed, double costG) {
        if (condensed.blocks() > 0) {
            plugin.recordGoldItemEvent(
                    player.getUniqueId(),
                    player.getName(),
                    GoldItemEventType.MINT_TO_ITEMS,
                    "MINT_GOLD",
                    Material.GOLD_BLOCK,
                    condensed.blocks(),
                    condensed.blocks() * 9.0,
                    player.getLocation(),
                    "{\"wallet_g\":" + costG + "}");
        }
        if (condensed.ingots() > 0) {
            plugin.recordGoldItemEvent(
                    player.getUniqueId(),
                    player.getName(),
                    GoldItemEventType.MINT_TO_ITEMS,
                    "MINT_GOLD",
                    Material.GOLD_INGOT,
                    condensed.ingots(),
                    condensed.ingots() * 1.0,
                    player.getLocation(),
                    "{\"wallet_g\":" + costG + "}");
        }
        if (condensed.nuggets() > 0) {
            plugin.recordGoldItemEvent(
                    player.getUniqueId(),
                    player.getName(),
                    GoldItemEventType.MINT_TO_ITEMS,
                    "MINT_GOLD",
                    Material.GOLD_NUGGET,
                    condensed.nuggets(),
                    condensed.nuggets() / 9.0,
                    player.getLocation(),
                    "{\"wallet_g\":" + costG + "}");
        }
    }

    private void sendMintSummary(Player player, int count, double gross, RootMcIncomeSweepResult sweep) {
        double toLoan = sweep != null ? Math.max(0, sweep.toLoanRepaid()) : 0;
        double toWallet = sweep != null ? Math.max(0, sweep.toWallet()) : gross;
        double tax = Math.max(0, gross - toLoan - toWallet);
        player.sendMessage(plugin.colorize(
                "&aMinted &f" + count + "&a item(s): gross &f" + plugin.money(gross) + " " + plugin.currency()
                        + "&a | tax &f" + plugin.money(tax)
                        + "&a | loan sweep &f" + plugin.money(toLoan)
                        + "&a | wallet +&f" + plugin.money(toWallet) + " " + plugin.currency()));
    }
}
