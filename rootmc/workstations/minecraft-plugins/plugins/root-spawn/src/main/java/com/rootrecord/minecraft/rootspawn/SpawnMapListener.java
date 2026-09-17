package com.rootrecord.minecraft.rootspawn;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SpawnMapListener implements Listener {

    private final RootSpawnPlugin plugin;

    public SpawnMapListener(RootSpawnPlugin plugin) {
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
        BuildTarget itemTarget = MapperItem.targetOf(plugin, event.getItem());
        if (itemTarget == null) {
            return;
        }
        BuildTarget mode = plugin.buildTarget(player.getUniqueId());
        if (!player.hasPermission("rootspawn.build") || mode == null || mode != itemTarget) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);

        if (mode == BuildTarget.SPAWN_REFINE) {
            handleSpawnRefine(player, block);
        } else if (mode == BuildTarget.LAVA) {
            handleLavaMap(player, block);
        } else if (mode == BuildTarget.SPAWN_MAP) {
            handleSpawnWaypoint(player, block);
        } else {
            handlePerimeterMap(player, block, mode);
        }
    }

    private void handleLavaMap(Player player, Block block) {
        SpawnConfig cfg = plugin.config();
        try {
            LavaSpotStore.LavaClickResult result = plugin.lavaSpotStore().recordClick(player.getUniqueId(), block);
            if (result.kind() == LavaSpotStore.LavaClickResult.Kind.CEILING) {
                String msg = cfg.buildLavaCeiling()
                        .replace("{x}", Integer.toString(result.x1()))
                        .replace("{y}", Integer.toString(result.y1()))
                        .replace("{z}", Integer.toString(result.z1()));
                player.sendMessage(plugin.colorize(cfg.prefix() + msg));
                player.sendMessage(plugin.colorize(cfg.prefix() + cfg.buildLavaFloorHint()));
            } else {
                String msg = cfg.buildLavaSpotSaved()
                        .replace("{n}", Integer.toString(result.spotIndex()))
                        .replace("{cx}", Integer.toString(result.x1()))
                        .replace("{cy}", Integer.toString(result.y1()))
                        .replace("{cz}", Integer.toString(result.z1()))
                        .replace("{fx}", Integer.toString(result.x2()))
                        .replace("{fy}", Integer.toString(result.y2()))
                        .replace("{fz}", Integer.toString(result.z2()));
                player.sendMessage(plugin.colorize(cfg.prefix() + msg));
                player.sendMessage(plugin.colorize(cfg.prefix() + cfg.buildLavaCeilingHint()));
            }
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cFailed to save lava spot: &f" + ex.getMessage()));
            plugin.getLogger().warning("lava.txt write failed: " + ex.getMessage());
        }
    }

    private void handleSpawnWaypoint(Player player, Block block) {
        SpawnConfig cfg = plugin.config();
        try {
            int index = plugin.waypointStore().appendWaypoint(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getZ(),
                    block.getY());
            String msg = "&aSpawn point &f#{n}&a: &f{x}, {z}"
                    .replace("{n}", Integer.toString(index))
                    .replace("{x}", Integer.toString(block.getX()))
                    .replace("{z}", Integer.toString(block.getZ()));
            player.sendMessage(plugin.colorize(cfg.prefix() + msg));
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cFailed to save waypoint: &f" + ex.getMessage()));
            plugin.getLogger().warning("spawnarea.txt write failed: " + ex.getMessage());
        }
    }

    private void handlePerimeterMap(Player player, Block block, BuildTarget target) {
        SpawnConfig cfg = plugin.config();
        PolygonAreaStore store = target == BuildTarget.CHAMBER ? plugin.chamberStore() : plugin.wellStore();
        try {
            int index = store.appendWaypoint(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getZ(),
                    block.getY());
            if (target == BuildTarget.CHAMBER) {
                plugin.reloadChamberBoundary();
            } else {
                plugin.reloadWellBoundary();
            }
            String msg = (target == BuildTarget.CHAMBER ? cfg.buildChamberPoint() : cfg.buildWellPoint())
                    .replace("{n}", Integer.toString(index))
                    .replace("{x}", Integer.toString(block.getX()))
                    .replace("{z}", Integer.toString(block.getZ()));
            player.sendMessage(plugin.colorize(cfg.prefix() + msg));
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cFailed to save waypoint: &f" + ex.getMessage()));
            plugin.getLogger().warning(target.label() + " write failed: " + ex.getMessage());
        }
    }

    private void handleSpawnRefine(Player player, Block block) {
        SpawnBoundary boundary = plugin.boundary();
        if (boundary == null || boundary.isEmpty()) {
            player.sendMessage(plugin.colorize("&cNo boundary loaded."));
            return;
        }
        if (!boundary.worldName().equals(block.getWorld().getName())) {
            player.sendMessage(plugin.colorize("&cWrong world — boundary is in &f" + boundary.worldName()));
            return;
        }

        SpawnConfig cfg = plugin.config();
        int x = block.getX();
        int z = block.getZ();
        boolean inside = boundary.contains(block.getWorld(), x + 0.5, z + 0.5);
        int moved;
        String detail;
        if (inside) {
            moved = boundary.pullInward(x, z);
            detail = cfg.buildPull()
                    .replace("{x}", Integer.toString(x))
                    .replace("{z}", Integer.toString(z));
        } else if (boundary.isNearOutside(block.getWorld(), x + 0.5, z + 0.5, cfg.buildExpandMaxDist())) {
            moved = boundary.pushOutward(x, z);
            detail = cfg.buildPush()
                    .replace("{x}", Integer.toString(x))
                    .replace("{z}", Integer.toString(z));
        } else {
            player.sendMessage(plugin.colorize(cfg.prefix() + cfg.buildHint()));
            return;
        }

        if (moved == 0) {
            player.sendMessage(plugin.colorize("&7No border change at that spot — try a different block."));
            return;
        }

        try {
            plugin.saveSpawnBoundary(player.getName());
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cFailed to save boundary: &f" + ex.getMessage()));
            plugin.getLogger().warning("spawnarea-refined write failed: " + ex.getMessage());
            return;
        }

        player.sendMessage(plugin.colorize(cfg.prefix() + detail));
        player.sendMessage(plugin.colorize(cfg.prefix() + cfg.buildSaved()
                + " &7(" + moved + " point" + (moved == 1 ? "" : "s") + ")"));
    }
}
