package com.rootrecord.minecraft.rootmcshops;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShopStore {

    private final File file;
    private final Map<String, ShopListing> byId = new LinkedHashMap<>();

    public ShopStore(JavaPlugin plugin) {
        RootRecordFolders.ensureDir(plugin);
        this.file = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOTMC_SHOPS_LISTINGS);
        migrateLegacyFile(plugin, file);
    }

    private static void migrateLegacyFile(JavaPlugin plugin, File target) {
        if (target.exists()) {
            return;
        }
        File legacy = new File(plugin.getServer().getPluginsFolder(), "RootMC-Shops/shops.yml");
        if (!legacy.isFile()) {
            return;
        }
        try {
            Files.copy(legacy.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Migrated shop listings to plugins/RootMC/shops.yml");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not migrate legacy shops.yml: " + ex.getMessage());
        }
    }

    public Collection<ShopListing> all() {
        return List.copyOf(byId.values());
    }

    public ShopListing getAt(String world, int x, int y, int z) {
        return byId.get(ShopListing.locationKey(world, x, y, z));
    }

    public ShopListing getById(String id) {
        return id == null ? null : byId.get(id);
    }

    public ShopListing getBySign(String world, int x, int y, int z) {
        for (ShopListing shop : byId.values()) {
            if (shop.isSignAt(world, x, y, z)) {
                return shop;
            }
        }
        return null;
    }

    /** Resolves a shop from a sign block, including signs mounted on shop chests. */
    public ShopListing getBySignBlock(Block signBlock) {
        if (signBlock == null) {
            return null;
        }
        String world = signBlock.getWorld().getName();
        ShopListing direct = getBySign(world, signBlock.getX(), signBlock.getY(), signBlock.getZ());
        if (direct != null) {
            return direct;
        }
        Block chest = ShopSigns.chestBlockForSign(signBlock);
        if (chest == null) {
            return null;
        }
        return getAt(world, chest.getX(), chest.getY(), chest.getZ());
    }

    public void put(ShopListing shop) {
        byId.put(shop.id(), shop);
        save();
    }

    public void remove(String id) {
        byId.remove(id);
        save();
    }

    public void load() {
        byId.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("shops");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            var path = "shops." + key + ".";
            int x = yaml.getInt(path + "x");
            int y = yaml.getInt(path + "y");
            int z = yaml.getInt(path + "z");
            byId.put(key, new ShopListing(
                    key,
                    yaml.getString(path + "owner_uuid"),
                    yaml.getString(path + "owner_name"),
                    yaml.getString(path + "world"),
                    x,
                    y,
                    z,
                    yaml.getString(path + "item"),
                    yaml.getDouble(path + "price"),
                    yaml.getString(path + "listing_type", "sell"),
                    yaml.getInt(path + "sale_qty", 1),
                    yaml.getInt(path + "sign_x", x),
                    yaml.getInt(path + "sign_y", y + 1),
                    yaml.getInt(path + "sign_z", z)));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ShopListing shop : byId.values()) {
            String path = "shops." + shop.id() + ".";
            yaml.set(path + "owner_uuid", shop.ownerUuid());
            yaml.set(path + "owner_name", shop.ownerName());
            yaml.set(path + "world", shop.world());
            yaml.set(path + "x", shop.x());
            yaml.set(path + "y", shop.y());
            yaml.set(path + "z", shop.z());
            yaml.set(path + "item", shop.itemKey());
            yaml.set(path + "price", shop.price());
            yaml.set(path + "listing_type", shop.normalizedType());
            yaml.set(path + "sale_qty", shop.saleQty());
            yaml.set(path + "sign_x", shop.signX());
            yaml.set(path + "sign_y", shop.signY());
            yaml.set(path + "sign_z", shop.signZ());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String newId(String world, int x, int y, int z) {
        return ShopListing.locationKey(world, x, y, z);
    }

    public static String itemKeyFromStack(org.bukkit.inventory.ItemStack stack) {
        return ShopItemKeys.fromItemStack(stack);
    }
}
