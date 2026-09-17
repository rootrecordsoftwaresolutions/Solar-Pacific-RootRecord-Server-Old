package com.rootrecord.minecraft.rootessentials.service;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Places an invisible {@link Material#LIGHT} block near players who hold a torch
 * (or other configured light items) so they can see without placing the block.
 */
public final class HeldLightService {

    private final RootEssentialsPlugin plugin;
    private final Map<UUID, PlacedLight> lights = new HashMap<>();
    private final Map<Material, Integer> lightItems = new HashMap<>();
    private boolean enabled = true;
    private int defaultLevel = 14;
    private int tickPeriod = 2;
    private BukkitTask task;

    public HeldLightService(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration cfg) {
        stop();
        ConfigurationSection section = cfg != null ? cfg.getConfigurationSection("held-light") : null;
        enabled = section == null || section.getBoolean("enabled", true);
        defaultLevel = clampLevel(section != null ? section.getInt("level", 14) : 14);
        tickPeriod = Math.max(1, section != null ? section.getInt("tick-period", 2) : 2);
        lightItems.clear();
        if (section != null && section.isConfigurationSection("items")) {
            ConfigurationSection items = section.getConfigurationSection("items");
            for (String key : items.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null || !mat.isItem()) {
                    plugin.getLogger().warning("held-light: unknown item " + key);
                    continue;
                }
                lightItems.put(mat, clampLevel(items.getInt(key, defaultLevel)));
            }
        }
        if (lightItems.isEmpty()) {
            lightItems.put(Material.TORCH, 14);
            lightItems.put(Material.SOUL_TORCH, 10);
            lightItems.put(Material.REDSTONE_TORCH, 7);
            lightItems.put(Material.LANTERN, 15);
            lightItems.put(Material.SOUL_LANTERN, 10);
            lightItems.put(Material.CAMPFIRE, 15);
            lightItems.put(Material.SOUL_CAMPFIRE, 10);
            lightItems.put(Material.JACK_O_LANTERN, 15);
            lightItems.put(Material.SHROOMLIGHT, 15);
            lightItems.put(Material.GLOWSTONE, 15);
            lightItems.put(Material.SEA_LANTERN, 15);
            lightItems.put(Material.OCHRE_FROGLIGHT, 15);
            lightItems.put(Material.VERDANT_FROGLIGHT, 15);
            lightItems.put(Material.PEARLESCENT_FROGLIGHT, 15);
        }
        if (enabled) {
            start();
        }
    }

    public void start() {
        if (task != null || !enabled) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, tickPeriod, tickPeriod);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAll();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
        // Drop lights for players who left
        for (UUID id : Map.copyOf(lights).keySet()) {
            if (Bukkit.getPlayer(id) == null) {
                clear(id);
            }
        }
    }

    private void updatePlayer(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            clear(player.getUniqueId());
            return;
        }
        int level = heldLightLevel(player);
        if (level <= 0) {
            clear(player.getUniqueId());
            return;
        }
        Block spot = findLightSpot(player);
        if (spot == null) {
            clear(player.getUniqueId());
            return;
        }
        PlacedLight current = lights.get(player.getUniqueId());
        if (current != null
                && current.world == spot.getWorld()
                && current.x == spot.getX()
                && current.y == spot.getY()
                && current.z == spot.getZ()
                && spot.getType() == Material.LIGHT
                && current.level == level) {
            return;
        }
        clear(player.getUniqueId());
        if (!isReplaceableForLight(spot.getType())) {
            return;
        }
        Light data = (Light) Material.LIGHT.createBlockData();
        data.setLevel(level);
        spot.setBlockData(data, false);
        lights.put(player.getUniqueId(), new PlacedLight(spot.getWorld(), spot.getX(), spot.getY(), spot.getZ(), level));
    }

    private int heldLightLevel(Player player) {
        int main = levelFor(player.getInventory().getItemInMainHand());
        int off = levelFor(player.getInventory().getItemInOffHand());
        return Math.max(main, off);
    }

    private int levelFor(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        Integer level = lightItems.get(stack.getType());
        return level == null ? 0 : level;
    }

    private void clear(UUID id) {
        PlacedLight placed = lights.remove(id);
        if (placed == null || placed.world == null) {
            return;
        }
        Block block = placed.world.getBlockAt(placed.x, placed.y, placed.z);
        if (block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
    }

    private void clearAll() {
        for (UUID id : Map.copyOf(lights).keySet()) {
            clear(id);
        }
        lights.clear();
    }

    private static Block findLightSpot(Player player) {
        Location eye = player.getEyeLocation();
        Block atEyes = eye.getBlock();
        if (isReplaceableForLight(atEyes.getType())) {
            return atEyes;
        }
        Block atFeet = player.getLocation().getBlock();
        if (isReplaceableForLight(atFeet.getType())) {
            return atFeet;
        }
        Block aboveFeet = atFeet.getRelative(0, 1, 0);
        if (isReplaceableForLight(aboveFeet.getType())) {
            return aboveFeet;
        }
        return null;
    }

    private static boolean isReplaceableForLight(Material type) {
        return type == Material.AIR
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR
                || type == Material.LIGHT;
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(15, level));
    }

    private record PlacedLight(World world, int x, int y, int z, int level) {}
}
