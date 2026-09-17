package com.rootrecord.minecraft.roothelp.command;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** /eating {msg} — roleplay broadcast: * Player is eating {msg} */
public final class EatingCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;

    public EatingCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(plugin.getName().isBlank()
                    ? "Usage: /eating <message>"
                    : "§7Usage: §f/eating <message>");
            return true;
        }
        String msg = String.join(" ", args).trim();
        Bukkit.broadcast(LEGACY.deserialize("&e* " + player.getName() + " is eating " + msg));
        return true;
    }
}
