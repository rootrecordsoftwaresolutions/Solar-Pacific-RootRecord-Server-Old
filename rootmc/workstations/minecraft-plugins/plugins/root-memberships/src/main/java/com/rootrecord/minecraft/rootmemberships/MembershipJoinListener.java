package com.rootrecord.minecraft.rootmemberships;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class MembershipJoinListener implements Listener {

    private final RootMembershipsPlugin plugin;

    public MembershipJoinListener(RootMembershipsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MembershipsConfig cfg = plugin.config();
        if (!cfg.enabled() || !cfg.syncOnJoin()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.syncPlayer(player);
        });
    }
}
