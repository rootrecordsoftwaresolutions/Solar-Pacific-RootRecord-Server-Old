package com.rootrecord.minecraft.rootstat.governance;

import com.rootrecord.minecraft.rootstat.RootStatBridge;
import com.rootrecord.minecraft.rootstat.cloud.CloudApiClient;
import com.rootrecord.minecraft.rootstat.mysql.MySqlPlayerStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Local governance share: digital Vote Shards (listing-vote count) → % of 100.
 * Physical /ec items are not used. Playtime no longer multiplies weight.
 * Prefer cloud snapshot when server credentials exist (paid shards + Pro/Life multipliers).
 */
public final class LocalGovernancePowerService {

    /** Ava_Ivy real account — 25% Council seat (one-third the sum of every other voter). */
    private static final UUID AVA_UUID = UUID.fromString("78c3de61-0fd6-4800-9eda-cc178eaae34b");
    private static final UUID AVA_LEGACY_UUID = UUID.fromString("a0a10000-0000-4000-a000-000000000001");

    private final RootStatBridge bridge;

    private static boolean isAvaSeat(UUID id) {
        return id != null && (AVA_UUID.equals(id) || AVA_LEGACY_UUID.equals(id));
    }

    public LocalGovernancePowerService(RootStatBridge bridge) {
        this.bridge = bridge;
    }

    public CloudApiClient.GovernanceVotingPower resolve(UUID uuid) {
        if (uuid == null) {
            return unavailable();
        }
        try {
            if (bridge.config().hasServerCredentials()) {
                CloudApiClient.GovernanceVotingPower cloud =
                        bridge.cloud().fetchGovernanceVotingPower(uuid.toString());
                if (cloud != null && cloud.ok()) {
                    return cloud;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            CloudApiClient.GovernanceVotingPower local = computeLocal(uuid);
            if (local != null) {
                return local;
            }
        } catch (Exception ex) {
            bridge.getPlugin()
                    .getLogger()
                    .log(Level.FINE, "Local governance failed: " + ex.getMessage());
        }
        return unavailable();
    }

    private CloudApiClient.GovernanceVotingPower computeLocal(UUID uuid) throws Exception {
        MySqlPlayerStore players = bridge.players();
        if (players == null) {
            return null;
        }
        String playersTable = bridge.config().playersTable();

        record Row(String uuid, boolean eligible) {}
        List<Row> rows = new ArrayList<>();
        try (Connection c = players.openConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, (verified = 1 AND account_id IS NOT NULL AND account_id <> '') AS eligible FROM "
                             + playersTable)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString("uuid"), rs.getBoolean("eligible")));
                }
            }
        }

        String target = uuid.toString().toLowerCase(Locale.ROOT);
        String targetCompact = target.replace("-", "");
        boolean linked = players.findByUuid(uuid).map(p -> p.verified()).orElse(false);

        double humanRaw = 0;
        double selfRaw = 0;
        int selfPower = 0;
        boolean foundSelf = false;
        boolean selfIsAva = isAvaSeat(uuid);

        for (Row r : rows) {
            if (r.uuid == null || !r.eligible) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(r.uuid.contains("-") ? r.uuid : insertDashes(r.uuid));
            } catch (Exception ex) {
                continue;
            }
            if (isAvaSeat(id)) {
                continue;
            }
            int power = (int) Math.min(Integer.MAX_VALUE, readVotes(id));
            if (power <= 0) {
                continue;
            }
            double raw = power;
            humanRaw += raw;
            String ru = r.uuid.toLowerCase(Locale.ROOT);
            if (ru.equals(target) || ru.replace("-", "").equals(targetCompact)) {
                selfRaw = raw;
                selfPower = power;
                foundSelf = true;
            }
        }

        if (!foundSelf && linked && !selfIsAva) {
            selfPower = (int) Math.min(Integer.MAX_VALUE, readVotes(uuid));
            selfRaw = selfPower;
            if (selfPower > 0) {
                humanRaw += selfRaw;
                foundSelf = true;
            }
        }

        // Ava 25% of total share → raw = one-third the human pile.
        double avaRaw = humanRaw / 3.0;
        double totalRaw = humanRaw + avaRaw;
        if (selfIsAva) {
            selfRaw = avaRaw;
            selfPower = (int) Math.round(avaRaw);
            foundSelf = humanRaw > 0;
            linked = true;
        }

        if (!linked) {
            return new CloudApiClient.GovernanceVotingPower(
                    true, false, 0, "link account", "", "https://rootmc.net/wiki/constitution/");
        }
        if (!foundSelf || selfPower <= 0) {
            return new CloudApiClient.GovernanceVotingPower(
                    true,
                    false,
                    0,
                    "run /vote on listing sites — digital Vote Shards = listing vote count (+1 Appreciation Token + 1–20 G each)",
                    "",
                    "https://rootmc.net/wiki/constitution/");
        }
        double share = totalRaw > 0 ? (selfRaw / totalRaw) * 100.0 : 0;
        return new CloudApiClient.GovernanceVotingPower(
                true,
                true,
                share,
                String.format(Locale.US, "score %d · %.3f%%", selfPower, share),
                "",
                "https://rootmc.net/wiki/constitution/");
    }

    private static String insertDashes(String compact) {
        String c = compact.replace("-", "");
        if (c.length() != 32) {
            return compact;
        }
        return c.substring(0, 8) + "-" + c.substring(8, 12) + "-" + c.substring(12, 16)
                + "-" + c.substring(16, 20) + "-" + c.substring(20);
    }

    public long voteCount(UUID uuid) {
        return readVotes(uuid);
    }

    private long readVotes(UUID uuid) {
        try {
            MySqlPlayerStore players = bridge.players();
            if (players == null) {
                return 0L;
            }
            String prefix = bridge.config().mysqlTablePrefix();
            if (prefix == null || prefix.isBlank()) {
                prefix = "root_";
            }
            String votesTable = prefix + "rewards_votes";
            try (Connection c = players.openConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT COUNT(*) FROM " + votesTable
                                 + " WHERE LOWER(REPLACE(uuid, '-', '')) = LOWER(REPLACE(?, '-', ''))")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static CloudApiClient.GovernanceVotingPower unavailable() {
        return new CloudApiClient.GovernanceVotingPower(
                false, false, 0, "unavailable", "", "https://rootmc.net/wiki/constitution/");
    }
}
