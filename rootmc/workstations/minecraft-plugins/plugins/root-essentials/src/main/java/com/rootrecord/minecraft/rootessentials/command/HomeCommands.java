package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class HomeCommands implements CommandExecutor {

    private final RootEssentialsPlugin plugin;
    private final String mode;

    public HomeCommands(RootEssentialsPlugin plugin, String mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        String perm = switch (mode) {
            case "set" -> "sethome";
            case "del" -> "delhome";
            default -> "home";
        };
        if (!Permissions.has(player, perm)
                && !(("set".equals(mode) || "del".equals(mode)) && Permissions.has(player, "home"))) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        String name = args.length > 0 ? args[0] : plugin.defaultHomeName();
        try {
            return switch (mode) {
                case "set" -> setHome(player, name);
                case "go" -> goHome(player, name);
                case "del" -> delHome(player, name);
                default -> true;
            };
        } catch (Exception ex) {
            ChatUi.entry(player, "Home", "error · try /sethome again", "alert");
            return true;
        }
    }

    private boolean setHome(Player player, String name) throws Exception {
        int max = plugin.maxHomes(player);
        int current = plugin.homeCount(player.getUniqueId());
        if (current >= max && !plugin.hasHome(player.getUniqueId(), name)) {
            ChatUi.entry(player, "Home", "limit " + max + " · /delhome a spare", "alert");
            return true;
        }
        plugin.setHome(player.getUniqueId(), name, player.getLocation());
        ChatUi.entry(player, "Home", "saved " + name, "ok");
        return true;
    }

    private boolean goHome(Player player, String name) throws Exception {
        var loc = plugin.getHome(player.getUniqueId(), name);
        if (loc == null) {
            ChatUi.entry(player, "Home", "none set · /sethome", "alert");
            return true;
        }
        if (!plugin.teleportPlayer(player, loc, () -> ChatUi.entry(player, "Home", name, "ok"))) {
            ChatUi.entry(player, "Home", "cancelled · stand still", "alert");
        }
        return true;
    }

    private boolean delHome(Player player, String name) throws Exception {
        boolean ok = plugin.deleteHome(player.getUniqueId(), name);
        if (!ok) {
            ChatUi.entry(player, "Home", "none named " + name + " · /homes", "alert");
            return true;
        }
        ChatUi.entry(player, "Home", "deleted " + name, "ok");
        return true;
    }
}
