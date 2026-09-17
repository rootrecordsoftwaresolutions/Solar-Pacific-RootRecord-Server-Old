package com.rootrecord.minecraft.rootmcshops.virtual;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopItemKeys;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Chestless listings stored in MySQL (same DB as VirtualItemStore). */
public final class VirtualListingStore {

    private final RootMcShopsPlugin plugin;
    private final VirtualItemStore items;

    public VirtualListingStore(RootMcShopsPlugin plugin, VirtualItemStore items) {
        this.plugin = plugin;
        this.items = items;
    }

    public boolean enabled() {
        return items.enabled();
    }

    public List<VirtualListing> allSell() {
        return listByType("sell", null);
    }

    public List<VirtualListing> allBuy() {
        return listByType("buy", null);
    }

    public List<VirtualListing> sellForItem(String itemKey) {
        return listByType("sell", itemKey);
    }

    public List<VirtualListing> buyForItem(String itemKey) {
        return listByType("buy", itemKey);
    }

    public List<VirtualListing> ownedBy(UUID owner) {
        if (!enabled()) {
            return List.of();
        }
        List<VirtualListing> out = new ArrayList<>();
        try (Connection conn = items.open();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, owner_uuid, owner_name, item_key, item_blob, price_g, listing_type, qty FROM "
                                + items.listingTable()
                                + " WHERE owner_uuid = ? AND qty > 0 ORDER BY created_at DESC")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(read(rs));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual listing owned list failed: " + ex.getMessage());
        }
        return out;
    }

    public VirtualListing get(String id) {
        if (!enabled() || id == null || id.isBlank()) {
            return null;
        }
        try (Connection conn = items.open();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, owner_uuid, owner_name, item_key, item_blob, price_g, listing_type, qty FROM "
                                + items.listingTable()
                                + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return read(rs);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual listing get failed: " + ex.getMessage());
        }
        return null;
    }

    /**
     * Create a sell listing by taking qty from a bin row.
     * @return listing id or null
     */
    public String createSellFromBin(UUID owner, String ownerName, long binId, int qty, double price) {
        if (!enabled() || qty <= 0 || price <= 0 || !Double.isFinite(price)) {
            return null;
        }
        VirtualBinEntry taken = items.takeForListing(owner, binId, qty);
        if (taken == null || taken.qty() <= 0) {
            return null;
        }
        if (ShopItemKeys.isForbiddenGoldResourceKey(taken.itemKey())) {
            // refund
            ItemStackCodec.decode(taken.itemBlob());
            org.bukkit.inventory.ItemStack t = taken.template();
            if (t != null) {
                t.setAmount(taken.qty());
                items.deposit(owner, t);
            }
            return null;
        }
        String id = "v-" + UUID.randomUUID();
        try (Connection conn = items.open();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + items.listingTable()
                                + " (id, owner_uuid, owner_name, item_key, item_blob, price_g, listing_type, qty)"
                                + " VALUES (?, ?, ?, ?, ?, ?, 'sell', ?)")) {
            ps.setString(1, id);
            ps.setString(2, owner.toString());
            ps.setString(3, ownerName == null ? "Player" : ownerName);
            ps.setString(4, ItemStackCodec.normalizeKey(taken.itemKey()));
            ps.setString(5, taken.itemBlob());
            ps.setDouble(6, price);
            ps.setInt(7, taken.qty());
            ps.executeUpdate();
            return id;
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual list create failed: " + ex.getMessage());
            org.bukkit.inventory.ItemStack t = taken.template();
            if (t != null) {
                t.setAmount(taken.qty());
                items.deposit(owner, t);
            }
            return null;
        }
    }

    /** Cancel listing and return remaining qty to owner bin. */
    public boolean cancel(UUID actor, String listingId, boolean admin) {
        if (!enabled()) {
            return false;
        }
        try (Connection conn = items.open()) {
            conn.setAutoCommit(false);
            try {
                VirtualListing listing;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, owner_uuid, owner_name, item_key, item_blob, price_g, listing_type, qty FROM "
                                + items.listingTable()
                                + " WHERE id = ? FOR UPDATE")) {
                    ps.setString(1, listingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        listing = read(rs);
                    }
                }
                if (!admin && !listing.ownerUuid().equals(actor)) {
                    conn.rollback();
                    return false;
                }
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM " + items.listingTable() + " WHERE id = ?")) {
                    del.setString(1, listingId);
                    del.executeUpdate();
                }
                conn.commit();
                if (listing.qty() > 0 && listing.isSell()) {
                    org.bukkit.inventory.ItemStack t = listing.template();
                    if (t != null) {
                        t.setAmount(listing.qty());
                        items.deposit(listing.ownerUuid(), t);
                    }
                }
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual cancel failed: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Purchase qty from a virtual sell listing.
     * @return stacks purchased (empty on failure); Gold settlement is caller's responsibility after success prep —
     *         this method does stock move only after caller should have charged. Prefer {@link VirtualTradeService}.
     */
    public List<org.bukkit.inventory.ItemStack> takeSellStock(String listingId, UUID buyer, int qty) {
        List<org.bukkit.inventory.ItemStack> out = new ArrayList<>();
        if (!enabled() || qty <= 0) {
            return out;
        }
        try (Connection conn = items.open()) {
            conn.setAutoCommit(false);
            try {
                VirtualListing listing;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, owner_uuid, owner_name, item_key, item_blob, price_g, listing_type, qty FROM "
                                + items.listingTable()
                                + " WHERE id = ? FOR UPDATE")) {
                    ps.setString(1, listingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return out;
                        }
                        listing = read(rs);
                    }
                }
                if (!listing.isSell() || listing.ownerUuid().equals(buyer) || listing.qty() <= 0) {
                    conn.rollback();
                    return out;
                }
                int take = Math.min(qty, listing.qty());
                org.bukkit.inventory.ItemStack template = listing.template();
                if (template == null || take <= 0) {
                    conn.rollback();
                    return out;
                }
                int remaining = listing.qty() - take;
                if (remaining <= 0) {
                    try (PreparedStatement del = conn.prepareStatement(
                            "DELETE FROM " + items.listingTable() + " WHERE id = ?")) {
                        del.setString(1, listingId);
                        del.executeUpdate();
                    }
                } else {
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE " + items.listingTable() + " SET qty = ? WHERE id = ?")) {
                        upd.setInt(1, remaining);
                        upd.setString(2, listingId);
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
            plugin.getLogger().warning("Virtual takeSellStock failed: " + ex.getMessage());
            return List.of();
        }
    }

    private List<VirtualListing> listByType(String type, String itemKey) {
        if (!enabled()) {
            return List.of();
        }
        List<VirtualListing> out = new ArrayList<>();
        String sql = "SELECT id, owner_uuid, owner_name, item_key, item_blob, price_g, listing_type, qty FROM "
                + items.listingTable()
                + " WHERE listing_type = ? AND qty > 0";
        if (itemKey != null && !itemKey.isBlank()) {
            sql += " AND item_key = ?";
        }
        sql += " ORDER BY price_g ASC, created_at ASC";
        try (Connection conn = items.open(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type.toLowerCase(Locale.ROOT));
            if (itemKey != null && !itemKey.isBlank()) {
                ps.setString(2, ItemStackCodec.normalizeKey(itemKey));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(read(rs));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Virtual listing query failed: " + ex.getMessage());
        }
        return out;
    }

    private static VirtualListing read(ResultSet rs) throws SQLException {
        return new VirtualListing(
                rs.getString("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("item_key"),
                rs.getString("item_blob"),
                rs.getDouble("price_g"),
                rs.getString("listing_type"),
                rs.getInt("qty"));
    }
}
