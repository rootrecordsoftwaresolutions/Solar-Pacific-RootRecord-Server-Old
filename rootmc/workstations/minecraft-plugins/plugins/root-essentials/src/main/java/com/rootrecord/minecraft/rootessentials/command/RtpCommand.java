package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.towny.TownyWildernessAccess;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class RtpCommand implements CommandExecutor {

    private final RootEssentialsPlugin plugin;

    public RtpCommand(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!Permissions.has(player, "rtp")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        var grace = plugin.newPlayerGrace();
        if (grace == null || !grace.rtpAllowed(player)) {
            player.sendMessage(plugin.msg("rtp-disabled"));
            return true;
        }
        long cooldownMs = grace.rtpCooldownRemainingMs(player.getUniqueId());
        if (cooldownMs > 0) {
            player.sendMessage(plugin.msg("rtp-cooldown")
                    .replace("{seconds}", String.valueOf(Math.max(1, cooldownMs / 1000L))));
            return true;
        }

        double fee = grace.rtpFeeFor(player);
        if (fee > 0) {
            try {
                double balance = plugin.balance(player.getUniqueId(), player.getName());
                if (balance + 1e-9 < fee) {
                    player.sendMessage(plugin.msg("rtp-insufficient")
                            .replace("{fee}", plugin.money(fee))
                            .replace("{balance}", plugin.money(balance))
                            .replace("{currency}", plugin.currency()));
                    return true;
                }
            } catch (Exception ex) {
                player.sendMessage(plugin.msg("rtp-failed"));
                return true;
            }
        }

        Location target = findWildernessTarget(player);
        if (target == null) {
            player.sendMessage(plugin.msg("rtp-failed"));
            return true;
        }

        final double charge = fee;
        plugin.teleportPlayer(player, target, () -> {
            if (charge > 0) {
                try {
                    if (!plugin.withdraw(player.getUniqueId(), player.getName(), charge)) {
                        grace.markRtpUsed(player.getUniqueId());
                        player.sendMessage(plugin.msg("rtp-insufficient")
                                .replace("{fee}", plugin.money(charge))
                                .replace("{balance}", plugin.money(0))
                                .replace("{currency}", plugin.currency()));
                        return;
                    }
                    plugin.sinkServiceFee(player.getUniqueId(), player.getName(), charge, "rtp");
                } catch (Exception ex) {
                    grace.markRtpUsed(player.getUniqueId());
                    player.sendMessage(plugin.msg("rtp-failed"));
                    return;
                }
            }
            grace.markRtpUsed(player.getUniqueId());
            String body = target.getBlockX() + ", " + target.getBlockZ();
            if (charge > 0) {
                body = body + " · -" + plugin.money(charge) + " " + plugin.currency();
            }
            ChatUi.entry(player, "Rtp", body, "ok");
        });
        return true;
    }

    private Location findWildernessTarget(Player player) {
        var grace = plugin.newPlayerGrace();
        if (grace == null) {
            return null;
        }
        World world = resolveRtpWorld(grace.rtpWorld());
        if (world == null) {
            plugin.getLogger().warning("/rtp destination world missing: " + grace.rtpWorld());
            return null;
        }
        // Anchor on the main world's spawn — never spawn_world hub coords.
        Location anchor = world.getSpawnLocation();
        int min = grace.rtpMinRadius();
        int max = grace.rtpMaxRadius();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < grace.rtpMaxAttempts(); attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            int dist = min + rng.nextInt(Math.max(1, max - min + 1));
            int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            Block highest = world.getHighestBlockAt(x, z);
            if (highest == null || highest.getType().isAir()) {
                continue;
            }
            Material ground = highest.getType();
            if (!ground.isSolid() || ground == Material.LAVA || ground == Material.WATER) {
                continue;
            }
            Location feet = highest.getLocation().add(0.5, 1.0, 0.5);
            Block above = feet.getBlock();
            Block head = feet.clone().add(0, 1, 0).getBlock();
            if (!above.getType().isAir() || !head.getType().isAir()) {
                continue;
            }
            if (!TownyWildernessAccess.isTownyWilderness(feet)) {
                continue;
            }
            return feet;
        }
        return null;
    }

    private static World resolveRtpWorld(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(name.trim());
        if (world != null) {
            return world;
        }
        // Multiverse sometimes registers namespaced aliases.
        return Bukkit.getWorld("minecraft:" + name.trim());
    }
}
