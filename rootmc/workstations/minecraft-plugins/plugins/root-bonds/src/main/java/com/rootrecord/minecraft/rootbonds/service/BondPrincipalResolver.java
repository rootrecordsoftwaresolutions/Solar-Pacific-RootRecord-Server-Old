package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.config.BondsConfig;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;
import com.rootrecord.minecraft.rootbonds.towny.TownyGroups;
import com.rootrecord.minecraft.rootbonds.towny.TownyReflection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BondPrincipalResolver {

    public enum Kind {
        PLAYER,
        TOWN,
        NATION
    }

    public record Holder(
            Kind kind,
            UUID accountUuid,
            String accountName,
            String displayName,
            double principalG) {}

    private final RootBondsPlugin plugin;
    private volatile BondsConfig config;
    private volatile BondsStore store;

    public BondPrincipalResolver(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(BondsConfig config, BondsStore store) {
        this.config = config;
        this.store = store;
    }

    public List<Holder> resolveActivePrincipals() throws SQLException {
        BondsConfig activeConfig = config;
        BondsStore activeStore = store;
        if (activeConfig == null || activeStore == null || !activeConfig.enabled()) {
            return List.of();
        }
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null) {
            return List.of();
        }
        Map<UUID, Instant> logins = loadLastLogins(activeConfig);
        for (Player online : Bukkit.getOnlinePlayers()) {
            logins.put(online.getUniqueId(), Instant.now());
        }

        List<Holder> out = new ArrayList<>();
        Map<UUID, PlayerBondAgg> playerBonds = new LinkedHashMap<>();
        for (BondsStore.BondRow bond : activeStore.listAllActive()) {
            if (BondService.isBondedRoot(bond) && !activeConfig.bondedRootRegisterForEarnings()) {
                continue;
            }
            if (!BondActivityGate.isPlayerActive(bond.ownerUuid(), logins, activeConfig.graceDays())) {
                continue;
            }
            playerBonds.compute(bond.ownerUuid(), (uuid, agg) -> {
                if (agg == null) {
                    return new PlayerBondAgg(bond.ownerName(), bond.principal());
                }
                return new PlayerBondAgg(agg.ownerName(), agg.principalG() + bond.principal());
            });
        }
        for (Map.Entry<UUID, PlayerBondAgg> entry : playerBonds.entrySet()) {
            PlayerBondAgg agg = entry.getValue();
            if (agg.principalG() + 1e-9 < activeConfig.minPrincipalG()) {
                continue;
            }
            out.add(new Holder(
                    Kind.PLAYER,
                    entry.getKey(),
                    agg.ownerName(),
                    agg.ownerName(),
                    GoldMoney.round(agg.principalG())));
        }

        if (TownyReflection.isAvailable()) {
            for (TownyGroups.NamedGroup town : TownyGroups.allTowns()) {
                addGovernment(out, economy, Kind.TOWN, town, logins, activeConfig);
            }
            for (TownyGroups.NamedGroup nation : TownyGroups.allNations()) {
                addGovernment(out, economy, Kind.NATION, nation, logins, activeConfig);
            }
        }
        return out;
    }

    private void addGovernment(
            List<Holder> out,
            RootMcEconomyService economy,
            Kind kind,
            TownyGroups.NamedGroup group,
            Map<UUID, Instant> logins,
            BondsConfig activeConfig) {
        if (!activeConfig.autoBondGovernments()) {
            return;
        }
        if (!BondActivityGate.isGroupActive(group.memberUuids(), logins, activeConfig.graceDays())) {
            return;
        }
        String displayName = group.name();
        String bankName = group.bankName();
        UUID bankUuid = group.bankUuid();
        if (bankName == null || bankName.isBlank()) {
            bankName = kind == Kind.TOWN ? "town-" + displayName : "nation-" + displayName;
        }
        if (bankUuid == null) {
            bankUuid = Bukkit.getOfflinePlayer(bankName).getUniqueId();
        }
        if (plugin.govSettings() != null
                && !plugin.govSettings().isEnabled(bankUuid, true)) {
            return;
        }
        if (plugin.bondIncome() != null && plugin.bondIncome().isGovernmentSuspended(bankUuid)) {
            return;
        }
        double balance = GoldMoney.round(economy.balance(bankUuid, bankName));
        if (balance + 1e-9 < activeConfig.minPrincipalG()) {
            return;
        }
        out.add(new Holder(kind, bankUuid, bankName, displayName, balance));
    }

    private Map<UUID, Instant> loadLastLogins(BondsConfig activeConfig) throws SQLException {
        Map<UUID, Instant> out = new HashMap<>();
        if (!activeConfig.mysqlEnabled() || activeConfig.playtimeTable().isBlank()) {
            return out;
        }
        String table = activeConfig.playtimeTable();
        try (Connection c = open(activeConfig)) {
            try {
                loadLastLoginsQuery(c, out, "SELECT uuid, last_login_at FROM " + table + " WHERE scope = '*'");
            } catch (SQLException ex) {
                if (!isUnknownColumn(ex, "scope")) {
                    throw ex;
                }
                loadLastLoginsQuery(c, out, "SELECT uuid, last_login_at FROM " + table);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning(
                    "Bond playtime logins unavailable (" + table + "): " + ex.getMessage());
            return out;
        }
        return out;
    }

    private static void loadLastLoginsQuery(Connection c, Map<UUID, Instant> out, String sql)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                Timestamp ts = rs.getTimestamp("last_login_at");
                if (ts != null) {
                    out.put(uuid, ts.toInstant());
                }
            }
        }
    }

    private static boolean isUnknownColumn(SQLException ex, String column) {
        String msg = ex.getMessage();
        return msg != null
                && msg.toLowerCase().contains("unknown column")
                && msg.toLowerCase().contains(column.toLowerCase());
    }

    private Connection open(BondsConfig activeConfig) throws SQLException {
        String url = "jdbc:mysql://" + activeConfig.mysqlHost() + ":" + activeConfig.mysqlPort() + "/"
                + activeConfig.mysqlDatabase();
        if (!activeConfig.mysqlJdbcParams().isBlank()) {
            url += "?" + activeConfig.mysqlJdbcParams();
        }
        return DriverManager.getConnection(url, activeConfig.mysqlUsername(), activeConfig.mysqlPassword());
    }

    private record PlayerBondAgg(String ownerName, double principalG) {}
}
