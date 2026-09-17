package com.rootrecord.minecraft.rootmapper;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class MapperListener implements Listener {

    private final RootMapperPlugin plugin;

    public MapperListener(RootMapperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        MapperSession itemSession = MapperItem.sessionOf(plugin, event.getItem());
        if (itemSession == null) {
            return;
        }
        MapperSession active = plugin.session(player.getUniqueId());
        if (!player.hasPermission("rootmapper.admin") || active == null || !active.equals(itemSession)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);

        switch (active.mode()) {
            case WAYPOINT -> handleWaypoint(player, block, active.areaId());
            case REFINE -> handleRefine(player, block, active.areaId());
            case LAVA -> handleLava(player, block, active.areaId());
        }
    }

    private void handleWaypoint(Player player, Block block, String areaId) {
        MapperConfig cfg = plugin.registry().config();
        PolygonAreaStore store = plugin.registry().polygonStore(areaId);
        if (store == null) {
            return;
        }
        try {
            int index = store.appendWaypoint(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getZ(),
                    block.getY());
            plugin.registry().reloadBoundary(areaId);
            player.sendMessage(plugin.colorize(cfg.prefix() + cfg.waypointSaved()
                    .replace("{n}", Integer.toString(index))
                    .replace("{x}", Integer.toString(block.getX()))
                    .replace("{z}", Integer.toString(block.getZ()))));
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cFailed to save waypoint: &f" + ex.getMessage()));
            plugin.getLogger().warning("Mapper waypoint write failed: " + ex.getMessage());
        }
    }

    private void handleRefine(Player player, Block block, String areaId) {
        PolygonBoundary boundary = plugin.registry().boundary(areaId);
        MapperConfig cfg = plugin.registry().config();
        if (boundary == null || boundary.isEmpty()) {
            player.sendMessage(plugin.colorize("&cNo boundary loaded for &f" + areaId));
            return;
        }
        if (!boundary.worldName().equals(block.getWorld().getName())) {
            player.sendMessage(plugin.colorize("&cWrong world — boundary is in &f" + boundary.worldName()));
            return;
        }

        int x = block.getX();
        int z = block.getZ();
        boolean inside = boundary.contains(block.getWorld(), x + 0.5, z + 0.5);
        int moved;
        String detail;
        if (inside) {
            moved = boundary.pullInward(x, z);
            detail = cfg.refinePull()
                    .replace("{x}", Integer.toString(x))
                    .replace("{z}", Integer.toString(z));
        } else if (boundary.isNearOutside(block.getWorld(), x + 0.5, z + 0.5, cfg.expandMaxDist())) {
            moved = boundary.pushOutward(x, z);
            detail = cfg.refinePush()
                    .replace("{x}", Integer.toString(x))
                    .replace("{z}", Integer.toString(z));
        } else {
            player.sendMessage(plugin.colorize(cfg.prefix() + cfg.refineHint()
                    .replace("{dist}", Integer.toString(cfg.expandMaxDist()))));
            return;
        }

        if (moved == 0) {
            player.sendMessage(plugin.colorize("&7No border change at that spot — try a different block."));
            return;
        }

        PolygonAreaStore store = plugin.registry().polygonStore(areaId);
        try {
            store.saveBoundary(boundary, player.getName());
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cFailed to save boundary: &f" + ex.getMessage()));
            plugin.getLogger().warning("Mapper boundary write failed: " + ex.getMessage());
            return;
        }

        player.sendMessage(plugin.colorize(cfg.prefix() + detail));
        player.sendMessage(plugin.colorize(cfg.prefix() + cfg.refineSaved()
                + " &7(" + moved + " point" + (moved == 1 ? "" : "s") + ")"));
    }

    private void handleLava(Player player, Block block, String areaId) {
        MapperConfig cfg = plugin.registry().config();
        LavaAreaStore store = plugin.registry().lavaStore(areaId);
        if (store == null) {
            return;
        }
        try {
            LavaAreaStore.LavaClickResult result = store.recordClick(player.getUniqueId(), block);
            if (result.kind() == LavaAreaStore.LavaClickResult.Kind.CEILING) {
                player.sendMessage(plugin.colorize(cfg.prefix() + cfg.lavaCeiling()
                        .replace("{x}", Integer.toString(result.x1()))
                        .replace("{y}", Integer.toString(result.y1()))
                        .replace("{z}", Integer.toString(result.z1()))));
                player.sendMessage(plugin.colorize(cfg.prefix() + cfg.lavaFloorHint()));
            } else {
                player.sendMessage(plugin.colorize(cfg.prefix() + cfg.lavaSpotSaved()
                        .replace("{n}", Integer.toString(result.spotIndex()))
                        .replace("{cx}", Integer.toString(result.x1()))
                        .replace("{cy}", Integer.toString(result.y1()))
                        .replace("{cz}", Integer.toString(result.z1()))
                        .replace("{fx}", Integer.toString(result.x2()))
                        .replace("{fy}", Integer.toString(result.y2()))
                        .replace("{fz}", Integer.toString(result.z2()))));
                player.sendMessage(plugin.colorize(cfg.prefix() + cfg.lavaCeilingHint()));
            }
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cFailed to save lava spot: &f" + ex.getMessage()));
            plugin.getLogger().warning("Mapper lava write failed: " + ex.getMessage());
        }
    }
}
