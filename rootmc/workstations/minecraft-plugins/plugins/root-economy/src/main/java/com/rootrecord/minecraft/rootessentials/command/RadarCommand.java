package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.service.GoldProspectService;
import com.rootrecord.minecraft.rootessentials.service.GoldSiteRates;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** {@code /radar} — paid ore compass (gold, diamond, iron, coal, lapis, redstone, emerald). */
public final class RadarCommand implements CommandExecutor, TabCompleter {

    private final RootEconomyPlugin plugin;

    public RadarCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!Permissions.has(player, "radar") && !Permissions.has(player, "gold")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        GoldProspectService radar = plugin.goldProspect();
        if (radar == null) {
            player.sendMessage(plugin.colorize("&cRadar is offline."));
            return true;
        }
        String sub = args != null && args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "toggle";
        return switch (sub) {
            case "on", "start", "scan" -> {
                radar.activate(player);
                yield true;
            }
            case "off", "stop" -> {
                if (radar.isActive(player)) {
                    radar.deactivate(player, "off");
                } else {
                    player.sendMessage(plugin.colorize("&7Radar is already off."));
                }
                yield true;
            }
            case "status", "info", "help" -> {
                sendInfo(player, radar);
                yield true;
            }
            case "toggle", "" -> {
                radar.toggle(player);
                yield true;
            }
            default -> {
                sendInfo(player, radar);
                yield true;
            }
        };
    }

    private void sendInfo(Player player, GoldProspectService radar) {
        ChatUi.banner(player, "Ore radar");
        ChatUi.entry(player, "Status", radar.isActive(player) ? "on" : "off", radar.isActive(player) ? "ok" : "open");
        ChatUi.entry(player, "Range", GoldSiteRates.DETECT_RADIUS + " blocks");
        ChatUi.entry(player, "Cost", "1 G/min · " + plugin.money(GoldProspectService.PING_COST) + " G / ping");
        ChatUi.entry(player, "Idle", "no move for " + GoldProspectService.IDLE_PINGS + " pings → off");
        ChatUi.section(player, "Compass tape");
        ChatUi.tip(player, "▲ ahead · N E S W scroll · colored * ore pips (max 3)");
        ChatUi.tip(player, "suffix = closest ore + ↑/↓ + count · coal gray · iron white");
        ChatUi.tip(player, "gold yellow · diamond aqua · lapis blue · redstone red · emerald green");
        ChatUi.section(player, "Mine pay");
        ChatUi.entry(player, "Gold ore", "Notes to wallet · live /gold multiplier");
        ChatUi.entry(player, "Redeem", "/mint gold <amount|max> → nuggets/ingots/blocks");
        ChatUi.tip(player, "/radar  |  /radar on  |  /radar off  |  /gold  |  /mint");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return List.of("on", "off", "status").stream().filter(s -> s.startsWith(p)).toList();
        }
        return List.of();
    }
}
