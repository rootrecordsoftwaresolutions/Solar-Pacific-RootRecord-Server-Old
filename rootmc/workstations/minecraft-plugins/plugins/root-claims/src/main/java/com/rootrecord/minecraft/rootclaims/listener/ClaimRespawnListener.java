package com.rootrecord.minecraft.rootclaims.listener;

import com.rootrecord.minecraft.rootclaims.ClaimRecord;
import com.rootrecord.minecraft.rootclaims.ClaimService;
import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class ClaimRespawnListener implements Listener {

    private final RootClaimsPlugin plugin;
    private final ClaimService claims;

    public ClaimRespawnListener(RootClaimsPlugin plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        ClaimRecord claim = claims.ownedSpawnClaim(player);
        Location target = claims.spawnLocation(claim);
        if (target == null) {
            return;
        }
        event.setRespawnLocation(target);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(plugin.msg("claim-respawn-success"));
            }
        }, 1L);
    }
}
