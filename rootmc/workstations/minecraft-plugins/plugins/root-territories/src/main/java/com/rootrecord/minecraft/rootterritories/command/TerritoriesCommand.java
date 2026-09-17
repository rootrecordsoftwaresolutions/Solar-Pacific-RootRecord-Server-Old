package com.rootrecord.minecraft.rootterritories.command;

import com.rootrecord.minecraft.rootterritories.RootTerritoriesPlugin;
import com.rootrecord.minecraft.rootterritories.config.TerritoriesConfig;
import com.rootrecord.minecraft.rootterritories.model.NationTerritory;
import com.rootrecord.minecraft.rootterritories.model.TownTerritory;
import com.rootrecord.minecraft.rootterritories.model.WildernessAt;
import com.rootrecord.minecraft.rootterritories.prefs.TerritoryNotificationStore;
import com.rootrecord.minecraft.rootterritories.service.TerritoryService;
import com.rootrecord.minecraft.rootterritories.towny.TownyResidents;
import com.rootrecord.minecraft.rootterritories.util.TerritoryMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TerritoriesCommand implements CommandExecutor, TabCompleter {

    private final RootTerritoriesPlugin plugin;
    private final TerritoryService territories;
    private final TerritoryNotificationStore notifications;

    public TerritoriesCommand(
            RootTerritoriesPlugin plugin,
            TerritoryService territories,
            TerritoryNotificationStore notifications) {
        this.plugin = plugin;
        this.territories = territories;
        this.notifications = notifications;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            return handleReload(sender, label);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TerritoryMessages.colorize("&cPlayers only."));
            return true;
        }
        if (args.length == 0 || "info".equalsIgnoreCase(args[0]) || "status".equalsIgnoreCase(args[0])) {
            sendInfo(player);
            return true;
        }
        if ("notifications".equalsIgnoreCase(args[0]) || "notify".equalsIgnoreCase(args[0])) {
            handleNotifications(player, args);
            return true;
        }
        sendUsage(player, label);
        return true;
    }

    private boolean handleReload(CommandSender sender, String label) {
        if (!sender.hasPermission("rootterritories.reload")) {
            sender.sendMessage(TerritoryMessages.colorize("&cYou don't have permission."));
            return true;
        }
        plugin.reloadAll();
        int nations = territories.nations().size();
        int regions = territories.nations().stream().mapToInt(n -> n.regions().size()).sum();
        int towns = territories.independentTowns().size();
        sender.sendMessage(TerritoryMessages.colorize(
                "&aRoot-Territories reloaded — &f" + nations + "&a nation(s), &f" + towns
                        + "&a independent town(s), &f" + regions + "&a nation region(s)."));
        return true;
    }

    private void handleNotifications(Player player, String[] args) {
        TerritoriesConfig cfg = territories.config();
        if (args.length == 1) {
            boolean enabled = notifications.toggle(player.getUniqueId());
            TerritoryMessages.sendLine(player, enabled ? cfg.notificationsEnabled() : cfg.notificationsDisabled());
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "enable" -> {
                notifications.setEnabled(player.getUniqueId(), true);
                TerritoryMessages.sendLine(player, cfg.notificationsEnabled());
            }
            case "off", "disable" -> {
                notifications.setEnabled(player.getUniqueId(), false);
                TerritoryMessages.sendLine(player, cfg.notificationsDisabled());
            }
            case "status" -> TerritoryMessages.sendLine(
                    player,
                    notifications.isEnabled(player.getUniqueId())
                            ? cfg.notificationsStatusOn()
                            : cfg.notificationsStatusOff());
            default -> sendUsage(player, "territories");
        }
    }

    private void sendInfo(Player player) {
        TerritoriesConfig cfg = territories.config();
        var loc = player.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        double px = Math.floor(loc.getX()) + 0.5;
        double pz = Math.floor(loc.getZ()) + 0.5;
        String world = loc.getWorld().getName();
        Set<String> zones = territories.zonesAt(world, px, pz);
        WildernessAt wilderness = territories.wildernessAt(world, px, pz);
        Map<String, NationTerritory> nationIndex = territories.nationIndex();
        Map<String, TownTerritory> townIndex = territories.townIndex();

        List<String> lines = new ArrayList<>();
        lines.add("&7— &bborder intelligence");
        lines.add("");

        if (zones.isEmpty()) {
            lines.add("&7You are in &funclaimed wilderness&7 — No influences.");
        } else {
            lines.add("&7Influence at your position:");
            for (String key : zones) {
                if (key.startsWith("nation:")) {
                    NationTerritory nation = nationIndex.get(key);
                    if (nation == null) {
                        continue;
                    }
                    lines.add("  &6✦ Nation &f" + nation.nationName()
                            + " &8(&7capital &f" + (int) cfg.nationCapitalBuffer()
                            + "&7, members &f" + (int) cfg.townBuffer() + "&8)");
                    appendWildernessLine(lines, wilderness, nation.nationName(), null);
                } else if (key.startsWith("town:")) {
                    TownTerritory town = townIndex.get(key);
                    if (town == null) {
                        continue;
                    }
                    lines.add("  &9✦ Town &f" + town.townName()
                            + " &8(&7" + (int) cfg.townBuffer() + " blocks from claims&8)");
                    if (wilderness.townNames().contains(town.townName())) {
                        lines.add("    &7Wilderness band — satellite alerts active for residents");
                    }
                }
            }
        }

        String playerTown = TownyResidents.playerTownName(player);
        String playerNation = TownyResidents.playerNationName(player);
        lines.add("");
        if (playerTown != null || playerNation != null) {
            lines.add("&7Your allegiance:");
            if (playerNation != null) {
                lines.add("  &6Nation &f" + playerNation);
            }
            if (playerTown != null) {
                lines.add("  &9Town &f" + playerTown);
            }
        }

        lines.add("");
        lines.add(notifications.isEnabled(player.getUniqueId())
                ? cfg.notificationsStatusOn()
                : cfg.notificationsStatusOff());

        for (String line : lines) {
            TerritoryMessages.sendLine(player, line);
        }
    }

    private static void appendWildernessLine(
            List<String> lines,
            WildernessAt wilderness,
            String nationName,
            String townName) {
        if (townName != null && wilderness.townNames().contains(townName)) {
            lines.add("    &7Wilderness band — satellite alerts active for residents");
            return;
        }
        if (nationName != null && wilderness.nationNames().contains(nationName)) {
            lines.add("    &7Nation wilderness — satellite alerts active for members");
        }
    }

    private void sendUsage(Player player, String label) {
        TerritoryMessages.sendLine(player, "&7Usage:");
        TerritoryMessages.sendLine(player, "&f/" + label + " &8— &7where you stand");
        TerritoryMessages.sendLine(player, "&f/" + label + " notifications [on|off] &8— &7border messages");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(List.of("info", "notifications"));
            if (sender.hasPermission("rootterritories.reload")) {
                options.add("reload");
            }
            return options.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && ("notifications".equalsIgnoreCase(args[0]) || "notify".equalsIgnoreCase(args[0]))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("on", "off", "status").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
