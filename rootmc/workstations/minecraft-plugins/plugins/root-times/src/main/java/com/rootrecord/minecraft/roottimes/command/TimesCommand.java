package com.rootrecord.minecraft.roottimes.command;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import com.rootrecord.minecraft.roottimes.api.McDaySnapshot;
import com.rootrecord.minecraft.roottimes.clock.ClockService;
import com.rootrecord.minecraft.roottimes.clock.DayIdEpoch;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class TimesCommand implements CommandExecutor, TabCompleter {

    private final RootTimesPlugin plugin;

    public TimesCommand(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roottimes.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(ChatColor.GREEN + "Root-Times reloaded.");
            }
            case "resetday", "reanchor" -> {
                DayIdEpoch.clearPersisted(plugin);
                plugin.reloadAll();
                sender.sendMessage(ChatColor.GREEN
                        + "MC day re-anchored for this world · local day #"
                        + McDayClock.currentDayId()
                        + ChatColor.GRAY
                        + " (absolute "
                        + McDayClock.absoluteDayId()
                        + ")");
            }
            case "web" -> sender.sendMessage(
                    ChatColor.AQUA + "Web UI: " + ChatColor.WHITE + plugin.httpServer().publicUrl());
            default -> {
                McDaySnapshot snap = ClockService.snapshot();
                sender.sendMessage(ChatColor.GOLD + "Root-Times status");
                sender.sendMessage(ChatColor.GRAY + "Day #"
                        + snap.dayId()
                        + " · "
                        + snap.timeOfDayTicks()
                        + " ticks ("
                        + snap.phase()
                        + ")");
                sender.sendMessage(ChatColor.GRAY + "Length "
                        + McDayClock.lengthMinutes()
                        + " min · zone "
                        + McDayClock.zone().getId()
                        + " · enabled "
                        + McDayClock.enabled());
                sender.sendMessage(ChatColor.GRAY + "MySQL "
                        + (plugin.mysql().ready() ? "ready" : "not configured")
                        + " · Core "
                        + (plugin.corePresent() ? "present" : "absent (fallback ensure)"));
                if (plugin.timesConfig().webEnabled()) {
                    sender.sendMessage(ChatColor.GRAY + "Web " + plugin.httpServer().publicUrl());
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("status", "reload", "resetday", "web")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
