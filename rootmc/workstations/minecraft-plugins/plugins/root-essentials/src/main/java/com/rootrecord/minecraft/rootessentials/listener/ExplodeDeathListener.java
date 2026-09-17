package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.rootessentials.command.ExplodeCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Keep inventory on /explode confirm deaths (TNT already consumed; wallet already seized). */
public final class ExplodeDeathListener implements Listener {

    private final ExplodeCommand explodeCommand;

    public ExplodeDeathListener(ExplodeCommand explodeCommand) {
        this.explodeCommand = explodeCommand;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!explodeCommand.shouldKeepInventory(event.getEntity().getUniqueId())) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }
}
