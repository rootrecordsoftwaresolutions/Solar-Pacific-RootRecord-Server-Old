package com.rootrecord.minecraft.rootrewards.command;

import com.rootrecord.minecraft.rootrewards.PlaytimeMilestones;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootrewards.data.PlaytimeRow;
import com.rootrecord.minecraft.rootrewards.service.PlaytimeRewardService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private final RootRewardsPlugin plugin;

    public PlaytimeCommand(RootRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "top".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("rootrewards.playtime.top")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            runAsync(sender, () -> sendTop(sender));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.colorize("&eUsage: /playtime <player> &7or &f/playtime top"));
                return true;
            }
            if (!player.hasPermission("rootrewards.use")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            runAsync(player, () -> sendStatus(player, player.getUniqueId(), player.getName(), true));
            return true;
        }

        if (!sender.hasPermission("rootrewards.playtime.others")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        String targetName = args[0];
        runAsync(sender, () -> {
            try {
                UUID uuid = resolveUuid(targetName);
                if (uuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.msg(plugin.rawMsg("playtime-player-not-found")
                                    .replace("{player}", targetName))));
                    return;
                }
                String display = displayName(uuid, targetName);
                if (sender instanceof Player player) {
                    Bukkit.getScheduler().runTask(plugin, () -> sendStatus(player, uuid, display, uuid.equals(player.getUniqueId())));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> sendStatusConsole(sender, uuid, display));
                }
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.colorize("&cCould not load playtime: &f" + ex.getMessage())));
            }
        });
        return true;
    }

    private void sendTop(CommandSender sender) {
        int limit = plugin.rewardsConfig().playtimeTopLimit();
        try {
            List<PlaytimeRow> rows = plugin.store().topPlaytime(limit);
            UUID viewerUuid = sender instanceof Player player ? player.getUniqueId() : null;
            int viewerRank = viewerUuid != null ? plugin.store().rankForUuid(viewerUuid) : -1;
            PlaytimeRow viewerRow = viewerUuid != null
                    ? plugin.store().findPlaytimeRow(viewerUuid).orElse(null)
                    : null;

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(plugin.msg(plugin.rawMsg("playtime-top-header")
                        .replace("{limit}", Integer.toString(limit))));
                if (rows.isEmpty()) {
                    sender.sendMessage(plugin.msg("playtime-top-empty"));
                    return;
                }
                for (int i = 0; i < rows.size(); i++) {
                    PlaytimeRow row = rows.get(i);
                    sender.sendMessage(plugin.colorize(plugin.rawMsg("playtime-top-row")
                            .replace("{rank}", Integer.toString(i + 1))
                            .replace("{player}", row.displayName())
                            .replace("{played}", PlaytimeMilestones.formatDuration(row.totalSeconds()))));
                }
                if (viewerUuid != null && viewerRow != null && viewerRank > 0) {
                    sender.sendMessage(plugin.colorize(plugin.rawMsg("playtime-top-you")
                            .replace("{rank}", Integer.toString(viewerRank))
                            .replace("{played}", PlaytimeMilestones.formatDuration(viewerRow.totalSeconds()))));
                }
            });
        } catch (Exception ex) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(plugin.colorize("&cCould not load playtime leaderboard: &f" + ex.getMessage())));
        }
    }

    private void sendStatus(Player viewer, UUID targetUuid, String targetName, boolean self) {
        try {
            PlaytimeRewardService.PlaytimeStatus status = plugin.playtimeRewards().status(targetUuid);
            sendStatusLines(viewer, targetName, status, self);
        } catch (Exception ex) {
            viewer.sendMessage(plugin.colorize("&cCould not load playtime: &f" + ex.getMessage()));
        }
    }

    private void sendStatusConsole(CommandSender sender, UUID targetUuid, String targetName) {
        try {
            PlaytimeRewardService.PlaytimeStatus status = plugin.playtimeRewards().status(targetUuid);
            sendStatusLines(sender, targetName, status, false);
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cCould not load playtime: &f" + ex.getMessage()));
        }
    }

    private void sendStatusLines(CommandSender sender, String targetName, PlaytimeRewardService.PlaytimeStatus status, boolean self) {
        String statusKey = self ? "playtime-status" : "playtime-other-status";
        sender.sendMessage(plugin.msg(plugin.rawMsg(statusKey)
                .replace("{player}", targetName)
                .replace("{played}", status.playedLabel())
                .replace("{tier_label}", status.tierLabel())));
        if (status.allClaimed()) {
            sender.sendMessage(plugin.msg("playtime-max"));
            return;
        }
        var next = status.next();
        sender.sendMessage(plugin.msg(plugin.rawMsg("playtime-next")
                .replace("{gold}", String.valueOf(next.gold()))
                .replace("{time}", next.label())
                .replace("{remaining}", status.remainingLabel())));
    }

    private UUID resolveUuid(String name) throws Exception {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        var row = plugin.store().findByUsername(name);
        if (row.isPresent()) {
            return row.get().uuid();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        return null;
    }

    private String displayName(UUID uuid, String fallback) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        try {
            return plugin.store().findPlaytimeRow(uuid)
                    .map(PlaytimeRow::displayName)
                    .orElse(fallback);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void runAsync(CommandSender sender, Runnable work) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, work);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("top".startsWith(prefix) && sender.hasPermission("rootrewards.playtime.top")) {
                out.add("top");
            }
            if (sender.hasPermission("rootrewards.playtime.others")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(player.getName());
                    }
                }
            }
            return out;
        }
        return List.of();
    }
}
