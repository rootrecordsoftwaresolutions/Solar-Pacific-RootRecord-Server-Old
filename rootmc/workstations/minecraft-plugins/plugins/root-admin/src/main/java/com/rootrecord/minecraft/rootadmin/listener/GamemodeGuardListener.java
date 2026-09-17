package com.rootrecord.minecraft.rootadmin.listener;

import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import com.rootrecord.minecraft.rootadmin.util.GamemodeBypass;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Keeps players out of creative/adventure unless they have the matching gamemode nodes. */
public final class GamemodeGuardListener implements Listener {

    private final RootAdminPlugin plugin;

    public GamemodeGuardListener(RootAdminPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> enforceAllowedGamemode(player, true));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        if (GamemodeBypass.consume(event.getPlayer().getUniqueId())) {
            return;
        }
        GameMode requested = event.getNewGameMode();
        if (AdminPermissions.canUseGamemode(event.getPlayer(), requested)) {
            return;
        }
        // Cancel only — do not call setGameMode here (re-enters the same event).
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.setAllowFlight(false);
        player.setFlying(false);
        player.sendMessage(plugin.msg("gamemode-denied"));
    }

    private void enforceAllowedGamemode(Player player, boolean onJoin) {
        if (!player.isOnline()) {
            return;
        }
        GameMode current = player.getGameMode();
        if (AdminPermissions.canUseGamemode(player, current)) {
            return;
        }
        // Always allow forcing survival; players need no gamemode.* node to be put back.
        GamemodeBypass.allowNextChange(player.getUniqueId());
        try {
            player.setGameMode(GameMode.SURVIVAL);
        } catch (Throwable t) {
            GamemodeBypass.consume(player.getUniqueId());
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            plugin.getLogger().warning(
                    "Gamemode enforce failed for "
                            + player.getName()
                            + ": "
                            + t.getClass().getSimpleName()
                            + " — "
                            + cause);
            return;
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        if (!onJoin) {
            player.sendMessage(plugin.msg("gamemode-denied"));
        }
    }
}
