package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SpawnCommand implements CommandExecutor, TabCompleter {

    private final RootEssentialsPlugin plugin;

    public SpawnCommand(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!Permissions.has(player, "spawn")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length >= 1 && tryAreaSpawn(player, args)) {
            return true;
        }
        var spawn = plugin.spawnLocation(player);
        if (spawn == null) {
            ChatUi.entry(player, "Spawn", "unset · ask staff", "alert");
            return true;
        }
        plugin.teleportPlayer(player, spawn, () -> ChatUi.entry(player, "Spawn", "teleported", "ok"));
        return true;
    }

    private boolean tryAreaSpawn(Player player, String[] args) {
        Plugin claimsPlugin = Bukkit.getPluginManager().getPlugin("Root-Claims");
        if (claimsPlugin == null || !claimsPlugin.isEnabled()) {
            return false;
        }
        try {
            Method method = claimsPlugin.getClass().getMethod(
                    "tryTeleportToAreaSpawn", Player.class, String.class, String.class);
            Object result = method.invoke(
                    claimsPlugin,
                    player,
                    args[0],
                    args.length >= 2 ? args[1] : null);
            return result instanceof Boolean ok && ok;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Area spawn bridge failed: " + ex.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        Plugin claimsPlugin = Bukkit.getPluginManager().getPlugin("Root-Claims");
        if (claimsPlugin != null && claimsPlugin.isEnabled() && args.length >= 1 && args.length <= 2) {
            try {
                Method method = claimsPlugin.getClass().getMethod(
                        "tabCompleteAreaSpawn", Player.class, String[].class);
                Object result = method.invoke(claimsPlugin, player, args);
                if (result instanceof List<?> list) {
                    return (List<String>) list;
                }
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().fine("Area spawn tab-complete bridge failed: " + ex.getMessage());
            }
        }
        if (args.length == 1) {
            String needle = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(needle)) {
                    out.add(online.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
