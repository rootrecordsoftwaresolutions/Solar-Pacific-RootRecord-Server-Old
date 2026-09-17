package com.rootrecord.minecraft.rootavacore.hire;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Per-player hire drop chest + speed upgrades (1000 G / level).
 */
public final class AvaChestService {

    public static final double SPEED_COST_G = 1000.0;

    private final RootAvaCorePlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public AvaChestService(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ava-chests.yml");
        reload();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean hasChest(UUID playerId) {
        return chestLocation(playerId) != null;
    }

    public Location chestLocation(UUID playerId) {
        if (playerId == null || yaml == null) {
            return null;
        }
        ConfigurationSection sec = yaml.getConfigurationSection("players." + playerId);
        if (sec == null) {
            return null;
        }
        String worldName = sec.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        Location loc = new Location(world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
        Block block = loc.getBlock();
        if (!isChest(block)) {
            return null;
        }
        return loc;
    }

    public int getSpeedLevel(UUID playerId) {
        if (playerId == null || yaml == null) {
            return 0;
        }
        return Math.max(0, yaml.getInt("players." + playerId + ".speed", 0));
    }

    public String setChestFromLook(Player player) {
        if (player == null) {
            return "players_only";
        }
        Block chest = findNearbyChest(player);
        if (chest == null) {
            return "no_chest";
        }
        yaml.set("players." + player.getUniqueId() + ".world", chest.getWorld().getName());
        yaml.set("players." + player.getUniqueId() + ".x", chest.getX());
        yaml.set("players." + player.getUniqueId() + ".y", chest.getY());
        yaml.set("players." + player.getUniqueId() + ".z", chest.getZ());
        save();
        return null;
    }

    public String buySpeed(Player player) {
        if (player == null) {
            return "players_only";
        }
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null) {
            return "no_economy";
        }
        if (!eco.has(player.getUniqueId(), SPEED_COST_G) || !eco.withdraw(player.getUniqueId(), SPEED_COST_G)) {
            return "broke";
        }
        int next = getSpeedLevel(player.getUniqueId()) + 1;
        yaml.set("players." + player.getUniqueId() + ".speed", next);
        save();
        return "ok:" + next;
    }

    public void depositOrDrop(UUID playerId, Collection<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        Location loc = chestLocation(playerId);
        if (loc == null) {
            return;
        }
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        Inventory inv = chest.getBlockInventory();
        Location dropAt = dropInFront(block);
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            var overflow = inv.addItem(stack);
            if (overflow.isEmpty()) {
                continue;
            }
            for (ItemStack left : overflow.values()) {
                if (left != null && left.getAmount() > 0 && dropAt.getWorld() != null) {
                    dropAt.getWorld().dropItemNaturally(dropAt, left);
                }
            }
        }
        chest.update(true, false);
    }

    private static Location dropInFront(Block chest) {
        Location at = chest.getLocation().add(0.5, 0.2, 0.5);
        if (chest.getBlockData() instanceof Directional dir) {
            BlockFace face = dir.getFacing();
            at.add(face.getModX(), 0, face.getModZ());
        } else {
            at.add(0, 0, 1);
        }
        return at;
    }

    private static Block findNearbyChest(Player player) {
        RayTraceResult hit = player.rayTraceBlocks(6);
        if (hit != null && isChest(hit.getHitBlock())) {
            return hit.getHitBlock();
        }
        Block target = player.getTargetBlockExact(6);
        if (isChest(target)) {
            return target;
        }
        Location feet = player.getLocation();
        World world = feet.getWorld();
        if (world == null) {
            return null;
        }
        int x = feet.getBlockX();
        int y = feet.getBlockY();
        int z = feet.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (isChest(b)) {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isChest(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "ava-chests.yml save failed: " + ex.getMessage());
        }
    }
}
