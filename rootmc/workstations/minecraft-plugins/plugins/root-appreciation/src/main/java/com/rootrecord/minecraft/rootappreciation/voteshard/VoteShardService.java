package com.rootrecord.minecraft.rootappreciation.voteshard;

import com.rootrecord.minecraft.common.RootMcEnderChestResolver;
import com.rootrecord.minecraft.common.RootMcEnderChestService;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import com.rootrecord.minecraft.rootappreciation.AppreciationStore;
import com.rootrecord.minecraft.rootappreciation.RootAppreciationPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Mint, backfill, merge Vote Shards into double /ec (per-server). */
public final class VoteShardService {

    /** Ava_Ivy — real Microsoft account (ex-wildecho94). 25% Council seat (synthetic, not minted). */
    public static final UUID AVA_UUID = UUID.fromString("78c3de61-0fd6-4800-9eda-cc178eaae34b");
    /** Retired synthetic Council UUID — still excluded from human totals. */
    public static final UUID AVA_LEGACY_SYNTHETIC_UUID =
            UUID.fromString("a0a10000-0000-4000-a000-000000000001");
    public static final String AVA_USERNAME = "Ava_Ivy";

    private final RootAppreciationPlugin plugin;
    private final VoteShardItem items;
    private final String issuedTable;
    private final String pendingTable;
    private final String votesTable;
    private volatile boolean ready;

    public static boolean isAvaSeat(UUID uuid) {
        return uuid != null && (AVA_UUID.equals(uuid) || AVA_LEGACY_SYNTHETIC_UUID.equals(uuid));
    }

    public VoteShardService(RootAppreciationPlugin plugin, VoteShardItem items, String tablePrefix) {
        this.plugin = plugin;
        this.items = items;
        String prefix = tablePrefix == null || tablePrefix.isBlank() ? "root_" : tablePrefix;
        this.issuedTable = prefix + "vote_shard_issued";
        this.pendingTable = prefix + "vote_shard_pending";
        this.votesTable = prefix + "rewards_votes";
    }

    public VoteShardItem items() {
        return items;
    }

    public void initSchema() {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, plugin.yaml().config());
        if (db == null || !db.isConfigured()) {
            ready = false;
            return;
        }
        try (Connection c = MysqlConnections.open(db); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      player_uuid CHAR(36) NOT NULL PRIMARY KEY,
                      weight_issued INT NOT NULL DEFAULT 0,
                      updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(issuedTable));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      player_uuid CHAR(36) NOT NULL,
                      weight INT NOT NULL,
                      idempotency_key VARCHAR(96) NOT NULL,
                      created_at DATETIME NOT NULL,
                      delivered_at DATETIME NULL,
                      UNIQUE KEY uq_vs_pending (idempotency_key),
                      INDEX idx_vs_pending_player (player_uuid, delivered_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(pendingTable));
            ready = true;
        } catch (Exception ex) {
            ready = false;
            plugin.getLogger().log(Level.SEVERE, "Vote shard schema failed: " + ex.getMessage());
        }
    }

    /** Soft-SPI: +1 weight per listing vote row (idempotent). Deposits into /ec and auto-condenses. */
    public boolean grantVoteShard(UUID uuid, String username, long voteRowId) {
        if (uuid == null || voteRowId <= 0 || !ready) {
            return false;
        }
        String key = "vote-shard:" + voteRowId;
        if (!enqueuePending(uuid, 1, key)) {
            // Already issued for this vote — still try deliver/condense any backlog.
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> deliverPending(online.getUniqueId(), false));
            }
            return false;
        }
        bumpIssued(uuid, 1);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> deliverPending(online.getUniqueId(), true));
        }
        if (!isAvaSeat(uuid)) {
            Bukkit.getScheduler().runTask(plugin, this::matchAvaToHumans);
        }
        return true;
    }

    /**
     * Ensure EC holds condensed shards for exact owed = max(0, votes - already_in_ec) relative to
     * issued ledger: mint any shortfall of issued vs vote count into EC as condensed form.
     */
    public void ensureExactOwed(Player player) {
        if (player == null || !ready) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int votes = countVotes(uuid);
        int issued = weightIssued(uuid);
        if (votes > issued) {
            int need = votes - issued;
            bumpIssued(uuid, need);
            enqueuePending(uuid, need, "backfill:" + uuid + ":" + votes);
        }
        deliverPending(uuid, false);
        if (!isAvaSeat(uuid)) {
            Bukkit.getScheduler().runTask(plugin, this::matchAvaToHumans);
        }
    }

    /**
     * Ava's Council seat is a synthetic <strong>25%</strong> lock in the API — do not mint EC shards
     * to match humans. Clears any leftover 50%-era issued weight.
     *
     * @return Ava issued weight after sync (always 0)
     */
    public int matchAvaToHumans() {
        if (!ready) {
            return 0;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::matchAvaToHumans);
            return 0;
        }
        int avaIssued = weightIssued(AVA_UUID);
        if (avaIssued != 0) {
            setIssued(AVA_UUID, 0);
            plugin.getLogger().info("Ava EC seat cleared · 25% Council is synthetic, not minted shards");
        }
        syncCloudWeights(List.of(AVA_UUID));
        return 0;
    }

    public int listingVoteCount(UUID uuid) {
        return uuid == null ? 0 : countVotes(uuid);
    }

    public int digitalIssued(UUID uuid) {
        return uuid == null ? 0 : weightIssued(uuid);
    }

    public int ecPower(Player player) {
        return player == null ? 0 : ecPower(player.getUniqueId());
    }

    public int ecPower(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        RootMcEnderChestService ec = RootMcEnderChestResolver.resolve(plugin);
        ItemStack[] contents;
        if (ec != null) {
            contents = ec.contents(uuid);
        } else {
            Player online = Bukkit.getPlayer(uuid);
            contents = online != null ? online.getEnderChest().getContents() : new ItemStack[0];
        }
        return items.sumPower(contents);
    }

    public int mergeEnder(Player player) {
        return autoCondenseEnder(player, true);
    }

    /**
     * Convert one digital Vote Shard into a physical inventory item.
     * Deducts issued weight and, when present, one power from /ec.
     *
     * @return {@code null} on success, otherwise a player-facing error
     */
    public String craftPhysical(Player player) {
        if (player == null || !player.isOnline()) {
            return "players_only";
        }
        UUID uuid = player.getUniqueId();
        int issued = digitalIssued(uuid);
        int ec = ecPower(player);
        if (issued < 1 && ec < 1) {
            return "none";
        }
        if (issued >= 1) {
            decrementIssued(uuid, 1);
        }
        if (ec >= 1) {
            reduceEnderPower(player, 1);
        }
        ItemStack shard = items.create(VoteShardItem.Form.SHARD, 1, 1);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(shard);
        if (!overflow.isEmpty()) {
            for (ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            player.sendMessage(plugin.colorize(
                    "&aCrafted a physical &dVote Shard&a · inventory full, dropped nearby."));
        } else {
            player.sendMessage(plugin.colorize(
                    "&aCrafted a physical &dVote Shard&a · 1 digital converted."));
        }
        syncCloudWeights(List.of(uuid));
        return null;
    }

    private void reduceEnderPower(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        RootMcEnderChestService ec = RootMcEnderChestResolver.resolve(plugin);
        ItemStack[] contents;
        if (ec != null) {
            contents = ec.inventory(player).getContents().clone();
        } else {
            contents = player.getEnderChest().getContents().clone();
        }
        int power = items.clearAllForms(contents);
        int remain = Math.max(0, power - amount);
        if (remain > 0) {
            placeCondensed(contents, remain);
        }
        writeContents(player, ec, contents);
    }

    private void decrementIssued(UUID uuid, int delta) {
        if (!ready || uuid == null || delta <= 0) {
            return;
        }
        int next = Math.max(0, weightIssued(uuid) - delta);
        setIssued(uuid, next);
    }

    public int mergeEnderTwoStep(Player player) {
        return autoCondenseEnder(player, true);
    }

    /**
     * Collapse every Vote Shard / Block / Geode in /ec into one maximally condensed certificate
     * (Geode preferred) with exact total voting power.
     */
    public int autoCondenseEnder(Player player, boolean notify) {
        if (player == null || !player.isOnline()) {
            return 0;
        }
        RootMcEnderChestService ec = RootMcEnderChestResolver.resolve(plugin);
        ItemStack[] contents;
        if (ec != null) {
            contents = ec.inventory(player).getContents().clone();
        } else {
            contents = player.getEnderChest().getContents().clone();
        }
        int power = items.clearAllForms(contents);
        if (power <= 0) {
            if (notify) {
                player.sendMessage(plugin.colorize("&eNo Vote Shards in &f/ec &eto condense."));
            }
            return 0;
        }
        if (!placeCondensed(contents, power)) {
            // Should not happen after clear — restore as condensed into slot 0.
            contents[0] = items.condensed(power);
        }
        writeContents(player, ec, contents);
        if (notify) {
            VoteShardItem.Form form = items.formOf(findFirstVote(contents));
            String label = form != null ? form.display() : "Vote Geode";
            player.sendMessage(plugin.colorize(
                    "&aCondensed Vote Shards in /ec → &f" + label + " &7(power " + power + ")"));
        }
        syncCloudWeights(List.of(player.getUniqueId()));
        return power;
    }

    /** Deliver all pending weight into /ec, then max-condense. */
    private void deliverPending(UUID uuid, boolean voteNotify) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline() || !ready) {
            return;
        }
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, plugin.yaml().config());
        if (db == null) {
            return;
        }
        int pendingWeight = 0;
        List<Long> pendingIds = new ArrayList<>();
        try (Connection c = MysqlConnections.open(db);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, weight FROM " + pendingTable
                             + " WHERE player_uuid = ? AND delivered_at IS NULL ORDER BY id ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pendingIds.add(rs.getLong(1));
                    pendingWeight += Math.max(0, rs.getInt(2));
                }
            }
            if (pendingWeight <= 0 || pendingIds.isEmpty()) {
                autoCondenseEnder(player, false);
                return;
            }
            if (!depositWeightAndCondense(player, pendingWeight)) {
                player.sendMessage(plugin.colorize(
                        "&e/ec full — Vote Shards pending. Clear space and rejoin (or /voteshard merge)."));
                return;
            }
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE " + pendingTable + " SET delivered_at = UTC_TIMESTAMP() WHERE id = ?")) {
                for (Long id : pendingIds) {
                    up.setLong(1, id);
                    up.addBatch();
                }
                up.executeBatch();
            }
            if (voteNotify) {
                int power = ecPower(player);
                player.sendMessage(plugin.colorize(
                        "&d+1 Vote Shard &7deposited to &f/ec &7· condensed power &f" + power));
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Vote shard deliver: " + ex.getMessage());
        }
        syncCloudWeights(List.of(uuid));
    }

    /**
     * Add {@code addWeight} into /ec Vote Shard holdings and rewrite as one max-condensed stack.
     * Returns false if there is no room for the certificate (EC full of non-shard items).
     */
    private boolean depositWeightAndCondense(Player player, int addWeight) {
        if (addWeight <= 0) {
            return true;
        }
        RootMcEnderChestService ec = RootMcEnderChestResolver.resolve(plugin);
        ItemStack[] contents;
        if (ec != null) {
            contents = ec.inventory(player).getContents().clone();
        } else {
            contents = player.getEnderChest().getContents().clone();
        }
        int existing = items.clearAllForms(contents);
        int total = existing + addWeight;
        if (!placeCondensed(contents, total)) {
            // Restore previous shards so we do not wipe on failure.
            if (existing > 0) {
                placeCondensed(contents, existing);
                writeContents(player, ec, contents);
            }
            return false;
        }
        writeContents(player, ec, contents);
        return true;
    }

    private boolean placeCondensed(ItemStack[] contents, int power) {
        if (power <= 0 || contents == null) {
            return true;
        }
        ItemStack out = items.condensed(power);
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null || contents[i].getType().isAir()) {
                contents[i] = out;
                return true;
            }
        }
        return false;
    }

    private void writeContents(Player player, RootMcEnderChestService ec, ItemStack[] contents) {
        if (ec != null) {
            Inventory inv = ec.inventory(player);
            inv.setContents(contents);
            ec.save(player);
        } else {
            player.getEnderChest().setContents(contents);
        }
    }

    private ItemStack findFirstVote(ItemStack[] contents) {
        if (contents == null) {
            return null;
        }
        for (ItemStack stack : contents) {
            if (items.isVoteShardItem(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** Push EC Vote Shard weights to api.rootmc.net for Council math. */
    public void syncCloudWeights(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var rootmc = Bukkit.getPluginManager().getPlugin("RootMC");
                if (rootmc == null || !rootmc.isEnabled()) {
                    return;
                }
                Object cloud = rootmc.getClass().getMethod("cloud").invoke(rootmc);
                if (cloud == null) {
                    return;
                }
                Class<?> rowClass = null;
                for (Class<?> nested : cloud.getClass().getDeclaredClasses()) {
                    if (nested.getSimpleName().equals("EcVoteShardRow")) {
                        rowClass = nested;
                        break;
                    }
                }
                if (rowClass == null) {
                    return;
                }
                var ctor = rowClass.getConstructor(String.class, int.class);
                List<Object> rows = new ArrayList<>();
                for (UUID id : uuids) {
                    if (id == null) {
                        continue;
                    }
                    int w = ecPower(id);
                    if (isAvaSeat(id)) {
                        w = Math.max(w, weightIssued(AVA_UUID));
                    }
                    rows.add(ctor.newInstance(id.toString(), w));
                }
                if (rows.isEmpty()) {
                    return;
                }
                cloud.getClass().getMethod("syncEcVoteShards", List.class).invoke(cloud, rows);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.FINE, "EC vote shard cloud sync: " + ex.getMessage());
            }
        });
    }

    public void syncCloudAllOnline() {
        List<UUID> ids = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ids.add(p.getUniqueId());
        }
        if (!ids.contains(AVA_UUID)) {
            ids.add(AVA_UUID);
        }
        syncCloudWeights(ids);
    }

    private boolean enqueuePending(UUID uuid, int weight, String key) {
        if (!ready || weight <= 0) {
            return false;
        }
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, plugin.yaml().config());
        if (db == null) {
            return false;
        }
        try (Connection c = MysqlConnections.open(db);
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO " + pendingTable
                             + " (player_uuid, weight, idempotency_key, created_at) VALUES (?, ?, ?, UTC_TIMESTAMP())")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, weight);
            ps.setString(3, key);
            return ps.executeUpdate() > 0;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Vote shard enqueue: " + ex.getMessage());
            return false;
        }
    }

    private void setIssued(UUID uuid, int weight) {
        if (!ready || uuid == null) {
            return;
        }
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, plugin.yaml().config());
        if (db == null) {
            return;
        }
        int w = Math.max(0, weight);
        try (Connection c = MysqlConnections.open(db);
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + issuedTable + " (player_uuid, weight_issued, updated_at) VALUES (?, ?, UTC_TIMESTAMP())"
                             + " ON DUPLICATE KEY UPDATE weight_issued = ?, updated_at = UTC_TIMESTAMP()")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, w);
            ps.setInt(3, w);
            ps.executeUpdate();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Vote shard issued set: " + ex.getMessage());
        }
    }

    private void bumpIssued(UUID uuid, int delta) {
        if (!ready || delta <= 0) {
            return;
        }
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, plugin.yaml().config());
        if (db == null) {
            return;
        }
        try (Connection c = MysqlConnections.open(db);
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + issuedTable + " (player_uuid, weight_issued, updated_at) VALUES (?, ?, UTC_TIMESTAMP())"
                             + " ON DUPLICATE KEY UPDATE weight_issued = weight_issued + ?, updated_at = UTC_TIMESTAMP()")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, delta);
            ps.setInt(3, delta);
            ps.executeUpdate();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Vote shard issued bump: " + ex.getMessage());
        }
    }

    private int weightIssued(UUID uuid) {
        if (!ready) {
            return 0;
        }
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, plugin.yaml().config());
        if (db == null) {
            return 0;
        }
        try (Connection c = MysqlConnections.open(db);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT weight_issued FROM " + issuedTable + " WHERE player_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception ex) {
            return 0;
        }
    }

    private int sumIssuedExcludingAva() {
        if (!ready) {
            return 0;
        }
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, plugin.yaml().config());
        if (db == null) {
            return 0;
        }
        try (Connection c = MysqlConnections.open(db);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(weight_issued), 0) FROM " + issuedTable
                             + " WHERE LOWER(player_uuid) NOT IN (?, ?)")) {
            ps.setString(1, AVA_UUID.toString().toLowerCase(Locale.ROOT));
            ps.setString(2, AVA_LEGACY_SYNTHETIC_UUID.toString().toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt(1)) : 0;
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Ava seat human sum: " + ex.getMessage());
            return 0;
        }
    }

    private void ensureAvaEcMatchesIssued() {
        int want = weightIssued(AVA_UUID);
        if (want <= 0) {
            return;
        }
        Player online = Bukkit.getPlayer(AVA_UUID);
        if (online != null && online.isOnline()) {
            deliverPending(AVA_UUID, false);
            int have = ecPower(online);
            if (have < want) {
                depositWeightAndCondense(online, want - have);
            }
            markAvaPendingDelivered();
            return;
        }
        RootMcEnderChestService ec = RootMcEnderChestResolver.resolve(plugin);
        if (ec == null) {
            return;
        }
        ItemStack[] contents = ec.contents(AVA_UUID);
        int have = items.sumPower(contents);
        int target = Math.max(have, want);
        items.clearAllForms(contents);
        if (!placeCondensed(contents, target)) {
            plugin.getLogger().warning("Ava /ec full — could not place council Vote Shards.");
            return;
        }
        ec.save(AVA_UUID, AVA_USERNAME, contents);
        markAvaPendingDelivered();
    }

    private void markAvaPendingDelivered() {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, plugin.yaml().config());
        if (db == null) {
            return;
        }
        try (Connection c = MysqlConnections.open(db);
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + pendingTable
                             + " SET delivered_at = UTC_TIMESTAMP()"
                             + " WHERE player_uuid = ? AND delivered_at IS NULL")) {
            ps.setString(1, AVA_UUID.toString());
            ps.executeUpdate();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Ava pending mark: " + ex.getMessage());
        }
    }

    private int countVotes(UUID uuid) {
        // Reuse appreciation store votes when possible
        int n = 0;
        for (AppreciationStore.VoteRow ignored : plugin.store().listVotesForPlayer(uuid)) {
            n++;
        }
        return n;
    }
}
