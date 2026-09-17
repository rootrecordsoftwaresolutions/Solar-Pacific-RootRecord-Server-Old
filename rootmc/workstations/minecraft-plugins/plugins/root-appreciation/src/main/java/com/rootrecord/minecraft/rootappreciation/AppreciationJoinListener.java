package com.rootrecord.minecraft.rootappreciation;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Join: backfill vote tokens + deliver pending grants; MOTD when /bonus is available. */
public final class AppreciationJoinListener implements Listener {

    private final RootAppreciationPlugin plugin;

    public AppreciationJoinListener(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.config().enabled()) {
            return;
        }
        Player player = event.getPlayer();
        long delay = Math.max(1L, plugin.config().motdDelayTicks());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.service().backfillVotesAndDeliver(player);
            plugin.tokens().refreshInventoryLore(player, plugin.config());
            if (plugin.voteShardService() != null) {
                plugin.voteShardService().ensureExactOwed(player);
                plugin.voteShardService().matchAvaToHumans();
                plugin.voteShardService().syncCloudWeights(java.util.List.of(player.getUniqueId()));
            }
            if (plugin.config().motdRemind() && plugin.service().bonusAvailable(player.getUniqueId())) {
                player.sendMessage(plugin.msg("motd-bonus"));
            }
        }, delay);
    }
}
