package com.rootrecord.minecraft.rootcore.command;

import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import com.rootrecord.minecraft.rootcore.transfer.MeshPeer;
import com.rootrecord.minecraft.rootcore.transfer.TransferMeshService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class GotoCommand implements CommandExecutor, TabCompleter {

    private final RootCorePlugin plugin;

    public GotoCommand(RootCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rootcore.goto")) {
            player.sendMessage(color("&cNo permission."));
            return true;
        }
        TransferMeshService mesh = plugin.transferMesh();
        if (mesh == null) {
            player.sendMessage(color("&cTransfer mesh is unavailable."));
            return true;
        }
        String want = args.length >= 1 ? args[0] : destinationFromAlias(label);
        if (want == null || want.isBlank()) {
            List<MeshPeer> peers = mesh.peers();
            if (peers.isEmpty()) {
                player.sendMessage(color("&eNo transfer peers loaded yet. Try again in a moment."));
                mesh.refreshAsync();
                return true;
            }
            player.sendMessage(color("&6/goto &f<server>"));
            player.sendMessage(color("&7Available: &f"
                    + peers.stream().map(MeshPeer::slug).collect(Collectors.joining("&7, &f"))));
            player.sendMessage(color("&7Shortcuts: &f/toclaims &7· &f/toava &7· &f/totest"));
            return true;
        }
        MeshPeer dest = mesh.find(want);
        if (dest == null && mesh.stale(90_000L)) {
            player.sendMessage(color("&7Refreshing server list…"));
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                mesh.refreshNowBlocking();
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    MeshPeer refreshed = mesh.find(want);
                    if (refreshed == null) {
                        player.sendMessage(color("&cUnknown server &f" + want + "&c. Use &f/goto &cfor the list."));
                        return;
                    }
                    mesh.transfer(player, refreshed, true);
                });
            });
            return true;
        }
        if (dest == null) {
            player.sendMessage(color("&cUnknown server &f" + want + "&c. Use &f/goto &cfor the list."));
            return true;
        }
        mesh.transfer(player, dest, true);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        TransferMeshService mesh = plugin.transferMesh();
        if (mesh == null || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (MeshPeer peer : mesh.peers()) {
            if (peer.slug().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(peer.slug());
            }
            if (peer.aliases() != null) {
                for (String a : peer.aliases()) {
                    if (a != null && a.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(a);
                    }
                }
            }
        }
        return out;
    }

    private static String destinationFromAlias(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return switch (label.trim().toLowerCase(Locale.ROOT)) {
            case "toclaims", "gen2", "g2", "toplay" -> "claims";
            case "totowny", "gen1", "g1" -> "towny";
            case "totest", "todev", "toava" -> "test";
            default -> null;
        };
    }

    private static String color(String s) {
        return s == null ? "" : s.replace('&', '\u00A7');
    }
}
