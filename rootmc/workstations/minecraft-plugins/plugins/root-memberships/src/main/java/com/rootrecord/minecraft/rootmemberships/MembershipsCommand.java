package com.rootrecord.minecraft.rootmemberships;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MembershipsCommand implements CommandExecutor, TabCompleter {

    private final RootMembershipsPlugin plugin;

    public MembershipsCommand(RootMembershipsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootmemberships.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage("§aRoot-Memberships reloaded.");
            }
            case "status" -> {
                MembershipsConfig cfg = plugin.config();
                sender.sendMessage("§8[§bMemberships§8] §7enabled=" + cfg.enabled()
                        + " sync-on-join=" + cfg.syncOnJoin()
                        + " interval=" + cfg.syncIntervalSeconds() + "s");
                for (MembershipsConfig.MembershipRule rule : cfg.rules()) {
                    sender.sendMessage("§7- §f" + rule.id() + "§7 group=§f" + rule.group()
                            + " §7enabled=" + rule.enabled()
                            + " cloud-pro=" + rule.requireCloudPro()
                            + " top-active=" + rule.requireTopActive()
                            + " life=" + rule.requireLife()
                            + " life-as-pro=" + rule.lifeCountsAsPro());
                }
                if (sender instanceof Player player) {
                    CloudMembershipFlags.Flags flags = CloudMembershipFlags.fetch(player);
                    sender.sendMessage("§7Your cloud: linked=" + flags.linked()
                            + " pro=" + flags.proUnlocked()
                            + " life=" + flags.lifeMember()
                            + " topActive=" + flags.topActivePlayer());
                    plugin.syncPlayer(player);
                    sender.sendMessage("§aSynced your membership groups.");
                }
            }
            case "sync" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cPlayers only (or use /memberships status).");
                    return true;
                }
                plugin.syncPlayer(player);
                sender.sendMessage("§aMembership sync complete.");
            }
            default -> sender.sendMessage("§e/memberships [status|reload|sync]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("rootmemberships.admin")) {
            return List.of();
        }
        String p = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : List.of("status", "reload", "sync")) {
            if (s.startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }
}
