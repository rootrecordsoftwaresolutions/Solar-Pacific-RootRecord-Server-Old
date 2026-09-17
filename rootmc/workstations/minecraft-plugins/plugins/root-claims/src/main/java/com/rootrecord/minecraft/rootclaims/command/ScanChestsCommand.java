package com.rootrecord.minecraft.rootclaims.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.rootclaims.ClaimChestService;
import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scan the player's claim chests for an item, then confirm withdraw into inventory.
 */
public final class ScanChestsCommand implements CommandExecutor, TabCompleter {

    private record Pending(Material material, int available, long createdAtMs) {}

    private final RootClaimsPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ScanChestsCommand(RootClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootclaims.use") && !player.isOp()) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        ClaimChestService chests = plugin.claimChests();
        if (chests == null) {
            player.sendMessage(plugin.msg("scanchests-unavailable"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.msg("scanchests-usage"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("cancel".equals(sub)) {
            pending.remove(player.getUniqueId());
            player.sendMessage(plugin.msg("scanchests-cancelled"));
            return true;
        }
        if ("confirm".equals(sub)) {
            return handleConfirm(player, chests, args);
        }

        Material material = resolveMaterial(player, args[0]);
        if (material == null || material.isAir() || !material.isItem()) {
            player.sendMessage(plugin.msg("scanchests-unknown").replace("{query}", args[0]));
            return true;
        }
        int count = chests.countOwned(player.getUniqueId(), material);
        String id = material.getKey().getKey();
        if (count <= 0) {
            pending.remove(player.getUniqueId());
            player.sendMessage(plugin.msg("scanchests-none")
                    .replace("{item}", id)
                    .replace("{count}", "0"));
            return true;
        }
        pending.put(player.getUniqueId(), new Pending(material, count, System.currentTimeMillis()));
        player.sendMessage(plugin.msg("scanchests-found")
                .replace("{item}", id)
                .replace("{count}", String.valueOf(count)));
        player.sendMessage(ChatLinks.confirmCancel("/scanchests confirm", "/scanchests cancel"));
        player.sendMessage(plugin.msg("scanchests-amount-hint")
                .replace("{count}", String.valueOf(count)));
        return true;
    }

    private boolean handleConfirm(Player player, ClaimChestService chests, String[] args) {
        Pending order = pending.get(player.getUniqueId());
        if (order == null) {
            player.sendMessage(plugin.msg("scanchests-no-pending"));
            return true;
        }
        if (System.currentTimeMillis() - order.createdAtMs() > 120_000L) {
            pending.remove(player.getUniqueId());
            player.sendMessage(plugin.msg("scanchests-expired"));
            return true;
        }
        int want = order.available();
        if (args.length >= 2) {
            try {
                want = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.msg("scanchests-bad-amount"));
                return true;
            }
        }
        if (want <= 0) {
            player.sendMessage(plugin.msg("scanchests-bad-amount"));
            return true;
        }
        want = Math.min(want, order.available());
        pending.remove(player.getUniqueId());
        int got = chests.withdrawOwned(player, order.material(), want);
        String id = order.material().getKey().getKey();
        if (got <= 0) {
            player.sendMessage(plugin.msg("scanchests-none")
                    .replace("{item}", id)
                    .replace("{count}", "0"));
            return true;
        }
        player.sendMessage(plugin.msg("scanchests-withdrawn")
                .replace("{item}", id)
                .replace("{count}", String.valueOf(got)));
        return true;
    }

    private static Material resolveMaterial(Player player, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("hand".equalsIgnoreCase(raw) || "held".equalsIgnoreCase(raw)) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                return null;
            }
            return hand.getType();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        Material matched = Material.matchMaterial(key);
        if (matched != null) {
            return matched;
        }
        return Material.matchMaterial(key, true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("hand");
            base.add("confirm");
            base.add("cancel");
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isAir()) {
                    base.add(material.getKey().getKey());
                }
            }
            return StringUtil.copyPartialMatches(args[0], base, new ArrayList<>());
        }
        if (args.length == 2 && "confirm".equalsIgnoreCase(args[0]) && sender instanceof Player player) {
            Pending order = pending.get(player.getUniqueId());
            if (order != null) {
                return StringUtil.copyPartialMatches(
                        args[1],
                        List.of(String.valueOf(order.available()), "1", "64"),
                        new ArrayList<>());
            }
        }
        return List.of();
    }
}
