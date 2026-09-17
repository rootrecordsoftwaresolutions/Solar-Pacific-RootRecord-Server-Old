package com.rootrecord.minecraft.rootheads;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class HeadsCommand implements CommandExecutor, TabCompleter {

    private final RootHeadsPlugin plugin;

    public HeadsCommand(RootHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.colorize("&e/rootheads reload | give <player> <mob|player> [amount] | inspect"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "inspect" -> handleInspect(sender);
            default -> {
                sender.sendMessage(plugin.colorize("&e/rootheads reload | give <player> <mob|player> [amount] | inspect"));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rootheads.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(plugin.msg("reload"));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootheads.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("give-usage"));
            return true;
        }
        Player target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[1]));
            return true;
        }
        String mobId = args[2].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.msg("give-usage"));
                return true;
            }
        }
        amount = Math.max(1, Math.min(64, amount));

        String version = plugin.getPluginMeta().getVersion();
        if ("player".equals(mobId)) {
            if (!plugin.config().playerHeadsEnabled()) {
                sender.sendMessage(plugin.msg("disabled"));
                return true;
            }
            for (int i = 0; i < amount; i++) {
                giveOrDrop(target, plugin.items().createPlayerHead(target, plugin.config().playerHeads(), version));
            }
        } else {
            HeadsConfig.MobHead mob = plugin.config().mob(mobId);
            if (mob == null) {
                String list = String.join(", ", plugin.config().mobs().keySet()) + ", player";
                sender.sendMessage(plugin.msg("unknown-mob").replace("{mobs}", list));
                return true;
            }
            for (int i = 0; i < amount; i++) {
                giveOrDrop(target, plugin.items().createMobHead(mob, version));
            }
        }
        sender.sendMessage(plugin.msg("give-ok")
                .replace("{amount}", String.valueOf(amount))
                .replace("{mob}", mobId)
                .replace("{player}", target.getName()));
        return true;
    }

    private boolean handleInspect(CommandSender sender) {
        if (!sender.hasPermission("rootheads.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cPlayers only."));
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!plugin.items().isRootHead(hand)) {
            sender.sendMessage(plugin.msg("inspect-no"));
            return true;
        }
        String kind = plugin.items().readKind(hand);
        String mob = plugin.items().readMobId(hand);
        String owner = plugin.items().readOwnerUuid(hand);
        String label = mob == null ? "?" : mob;
        if (HeadItemFactory.KIND_PLAYER.equals(kind) && owner != null) {
            label = "player (" + owner + ")";
        }
        sender.sendMessage(plugin.msg("inspect-yes").replace("{mob}", label));
        return true;
    }

    private static void giveOrDrop(Player target, ItemStack stack) {
        var leftover = target.getInventory().addItem(stack);
        leftover.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
    }

    private static Player resolvePlayer(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rootheads.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("reload", "give", "inspect"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>(plugin.config().mobs().keySet());
            ids.add("player");
            return filter(ids, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "64"), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
