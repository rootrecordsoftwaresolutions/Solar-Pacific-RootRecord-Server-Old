package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReportCommand implements CommandExecutor {

    private final RootAdminPlugin plugin;
    private final Map<UUID, Long> lastReportAt = new ConcurrentHashMap<>();

    public ReportCommand(RootAdminPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!AdminPermissions.has(reporter, "report")) {
            reporter.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 1) {
            reporter.sendMessage(plugin.msg("report-usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            reporter.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            reporter.sendMessage(plugin.msg("report-self"));
            return true;
        }

        int cooldownSec = plugin.reportCooldownSeconds();
        if (cooldownSec > 0) {
            long now = System.currentTimeMillis();
            Long last = lastReportAt.get(reporter.getUniqueId());
            if (last != null) {
                long remain = cooldownSec * 1000L - (now - last);
                if (remain > 0) {
                    reporter.sendMessage(plugin.msg("report-cooldown")
                            .replace("{seconds}", String.valueOf((remain + 999) / 1000)));
                    return true;
                }
            }
            lastReportAt.put(reporter.getUniqueId(), now);
        }

        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
        String targetName = target.getName() != null ? target.getName() : args[0];
        plugin.reports().submit(reporter, target, reason);
        reporter.sendMessage(plugin.msg("report-submitted").replace("{player}", targetName));
        return true;
    }
}
