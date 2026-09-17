package com.rootrecord.minecraft.rootclaims;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Persists per-claim community chests (27 slots - standard single chest). */
public final class ClaimChestStore {

    public static final int CHEST_SIZE = 27;
    private static final String DATA_FILE = "root-claims-chests.yml";

    private final RootClaimsPlugin plugin;
    private final File file;
    private final Map<String, ItemStack[]> contents = new HashMap<>();

    public ClaimChestStore(RootClaimsPlugin plugin) {
        this.plugin = plugin;
        this.file = RootRecordFolders.configFile(plugin, DATA_FILE);
    }

    public void load() {
        contents.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("chests");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(id);
            if (row == null) {
                continue;
            }
            ItemStack[] items = new ItemStack[CHEST_SIZE];
            for (int i = 0; i < CHEST_SIZE; i++) {
                ItemStack stack = row.getItemStack(String.valueOf(i));
                if (stack != null && !stack.getType().isAir()) {
                    items[i] = stack;
                }
            }
            contents.put(id, items);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        for (Map.Entry<String, ItemStack[]> entry : contents.entrySet()) {
            String path = "chests." + entry.getKey();
            ItemStack[] items = entry.getValue();
            for (int i = 0; i < CHEST_SIZE; i++) {
                ItemStack stack = items[i];
                if (stack != null && !stack.getType().isAir()) {
                    yaml.set(path + "." + i, stack);
                }
            }
        }
        try {
            RootRecordFolders.ensureDir(plugin);
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save " + DATA_FILE + ": " + ex.getMessage());
        }
    }

    public void applyTo(Inventory inventory, ClaimKey key) {
        ItemStack[] stored = contents.get(key.storageId());
        inventory.clear();
        if (stored == null) {
            return;
        }
        for (int i = 0; i < Math.min(CHEST_SIZE, inventory.getSize()); i++) {
            if (stored[i] != null) {
                inventory.setItem(i, stored[i].clone());
            }
        }
    }

    public void captureFrom(Inventory inventory, ClaimKey key) {
        ItemStack[] items = new ItemStack[CHEST_SIZE];
        boolean any = false;
        for (int i = 0; i < CHEST_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && !stack.getType().isAir()) {
                items[i] = stack.clone();
                any = true;
            }
        }
        String id = key.storageId();
        if (any) {
            contents.put(id, items);
        } else {
            contents.remove(id);
        }
        save();
    }

    public ItemStack[] takeAll(ClaimKey key) {
        ItemStack[] stored = contents.remove(key.storageId());
        save();
        if (stored == null) {
            return new ItemStack[0];
        }
        return stored;
    }

    /** Live contents for a claim chest (cloned). Empty array if none. */
    public ItemStack[] peek(ClaimKey key) {
        ItemStack[] stored = contents.get(key.storageId());
        if (stored == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[stored.length];
        for (int i = 0; i < stored.length; i++) {
            if (stored[i] != null && !stored[i].getType().isAir()) {
                copy[i] = stored[i].clone();
            }
        }
        return copy;
    }

    public void put(ClaimKey key, ItemStack[] items) {
        if (key == null) {
            return;
        }
        ItemStack[] slots = new ItemStack[CHEST_SIZE];
        boolean any = false;
        if (items != null) {
            for (int i = 0; i < Math.min(CHEST_SIZE, items.length); i++) {
                ItemStack stack = items[i];
                if (stack != null && !stack.getType().isAir()) {
                    slots[i] = stack.clone();
                    any = true;
                }
            }
        }
        String id = key.storageId();
        if (any) {
            contents.put(id, slots);
        } else {
            contents.remove(id);
        }
        save();
    }

    public boolean hasItems(ClaimKey key) {
        ItemStack[] stored = contents.get(key.storageId());
        if (stored == null) {
            return false;
        }
        for (ItemStack stack : stored) {
            if (stack != null && !stack.getType().isAir()) {
                return true;
            }
        }
        return false;
    }
}
