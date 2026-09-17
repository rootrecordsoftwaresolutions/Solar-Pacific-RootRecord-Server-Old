package com.rootrecord.minecraft.roottry;

import com.rootrecord.minecraft.common.ChatUi;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TryCommand implements CommandExecutor, TabCompleter {

    private final RootTryPlugin plugin;

    public TryCommand(RootTryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        TryService service = plugin.service();
        if (service == null) {
            sender.sendMessage("Root-Try is not ready.");
            return true;
        }
        TryConfig cfg = service.config();
        if (!cfg.enabled() && (args.length == 0 || !"reload".equalsIgnoreCase(args[0]))) {
            sender.sendMessage(service.color(cfg.prefix() + cfg.msgDisabled()));
            return true;
        }

        String sub = args.length == 0 ? "next" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!player.hasPermission("roottry.use")) {
                    return true;
                }
                list(player, service);
            }
            case "next" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!player.hasPermission("roottry.use")) {
                    return true;
                }
                next(player, service);
            }
            case "progress" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!player.hasPermission("roottry.use")) {
                    return true;
                }
                progress(player, service);
            }
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(service.color(cfg.prefix() + "&7Usage: /try info <id>"));
                    return true;
                }
                info(player, service, args[1]);
            }
            case "reload" -> {
                if (!sender.hasPermission("roottry.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(service.color(cfg.prefix() + "&aReloaded."));
            }
            case "skip" -> {
                if (!sender.hasPermission("roottry.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(service.color(cfg.prefix() + "&7Usage: /try skip <player> <id>"));
                    return true;
                }
                adminSkip(sender, service, args[1], args[2]);
            }
            case "forcecomplete" -> {
                if (!sender.hasPermission("roottry.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(service.color(cfg.prefix() + "&7Usage: /try forcecomplete <player> <id>"));
                    return true;
                }
                adminForce(sender, service, args[1], args[2]);
            }
            default -> {
                if (sender instanceof Player player && player.hasPermission("roottry.use")) {
                    next(player, service);
                } else {
                    sender.sendMessage(service.color(cfg.prefix() + "&7/try list|next|info|progress"));
                }
            }
        }
        return true;
    }

    private void list(Player player, TryService service) {
        Set<String> done = service.completed(player.getUniqueId());
        List<TryEntry> tries = service.hostTries();
        int finished = service.doneCount(player.getUniqueId());
        ChatUi.banner(player, "Try these");
        ChatUi.entry(player, "Progress", finished + "/" + tries.size());
        int shown = 0;
        for (TryEntry e : tries) {
            if (done.contains(e.id())) {
                continue;
            }
            // Short id tag + title — catalog style, not a wall
            String tag = shortTag(e.id());
            ChatUi.entryStatus(player, tag, e.title(), false, service.incompleteStatus(e));
            shown++;
            if (shown >= 8) {
                ChatUi.tip(player, "/try next  ·  /try info <id>");
                break;
            }
        }
        if (shown == 0) {
            ChatUi.entry(player, "Done", "every try on this host", "ok");
        }
    }

    private void next(Player player, TryService service) {
        TryEntry next = service.nextIncomplete(player);
        ChatUi.banner(player, "Try these");
        if (next == null) {
            ChatUi.entry(player, "Done", "every try on this host", "ok");
            return;
        }
        ChatUi.entryStatus(player, shortTag(next.id()), next.title(), false, service.incompleteStatus(next));
        if (next.hint() != null && !next.hint().isBlank()) {
            ChatUi.tip(player, next.hint());
        }
        progress(player, service);
    }

    private void progress(Player player, TryService service) {
        int total = service.hostTries().size();
        int done = service.doneCount(player.getUniqueId());
        ChatUi.entry(player, "Progress", done + "/" + total);
    }

    private void info(Player player, TryService service, String id) {
        TryEntry entry = service.catalog().get(id);
        if (entry == null || !entry.appliesToHost(service.activeHost())) {
            ChatUi.entry(player, "Try", "unknown · /try", "alert");
            return;
        }
        boolean done = service.completed(player.getUniqueId()).contains(entry.id());
        ChatUi.banner(player, "Try");
        ChatUi.entryStatus(player, shortTag(entry.id()), entry.title(), done, service.incompleteStatus(entry));
        if (entry.hint() != null && !entry.hint().isBlank()) {
            ChatUi.tip(player, entry.hint());
        }
    }

    /** eco.balance → Balance; travel.spawn → Spawn */
    private static String shortTag(String id) {
        if (id == null || id.isBlank()) {
            return "Try";
        }
        int dot = id.lastIndexOf('.');
        String raw = dot >= 0 && dot + 1 < id.length() ? id.substring(dot + 1) : id;
        if (raw.isEmpty()) {
            return "Try";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private void adminSkip(CommandSender sender, TryService service, String playerName, String tryId) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = target.getUniqueId();
        if (!service.adminSkip(uuid, tryId)) {
            sender.sendMessage(service.color(service.config().prefix() + "&cSkip failed (unknown id or DB)."));
            return;
        }
        sender.sendMessage(service.color(service.config().prefix() + service.config().msgAdminSkipped()
                .replace("{id}", tryId)
                .replace("{player}", playerName)));
    }

    private void adminForce(CommandSender sender, TryService service, String playerName, String tryId) {
        TryEntry entry = service.catalog().get(tryId);
        if (entry == null) {
            sender.sendMessage(service.color(service.config().prefix() + "&cUnknown try id."));
            return;
        }
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            service.complete(online, entry, true);
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            boolean ok = service.adminSkip(target.getUniqueId(), tryId);
            if (!ok) {
                sender.sendMessage(service.color(service.config().prefix() + "&cForce failed (player offline + DB)."));
                return;
            }
        }
        sender.sendMessage(service.color(service.config().prefix() + service.config().msgAdminForced()
                .replace("{id}", tryId)
                .replace("{player}", playerName)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("list", "next", "info", "progress"));
            if (sender.hasPermission("roottry.admin")) {
                base.addAll(Arrays.asList("reload", "skip", "forcecomplete"));
            }
            String p = args[0].toLowerCase(Locale.ROOT);
            return base.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }
        if (args.length == 2 && "info".equalsIgnoreCase(args[0]) && plugin.service() != null) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return plugin.service().hostTries().stream()
                    .map(TryEntry::id)
                    .filter(id -> id.startsWith(p))
                    .limit(30)
                    .collect(Collectors.toList());
        }
        if (args.length == 2
                && ("skip".equalsIgnoreCase(args[0]) || "forcecomplete".equalsIgnoreCase(args[0]))
                && sender.hasPermission("roottry.admin")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .collect(Collectors.toList());
        }
        if (args.length == 3
                && ("skip".equalsIgnoreCase(args[0]) || "forcecomplete".equalsIgnoreCase(args[0]))
                && plugin.service() != null) {
            String p = args[2].toLowerCase(Locale.ROOT);
            return plugin.service().catalog().all().stream()
                    .map(TryEntry::id)
                    .filter(id -> id.startsWith(p))
                    .limit(30)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
