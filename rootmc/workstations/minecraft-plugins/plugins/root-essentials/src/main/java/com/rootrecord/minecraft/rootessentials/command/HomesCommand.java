package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class HomesCommand implements CommandExecutor {

    private final RootEssentialsPlugin plugin;

    public HomesCommand(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!Permissions.has(player, "homes")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        try {
            var names = plugin.listHomeNames(player.getUniqueId());
            int max = plugin.maxHomes(player);
            if (names.isEmpty()) {
                player.sendMessage(plugin.colorize("&7You have no homes (&f0&7/&f" + max + "&7)."));
                return true;
            }
            player.sendMessage(plugin.colorize("&aHomes (&f" + names.size() + "&a/&f" + max + "&a): &f" + String.join(", ", names)));
            return true;
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cCould not list homes: &f" + ex.getMessage()));
            return true;
        }
    }
}
