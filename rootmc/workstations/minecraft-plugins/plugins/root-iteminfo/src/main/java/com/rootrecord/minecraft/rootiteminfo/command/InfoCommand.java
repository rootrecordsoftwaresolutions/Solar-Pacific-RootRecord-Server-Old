package com.rootrecord.minecraft.rootiteminfo.command;

import com.rootrecord.minecraft.rootiteminfo.RootItemInfoPlugin;
import com.rootrecord.minecraft.rootiteminfo.census.ItemCensusScanner;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public final class InfoCommand implements CommandExecutor, TabCompleter {

    private static final DecimalFormat COUNT = new DecimalFormat("#,###");
    private static final DecimalFormat GOLD = new DecimalFormat("0.###");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d HH:mm z")
            .withZone(ZoneId.of("Pacific/Honolulu"));

    private final RootItemInfoPlugin plugin;

    public InfoCommand(RootItemInfoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.featureEnabled()) {
            sender.sendMessage(plugin.msg("disabled"));
            return true;
        }
        if (!sender.hasPermission("rootiteminfo.use") && !sender.isOp()) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rootiteminfo.reload") && !sender.isOp()) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadLocalConfig();
            plugin.scanner().requestFullScan("reload");
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            if (!sender.hasPermission("rootiteminfo.admin") && !sender.isOp()) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            sendTop(sender, 15);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("gold")) {
            sendGoldSummary(sender);
            return true;
        }

        Material material = resolveTarget(sender, args);
        if (material == null || material.isAir()) {
            sender.sendMessage(plugin.msg("unknown-item").replace("{query}", args.length > 0 ? args[0] : "hand"));
            sender.sendMessage(plugin.msg("usage"));
            return true;
        }
        sendItemInfo(sender, material);
        return true;
    }

    private Material resolveTarget(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("hand") || args[0].equalsIgnoreCase("held")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("players-only"));
                return null;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                return null;
            }
            return hand.getType();
        }
        return plugin.values().resolveMaterial(args[0]);
    }

    private void sendItemInfo(CommandSender sender, Material material) {
        String id = RootItemInfoPlugin.materialKey(material);
        long count = plugin.store().count(id);
        OptionalDouble avg = plugin.values().averageUnitG(material);

        sender.sendMessage(plugin.msg("line-id").replace("{id}", id));
        sender.sendMessage(plugin.msg("line-total").replace("{count}", COUNT.format(count)));
        if (avg.isPresent()) {
            sender.sendMessage(plugin.msg("line-avg").replace("{avg}", GOLD.format(avg.getAsDouble())));
        } else {
            sender.sendMessage(plugin.msg("line-avg-none"));
        }
        if (ItemCensusScanner.isGoldMaterial(material)) {
            double goldG = count * ItemCensusScanner.mintPegG(material);
            sender.sendMessage(plugin.msg("line-gold-g").replace("{gold}", GOLD.format(goldG)));
        }
        sender.sendMessage(plugin.msg("line-as-of").replace("{when}", formatWhen()));
    }

    private void sendGoldSummary(CommandSender sender) {
        Map<String, Long> snap = plugin.store().snapshot();
        long nuggets = snap.getOrDefault("gold_nugget", 0L);
        long raw = snap.getOrDefault("raw_gold", 0L);
        long ingots = snap.getOrDefault("gold_ingot", 0L);
        long blocks = snap.getOrDefault("gold_block", 0L);
        long rawBlocks = snap.getOrDefault("raw_gold_block", 0L);
        double pegG = nuggets / 9.0 + raw + ingots + blocks * 9.0 + rawBlocks * 9.0;

        sender.sendMessage(plugin.colorize("&6Gold items (scanned world)"));
        sender.sendMessage(plugin.colorize("&7nugget &f" + COUNT.format(nuggets)
                + " &8| &7raw &f" + COUNT.format(raw)
                + " &8| &7ingot &f" + COUNT.format(ingots)
                + " &8| &7block &f" + COUNT.format(blocks)
                + (rawBlocks > 0 ? " &8| &7raw_block &f" + COUNT.format(rawBlocks) : "")));
        sender.sendMessage(plugin.msg("line-gold-g").replace("{gold}", GOLD.format(pegG)));
        sender.sendMessage(plugin.msg("line-as-of").replace("{when}", formatWhen()));
    }

    private void sendTop(CommandSender sender, int n) {
        List<Map.Entry<String, Long>> top = plugin.store().snapshot().entrySet().stream()
                .limit(n)
                .collect(Collectors.toList());
        sender.sendMessage(plugin.msg("top-header").replace("{n}", String.valueOf(top.size())));
        int rank = 1;
        for (Map.Entry<String, Long> e : top) {
            sender.sendMessage(plugin.msg("top-line")
                    .replace("{rank}", String.valueOf(rank++))
                    .replace("{id}", e.getKey())
                    .replace("{count}", COUNT.format(e.getValue())));
        }
        sender.sendMessage(plugin.msg("line-as-of").replace("{when}", formatWhen()));
    }

    private String formatWhen() {
        long epoch = plugin.store().scannedAtEpochMs();
        if (epoch <= 0) {
            return "never";
        }
        return WHEN.format(Instant.ofEpochMilli(epoch));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("hand", "gold", "top", "reload"));
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isAir()) {
                    base.add(RootItemInfoPlugin.materialKey(material));
                }
            }
            return StringUtil.copyPartialMatches(args[0], base, new ArrayList<>());
        }
        return List.of();
    }
}
