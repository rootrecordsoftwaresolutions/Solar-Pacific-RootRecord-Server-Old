package com.rootrecord.minecraft.rootmcshops.virtual;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopItemKeys;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Per-player virtual item bins (MySQL). Soft-fails if DB is down. */
public final class VirtualItemStore {

    private final RootMcShopsPlugin plugin;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile boolean enabled;
    private volatile RootMcDatabaseConfig.DatabaseSettings db;
    private volatile String binTable;
    private volatile String listingTable;

    public VirtualItemStore(RootMcShopsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return enabled && ready.get();
    }

    public void init() {
        if (!plugin.yamlConfig().config().getBoolean("virtual.enabled", true)) {
            enabled = false;
            plugin.getLogger().info("Virtual storage disabled in config.");
            return;
        }
        db = RootMcDatabaseConfig.resolve(plugin, plugin.yamlConfig().config());
        enabled = db.enabled() && db.isConfigured();
        if (!enabled) {
            plugin.getLogger().warning("Virtual storage unavailable — MySQL not configured (chest shops still work).");
            return;
        }
        binTable = db.tablePrefix() + "player_item_bin";
        listingTable = db.tablePrefix() + "virtual_listing";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ensureSchema();
                ready.set(true);
                plugin.getLogger().info("Virtual storage MySQL ready (" + binTable + ", " + listingTable + ").");
            } catch (SQLException ex) {
                enabled = false;
                plugin.getLogger().log(Level.SEVERE, "Virtual storage schema failed — feature disabled: " + ex.getMessage());
            }
        });
    }

    public String listingTable() {
        return listingTable;
    }

    public Connection open() throws SQLException {
        return MysqlConnections.open(db);
    }

    private void ensureSchema() throws SQLException {
        try (Connection conn = open(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      owner_uuid CHAR(36) NOT NULL,
                      item_key VARCHAR(128) NOT NULL,
                      item_blob MEDIUMTEXT NOT NULL,
                      qty INT NOT NULL,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      INDEX idx_bin_owner (owner_uuid),
                      INDEX idx_bin_owner_key (owner_uuid, item_key)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(binTable));
            st.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id VARCHAR(40) NOT NULL PRIMARY KEY,
                      owner_uuid CHAR(36) NOT NULL,
                      owner_name VARCHAR(16) NOT NULL,
                      item_key VARCHAR(128) NOT NULL,
                      item_blob MEDIUMTEXT NOT NULL,
                      price_g DOUBLE NOT NULL,
                      listing_type VARCHAR(8) NOT NULL,
                      qty INT NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      INDEX idx_vlist_owner (owner_uuid),
                      INDEX idx_vlist_item (item_key, listing_type),
                      INDEX idx_vlist_price (item_key, listing_type, price_g)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(listingTable));
        }
    }

    public List<VirtualBinEntry> listBins(UUID owner) {
        if (!enabled()) {
            return List.of();
        }
        List<VirtualBinEntry> out = new ArrayList<>();
        try (Connection conn = open();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, owner_uuid, item_key, item_blob, qty FROM " + binTable
                                + " WHERE owner_uuid = ? AND qty > 0 ORDER BY item_key, id")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readBin(rs));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual bin list failed: " + ex.getMessage());
        }
        return out;
    }

    public VirtualBinEntry getBin(long id) {
        if (!enabled()) {
            return null;
        }
        try (Connection conn = open();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, owner_uuid, item_key, item_blob, qty FROM " + binTable + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readBin(rs);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual bin get failed: " + ex.getMessage());
        }
        return null;
    }

    /** Deposit exact stack into owner's bin (merges similar rows). Returns qty deposited or 0. */
    public int deposit(UUID owner, ItemStack stack) {
        if (!enabled() || stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        if (ShopItemKeys.isForbiddenGoldResource(stack)) {
            return 0;
        }
        String key = ShopItemKeys.fromItemStack(stack);
        if (key == null || key.isBlank()) {
            return 0;
        }
        key = ItemStackCodec.normalizeKey(key);
        String blob = ItemStackCodec.encode(stack);
        int amount = stack.getAmount();
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try {
                Long mergeId = null;
                int existingQty = 0;
                try (PreparedStatement find = conn.prepareStatement(
                        "SELECT id, qty, item_blob FROM " + binTable + " WHERE owner_uuid = ? AND item_key = ? FOR UPDATE")) {
                    find.setString(1, owner.toString());
                    find.setString(2, key);
                    try (ResultSet rs = find.executeQuery()) {
                        while (rs.next()) {
                            if (blobEquals(blob, rs.getString("item_blob"))) {
                                mergeId = rs.getLong("id");
                                existingQty = rs.getInt("qty");
                                break;
                            }
                        }
                    }
                }
                if (mergeId != null) {
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE " + binTable + " SET qty = ? WHERE id = ?")) {
                        upd.setInt(1, existingQty + amount);
                        upd.setLong(2, mergeId);
                        upd.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO " + binTable + " (owner_uuid, item_key, item_blob, qty) VALUES (?, ?, ?, ?)")) {
                        ins.setString(1, owner.toString());
                        ins.setString(2, key);
                        ins.setString(3, blob);
                        ins.setInt(4, amount);
                        ins.executeUpdate();
                    }
                }
                conn.commit();
                return amount;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual deposit failed: " + ex.getMessage());
            return 0;
        }
    }

    /** Remove up to qty from a specific bin row. Returns stacks to give the player. */
    public List<ItemStack> withdraw(UUID owner, long binId, int qty) {
        List<ItemStack> out = new ArrayList<>();
        if (!enabled() || qty <= 0) {
            return out;
        }
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try {
                VirtualBinEntry entry;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, owner_uuid, item_key, item_blob, qty FROM " + binTable
                                + " WHERE id = ? AND owner_uuid = ? FOR UPDATE")) {
                    ps.setLong(1, binId);
                    ps.setString(2, owner.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return out;
                        }
                        entry = readBin(rs);
                    }
                }
                int take = Math.min(qty, entry.qty());
                ItemStack template = entry.template();
                if (template == null || take <= 0) {
                    conn.rollback();
                    return out;
                }
                int remaining = entry.qty() - take;
                if (remaining <= 0) {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM " + binTable + " WHERE id = ?")) {
                        del.setLong(1, binId);
                        del.executeUpdate();
                    }
                } else {
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE " + binTable + " SET qty = ? WHERE id = ?")) {
                        upd.setInt(1, remaining);
                        upd.setLong(2, binId);
                        upd.executeUpdate();
                    }
                }
                conn.commit();
                int left = take;
                int max = Math.max(1, template.getMaxStackSize());
                while (left > 0) {
                    int n = Math.min(max, left);
                    out.add(ItemStackCodec.withAmount(template, n));
                    left -= n;
                }
                return out;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual withdraw failed: " + ex.getMessage());
            return List.of();
        }
    }

    /** Atomically move qty from a bin row into caller-held stacks (for listing). */
    public VirtualBinEntry takeForListing(UUID owner, long binId, int qty) {
        List<ItemStack> stacks = withdraw(owner, binId, qty);
        if (stacks.isEmpty()) {
            return null;
        }
        int total = stacks.stream().mapToInt(ItemStack::getAmount).sum();
        ItemStack template = stacks.get(0).clone();
        template.setAmount(1);
        return new VirtualBinEntry(binId, owner, ShopItemKeys.fromItemStack(template), ItemStackCodec.encode(template), total);
    }

    private static VirtualBinEntry readBin(ResultSet rs) throws SQLException {
        return new VirtualBinEntry(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("item_key"),
                rs.getString("item_blob"),
                rs.getInt("qty"));
    }

    private static boolean blobEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        ItemStack sa = ItemStackCodec.decode(a);
        ItemStack sb = ItemStackCodec.decode(b);
        if (sa == null || sb == null) {
            return false;
        }
        if (sa.isSimilar(sb)) {
            return true;
        }
        // Appreciation Tokens share one shop key; merge even when issue-id PDC differs.
        return ShopItemKeys.isAppreciationToken(sa) && ShopItemKeys.isAppreciationToken(sb);
    }
}
