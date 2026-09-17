package com.rootrecord.minecraft.rootadmin.listener;

import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Admin/mod staff should not use vanilla OP — Dev/Owner keep OP via Root-Perms {@code *}. */
public final class StaffDeopListener implements Listener {

    private final RootAdminPlugin plugin;

    public StaffDeopListener(RootAdminPlugin plugin) {
        this.plugin = plugin;
    }

    public void deopStaffOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            deopIfStaff(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        deopIfStaff(event.getPlayer());
    }

    private void deopIfStaff(Player player) {
        if (!player.isOp()) {
            return;
        }
        // Dev / owner wildcard OP is intentional.
        if (player.hasPermission("*")
                || player.hasPermission("group.dev")
                || player.hasPermission("group.developer")
                || player.hasPermission("group.owner")) {
            return;
        }
        if (!player.hasPermission("group.admin") && !player.hasPermission("group.moderator")) {
            return;
        }
        player.setOp(false);
        plugin.getLogger().info("Removed vanilla OP from staff account " + player.getName()
                + " (admin/mod — use Root-Perms groups, not ops.json).");
    }
}
