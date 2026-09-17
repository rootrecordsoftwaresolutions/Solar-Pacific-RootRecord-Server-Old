package com.rootrecord.minecraft.rootclaims;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimStore {

    private static final String DATA_FILE = "root-claims-data.yml";

    private final RootClaimsPlugin plugin;
    private final File file;
    private final Map<ClaimKey, ClaimRecord> claims = new LinkedHashMap<>();

    public ClaimStore(RootClaimsPlugin plugin) {
        this.plugin = plugin;
        this.file = RootRecordFolders.configFile(plugin, DATA_FILE);
    }

    public void load() {
        claims.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(id);
            if (row == null) {
                continue;
            }
            String world = row.getString("world", "");
            int x = row.getInt("x");
            int y = row.getInt("y", row.getInt("anchor-y", 64));
            int z = row.getInt("z");
            int radius = Math.max(1, row.getInt("radius-blocks", 16));
            String ownerRaw = row.getString("owner", "");
            if (world.isBlank() || ownerRaw.isBlank()) {
                continue;
            }
            try {
                UUID owner = UUID.fromString(ownerRaw);
                ClaimKey parentKey = parseKey(row.getString("parent-key"));
                ClaimRecord claim = new ClaimRecord(
                        new ClaimKey(world, x, y, z),
                        owner,
                        row.getString("owner-name", ownerRaw),
                        row.getLong("created-at", System.currentTimeMillis()),
                        row.getDouble("paid-gold", 0),
                        radius,
                        parentKey,
                        row.getBoolean("settings.mobs", false),
                        row.getBoolean("settings.public-spawn", false));
                String areaName = row.getString("display-name", row.getString("area-name", ""));
                if (areaName != null && !areaName.isBlank()) {
                    claim.displayName(areaName);
                }
                ConfigurationSection spawn = row.getConfigurationSection("spawn");
                if (spawn != null && spawn.contains("x") && spawn.contains("y") && spawn.contains("z")) {
                    claim.setSpawn(
                            spawn.getDouble("x"),
                            spawn.getDouble("y"),
                            spawn.getDouble("z"),
                            (float) spawn.getDouble("yaw", 0),
                            (float) spawn.getDouble("pitch", 0));
                }
                ConfigurationSection trusted = row.getConfigurationSection("trusted");
                if (trusted != null) {
                    for (String uuidRaw : trusted.getKeys(false)) {
                        claim.trust(UUID.fromString(uuidRaw), trusted.getString(uuidRaw, uuidRaw));
                    }
                }
                claims.put(claim.key(), claim);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed claim row " + id + ": " + ex.getMessage());
            }
        }
        migrateAreaNamesIfNeeded();
    }

    /** Assign username(1..n) to roots missing a display name (creation order). */
    private void migrateAreaNamesIfNeeded() {
        Map<UUID, List<ClaimRecord>> rootsByOwner = new LinkedHashMap<>();
        for (ClaimRecord claim : claims.values()) {
            if (!claim.isAreaRoot()) {
                continue;
            }
            rootsByOwner.computeIfAbsent(claim.ownerId(), ignored -> new ArrayList<>()).add(claim);
        }
        boolean changed = false;
        for (Map.Entry<UUID, List<ClaimRecord>> entry : rootsByOwner.entrySet()) {
            List<ClaimRecord> roots = entry.getValue();
            roots.sort(Comparator.comparingLong(ClaimRecord::createdAtMillis)
                    .thenComparing(c -> c.key().storageId()));
            int slot = 1;
            for (ClaimRecord root : roots) {
                if (root.displayName() == null || root.displayName().isBlank()) {
                    String base = root.ownerName() == null || root.ownerName().isBlank()
                            ? "Area"
                            : root.ownerName();
                    root.displayName(base + "(" + slot + ")");
                    changed = true;
                }
                slot++;
            }
        }
        if (changed) {
            save();
            plugin.getLogger().info("Migrated area display names for root claims.");
        }
    }

    public void save() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("schema-version", 1);
        yaml.set("claims", null);
        for (ClaimRecord claim : claims.values()) {
            String path = "claims." + idFor(claim.key());
            yaml.set(path + ".world", claim.key().world());
            yaml.set(path + ".x", claim.key().x());
            yaml.set(path + ".y", claim.key().y());
            yaml.set(path + ".z", claim.key().z());
            yaml.set(path + ".radius-blocks", claim.radiusBlocks());
            yaml.set(path + ".parent-key", claim.parentKey() == null ? "" : claim.parentKey().storageId());
            yaml.set(path + ".owner", claim.ownerId().toString());
            yaml.set(path + ".owner-name", claim.ownerName());
            if (claim.displayName() != null && !claim.displayName().isBlank()) {
                yaml.set(path + ".display-name", claim.displayName());
            }
            yaml.set(path + ".created-at", claim.createdAtMillis());
            yaml.set(path + ".paid-gold", claim.paidGold());
            yaml.set(path + ".settings.mobs", claim.mobsAllowed());
            yaml.set(path + ".settings.public-spawn", claim.spawnPublic());
            if (claim.hasCustomSpawn()) {
                yaml.set(path + ".spawn.x", claim.spawnX());
                yaml.set(path + ".spawn.y", claim.spawnY());
                yaml.set(path + ".spawn.z", claim.spawnZ());
                yaml.set(path + ".spawn.yaw", claim.spawnYaw());
                yaml.set(path + ".spawn.pitch", claim.spawnPitch());
            }
            for (Map.Entry<UUID, String> entry : claim.trusted().entrySet()) {
                yaml.set(path + ".trusted." + entry.getKey(), entry.getValue());
            }
        }
        try {
            RootRecordFolders.ensureDir(plugin);
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save " + DATA_FILE + ": " + ex.getMessage());
        }
    }

    public void appendHistory(
            String action,
            String status,
            Player actor,
            ClaimRecord claim,
            double amountGold,
            int ownerClaimCountBefore,
            int ownerClaimCountAfter,
            String details) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("schema-version", 1);
        long id = Math.max(1L, yaml.getLong("meta.next-history-id", 1L));
        String path = "history." + id;
        yaml.set(path + ".created-at", System.currentTimeMillis());
        yaml.set(path + ".action", action);
        yaml.set(path + ".status", status);
        yaml.set(path + ".amount-g", amountGold);
        yaml.set(path + ".owner-claim-count-before", ownerClaimCountBefore);
        yaml.set(path + ".owner-claim-count-after", ownerClaimCountAfter);
        yaml.set(path + ".details", details == null ? "" : details);
        if (actor != null) {
            yaml.set(path + ".actor.uuid", actor.getUniqueId().toString());
            yaml.set(path + ".actor.name", actor.getName());
        }
        if (claim != null) {
            yaml.set(path + ".claim.world", claim.key().world());
            yaml.set(path + ".claim.x", claim.key().x());
            yaml.set(path + ".claim.y", claim.key().y());
            yaml.set(path + ".claim.z", claim.key().z());
            yaml.set(path + ".claim.radius-blocks", claim.radiusBlocks());
            yaml.set(path + ".claim.parent-key", claim.parentKey() == null ? "" : claim.parentKey().storageId());
            yaml.set(path + ".claim.owner.uuid", claim.ownerId().toString());
            yaml.set(path + ".claim.owner.name", claim.ownerName());
            yaml.set(path + ".claim.paid-gold", claim.paidGold());
        }
        yaml.set("meta.next-history-id", id + 1);
        try {
            RootRecordFolders.ensureDir(plugin);
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not append RootClaims history: " + ex.getMessage());
        }
    }

    public ClaimRecord get(ClaimKey key) {
        return claims.get(key);
    }

    public ClaimRecord containing(Block block) {
        if (block == null) {
            return null;
        }
        ClaimRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ClaimRecord claim : claims.values()) {
            if (!claim.contains(block)) {
                continue;
            }
            double distance = claim.horizontalDistance(block.getX(), block.getZ());
            if (distance < bestDistance) {
                best = claim;
                bestDistance = distance;
            }
        }
        return best;
    }

    public ClaimRecord containing(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        if (location.getBlockY() < ClaimRecord.MINING_FLOOR_Y) {
            return null;
        }
        ClaimRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ClaimRecord claim : claims.values()) {
            if (!claim.contains(location)) {
                continue;
            }
            double distance = claim.horizontalDistance(location.getBlockX(), location.getBlockZ());
            if (distance < bestDistance) {
                best = claim;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Nearest claim covering this XZ column (ignores Y — underworld mining rights). */
    public ClaimRecord containingColumn(String worldName, int blockX, int blockZ) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        ClaimRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ClaimRecord claim : claims.values()) {
            if (!claim.coversColumn(worldName, blockX, blockZ)) {
                continue;
            }
            double distance = claim.horizontalDistance(blockX, blockZ);
            if (distance < bestDistance) {
                best = claim;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Nearest claim whose unclaimed territory band covers this location.
     * Overlapping bands split 50/50 (Voronoi / nearest center). Y &lt; 0 is never territory.
     */
    public ClaimRecord territoryContaining(Location location, int bufferBlocks) {
        if (location == null || location.getWorld() == null || bufferBlocks <= 0) {
            return null;
        }
        if (location.getBlockY() < ClaimRecord.MINING_FLOOR_Y) {
            return null;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        ClaimRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ClaimRecord claim : claims.values()) {
            if (!claim.containsTerritory(world, y, x, z, bufferBlocks)) {
                continue;
            }
            double distance = claim.horizontalDistance(x, z);
            if (distance < bestDistance) {
                best = claim;
                bestDistance = distance;
            }
        }
        return best;
    }

    public boolean add(ClaimRecord claim) {
        if (claims.containsKey(claim.key())) {
            return false;
        }
        claims.put(claim.key(), claim);
        save();
        return true;
    }

    public boolean remove(ClaimKey key) {
        if (claims.remove(key) == null) {
            return false;
        }
        save();
        return true;
    }

    /** Removes every claim owned by {@code ownerId}. Returns removed records (pre-delete snapshot). */
    public List<ClaimRecord> removeOwnedBy(UUID ownerId) {
        List<ClaimRecord> removed = new ArrayList<>();
        if (ownerId == null) {
            return removed;
        }
        for (ClaimRecord claim : List.copyOf(claims.values())) {
            if (ownerId.equals(claim.ownerId())) {
                claims.remove(claim.key());
                removed.add(claim);
            }
        }
        if (!removed.isEmpty()) {
            save();
        }
        return removed;
    }

    public int countOwnedBy(UUID ownerId) {
        int count = 0;
        for (ClaimRecord claim : claims.values()) {
            if (claim.ownerId().equals(ownerId)) {
                count++;
            }
        }
        return count;
    }

    public List<ClaimRecord> ownedBy(UUID ownerId) {
        List<ClaimRecord> out = new ArrayList<>();
        for (ClaimRecord claim : claims.values()) {
            if (claim.ownerId().equals(ownerId)) {
                out.add(claim);
            }
        }
        out.sort(Comparator.comparing((ClaimRecord claim) -> claim.key().world())
                .thenComparingInt(claim -> claim.key().x())
                .thenComparingInt(claim -> claim.key().y())
                .thenComparingInt(claim -> claim.key().z()));
        return out;
    }

    public List<ClaimRecord> rootsOwnedBy(UUID ownerId) {
        List<ClaimRecord> out = new ArrayList<>();
        for (ClaimRecord claim : claims.values()) {
            if (claim.ownerId().equals(ownerId) && claim.isAreaRoot()) {
                out.add(claim);
            }
        }
        out.sort(Comparator.comparingLong(ClaimRecord::createdAtMillis)
                .thenComparing(c -> c.key().storageId()));
        return out;
    }

    public ClaimRecord areaRoot(ClaimRecord claim) {
        if (claim == null) {
            return null;
        }
        ClaimRecord current = claim;
        int guard = 0;
        while (current.parentKey() != null && guard++ < 64) {
            ClaimRecord parent = claims.get(current.parentKey());
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return current;
    }

    public List<ClaimRecord> areaCircles(ClaimRecord anyInArea) {
        ClaimRecord root = areaRoot(anyInArea);
        if (root == null) {
            return List.of();
        }
        List<ClaimRecord> out = new ArrayList<>();
        for (ClaimRecord claim : claims.values()) {
            if (claim.ownerId().equals(root.ownerId()) && root.key().equals(areaRoot(claim).key())) {
                out.add(claim);
            }
        }
        return out;
    }

    public int areaLevel(ClaimRecord anyInArea) {
        return areaCircles(anyInArea).size();
    }

    public int countLevel10Areas(UUID ownerId, int maxLevel) {
        int count = 0;
        for (ClaimRecord root : rootsOwnedBy(ownerId)) {
            if (areaLevel(root) >= maxLevel) {
                count++;
            }
        }
        return count;
    }

    public boolean hasDependentChildren(ClaimRecord claim) {
        if (claim == null) {
            return false;
        }
        ClaimKey key = claim.key();
        for (ClaimRecord other : claims.values()) {
            if (key.equals(other.parentKey())) {
                return true;
            }
        }
        return false;
    }

    public ClaimRecord findAreaByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String needle = name.toLowerCase(java.util.Locale.ROOT);
        for (ClaimRecord claim : claims.values()) {
            if (!claim.isAreaRoot()) {
                continue;
            }
            String label = claim.areaNameOrOwner();
            if (label != null && label.toLowerCase(java.util.Locale.ROOT).equals(needle)) {
                return claim;
            }
        }
        return null;
    }

    public ClaimRecord findAreaByName(UUID ownerId, String name) {
        if (ownerId == null || name == null || name.isBlank()) {
            return null;
        }
        String needle = name.toLowerCase(java.util.Locale.ROOT);
        for (ClaimRecord root : rootsOwnedBy(ownerId)) {
            String label = root.areaNameOrOwner();
            if (label != null && label.toLowerCase(java.util.Locale.ROOT).equals(needle)) {
                return root;
            }
        }
        return null;
    }

    public boolean ownerHasAreaName(UUID ownerId, String name) {
        if (ownerId == null || name == null || name.isBlank()) {
            return false;
        }
        String needle = name.toLowerCase(java.util.Locale.ROOT);
        for (ClaimRecord root : rootsOwnedBy(ownerId)) {
            String label = root.areaNameOrOwner();
            if (label != null && label.toLowerCase(java.util.Locale.ROOT).equals(needle)) {
                return true;
            }
        }
        return false;
    }

    public Collection<ClaimRecord> all() {
        return List.copyOf(claims.values());
    }

    private static String idFor(ClaimKey key) {
        String raw = key.storageId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static ClaimKey parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new ClaimKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
