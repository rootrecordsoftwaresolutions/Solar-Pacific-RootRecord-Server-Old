package com.rootrecord.minecraft.rootavacore.autonomy;

import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Players can gift Ava materials; Ava_Ivy picks up freely in her claim.
 */
public final class AvaGiftListener implements Listener {

    private final RootAvaCorePlugin plugin;

    public AvaGiftListener(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropNearAva(PlayerDropItemEvent event) {
        // Soft hint only — vacuum tick will collect; no force-steal on drop
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAvaPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.powers().isAva(player)) return;
        // Ava always allowed to pick up; god kit keeps tools
    }

    /** Called from /ava gift */
    public String giftHeld(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return "empty_hand";
        }
        ItemStack gift = hand.clone();
        String err = plugin.autonomy().acceptGift(player, gift);
        if (err == null) {
            player.getInventory().setItemInMainHand(null);
            return null;
        }
        if (err.startsWith("partial:")) {
            int left = Integer.parseInt(err.substring("partial:".length()));
            hand.setAmount(left);
            player.getInventory().setItemInMainHand(hand);
            return "partial";
        }
        return err;
    }
}
