package com.rootrecord.minecraft.rootessentials.treasury;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.data.EconomyStore;
import com.rootrecord.minecraft.rootessentials.data.GoldFoundStore;
import com.rootrecord.minecraft.rootessentials.data.ListTotalsStore;
import com.rootrecord.minecraft.rootessentials.data.MySqlSupport;
import com.rootrecord.minecraft.rootessentials.data.TreasuryLedgerStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds {@link ListTotalsStore} snapshots from local MySQL.
 * Skips recompute when last snapshot is fresher than {@link #MIN_INTERVAL}.
 */
public final class ListTotalsRefreshService {

    public static final Duration MIN_INTERVAL = Duration.ofMinutes(5);

    private static final String CLAIMS_SERVER_ID = "4963895e-0964-48b8-81b7-1f40a966e8be";
    private static final String TOWNY_SERVER_ID = "15bbc057-4f8b-4761-abdb-7b7e4d9c7512";

    private static final Map<String, String> TREASURY_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> TOWNY_INTAKE_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> SUPPLY_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> POOL_LABELS = new LinkedHashMap<>();

    static {
        TREASURY_LABELS.put("TAX", "Transaction Taxes");
        TREASURY_LABELS.put("DEATH", "PvP death fees (Server Reserve share)");
        TREASURY_LABELS.put("TOWNY_SINK", "Server fees (Towny claims, outposts, founding, service fees)");
        TREASURY_LABELS.put("LOAN_PRINCIPAL", "Loan repaid");
        TREASURY_LABELS.put("LOAN_INTEREST", "Loan Interest");
        TREASURY_LABELS.put("BOND_ISSUE", "Bonded note deposits");
        TREASURY_LABELS.put("BOND_COUPON_FORFEIT", "Unclaimed bond earnings returned");
        TREASURY_LABELS.put("NOTE_BURN", "Notes retired (tax, /mint gold)");
        TREASURY_LABELS.put("MINT_GROSS", "Physical gold â†’ wallet Notes (/mint)");
        TREASURY_LABELS.put("MINT_REDEEM", "Wallet Notes â†’ physical gold (/mint gold)");
        TREASURY_LABELS.put("DONATION", "Reserve donations (/pay reserve)");
        TREASURY_LABELS.put("VOTE", "Vote Rewards");
        TREASURY_LABELS.put("PLAYTIME", "Playtime Rewards");
        TREASURY_LABELS.put("GRANT", "Grants");
        TREASURY_LABELS.put("DIVIDEND", "Treasury payouts");
        TREASURY_LABELS.put("LOAN_DISBURSE", "Loan Taken");
        TREASURY_LABELS.put("BOND_REDEEM", "Bond redemption (wallet Notes)");
        TREASURY_LABELS.put("BOND_COUPON", "Bond earnings payout");
        TREASURY_LABELS.put("OTHER", "Other treasury movements");

        TOWNY_INTAKE_LABELS.put("new_town", "New town founding (/town new)");
        TOWNY_INTAKE_LABELS.put("new_nation", "New nation founding");
        TOWNY_INTAKE_LABELS.put("claims", "Per-chunk claims (/town claim)");
        TOWNY_INTAKE_LABELS.put("service_fees", "Service fees (survey, warp create, shop stock, etc.)");
        TOWNY_INTAKE_LABELS.put("other", "Other Towny closed-economy sinks");

        SUPPLY_LABELS.put("reserve_balance", "Reserve balance");
        SUPPLY_LABELS.put("player_wallets", "Player wallets");
        SUPPLY_LABELS.put("town_banks", "Town banks");
        SUPPLY_LABELS.put("nation_banks", "Nation banks");
        SUPPLY_LABELS.put("bonds_principal", "Bonds principal outstanding");
        SUPPLY_LABELS.put("physical_gold", "Physical gold (scanned storage)");
        SUPPLY_LABELS.put("gold_minted_net", "Gold minted net (/mint)");
        SUPPLY_LABELS.put("gold_found", "Gold found (all-time)");

        POOL_LABELS.put("playtime_rewards_paid", "Playtime rewards paid");
        POOL_LABELS.put("vote_rewards_paid", "Vote rewards paid");
        POOL_LABELS.put("grants_paid", "Grants paid");
        POOL_LABELS.put("dividends_paid", "Dividends paid");
    }

    private final RootEconomyPlugin plugin;
    private final ListTotalsStore store;
    private final MySqlSupport sql;
    private final String ledgerTable;

    public ListTotalsRefreshService(
            RootEconomyPlugin plugin,
            ListTotalsStore store,
            MySqlSupport sql,
            String tablePrefix) {
        this.plugin = plugin;
        this.store = store;
        this.sql = sql;
        this.ledgerTable = tablePrefix + "treasury_ledger";
    }

    public String resolveScope() {
        RootRecordCloudConfig.CloudSettings cloud = RootRecordCloudConfig.resolve(plugin, null);
        String id = cloud == null ? "" : String.valueOf(cloud.serverId()).trim().toLowerCase();
        if (TOWNY_SERVER_ID.equals(id)) {
            return "towny";
        }
        if (CLAIMS_SERVER_ID.equals(id)) {
            return "claims";
        }
        // Claims hosts often lack Towny; default Claims-like when unknown.
        if (plugin.getServer().getPluginManager().getPlugin("Towny") != null) {
            return "towny";
        }
        return "claims";
    }

    /** @return true if a refresh ran */
    public boolean refreshIfStale() throws Exception {
        String scope = resolveScope();
        Instant latest = store.latestComputedAt(scope);
        if (latest != null && Instant.now().isBefore(latest.plus(MIN_INTERVAL))) {
            return false;
        }
        refreshNow(scope);
        return true;
    }

    public void refreshNow(String scope) throws Exception {
        Instant now = Instant.now();
        TreasuryLedgerStore ledger = plugin.treasuryLedger();
        EconomyStore economy = plugin.economy();
        if (ledger == null || economy == null) {
            return;
        }

        TreasuryLedgerStore.LedgerTotals all = ledger.totalsAllTime();
        Map<TreasuryLedgerType, Double> byType = all.byType();

        for (Map.Entry<String, String> e : TREASURY_LABELS.entrySet()) {
            String cat = e.getKey();
            double amount;
            if ("MINT_GROSS".equals(cat)) {
                amount = ledger.totalGoldMinedGross() + ledger.totalMintRedeemGross();
            } else if ("MINT_REDEEM".equals(cat)) {
                amount = ledger.totalMintRedeemGross();
            } else {
                try {
                    amount = byType.getOrDefault(TreasuryLedgerType.valueOf(cat), 0.0);
                } catch (IllegalArgumentException ex) {
                    amount = 0;
                }
            }
            store.upsert(scope, "treasury_type", cat, e.getValue(), amount, now);
        }

        Map<String, Double> intake = townyIntakeAllTime();
        for (Map.Entry<String, String> e : TOWNY_INTAKE_LABELS.entrySet()) {
            store.upsert(scope, "towny_intake", e.getKey(), e.getValue(),
                    intake.getOrDefault(e.getKey(), 0.0), now);
        }

        double overIssueRepaid = ledger.totalOverIssueShortfallRepaid();
        double reserveBalance = EconomyBaseline.headlineReserveBalance(all.net(), overIssueRepaid);
        double wallets = economy.sumPlayerWalletGold();
        double towns = economy.sumTownBankGold();
        double nations = economy.sumNationBankGold();
        double mintNet = ledger.totalGoldMinedGross();
        double goldFound = 0;
        GoldFoundStore gf = plugin.goldFoundStore();
        if (gf != null) {
            goldFound = gf.sumTotalGoldFound();
        }
        double bondsPrincipal = readBondsPrincipal();
        double physicalGold = 0;

        store.upsert(scope, "supply", "reserve_balance", SUPPLY_LABELS.get("reserve_balance"), reserveBalance, now);
        store.upsert(scope, "supply", "player_wallets", SUPPLY_LABELS.get("player_wallets"), wallets, now);
        store.upsert(scope, "supply", "town_banks", SUPPLY_LABELS.get("town_banks"), towns, now);
        store.upsert(scope, "supply", "nation_banks", SUPPLY_LABELS.get("nation_banks"), nations, now);
        store.upsert(scope, "supply", "bonds_principal", SUPPLY_LABELS.get("bonds_principal"), bondsPrincipal, now);
        store.upsert(scope, "supply", "physical_gold", SUPPLY_LABELS.get("physical_gold"), physicalGold, now);
        store.upsert(scope, "supply", "gold_minted_net", SUPPLY_LABELS.get("gold_minted_net"), mintNet, now);
        store.upsert(scope, "supply", "gold_found", SUPPLY_LABELS.get("gold_found"), goldFound, now);

        store.upsert(scope, "pools", "playtime_rewards_paid", POOL_LABELS.get("playtime_rewards_paid"),
                byType.getOrDefault(TreasuryLedgerType.PLAYTIME, 0.0), now);
        store.upsert(scope, "pools", "vote_rewards_paid", POOL_LABELS.get("vote_rewards_paid"),
                byType.getOrDefault(TreasuryLedgerType.VOTE, 0.0), now);
        store.upsert(scope, "pools", "grants_paid", POOL_LABELS.get("grants_paid"),
                byType.getOrDefault(TreasuryLedgerType.GRANT, 0.0), now);
        store.upsert(scope, "pools", "dividends_paid", POOL_LABELS.get("dividends_paid"),
                byType.getOrDefault(TreasuryLedgerType.DIVIDEND, 0.0), now);
    }

    private Map<String, Double> townyIntakeAllTime() throws SQLException {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String k : TOWNY_INTAKE_LABELS.keySet()) {
            out.put(k, 0.0);
        }
        try (Connection c = sql.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT amount, details FROM " + ledgerTable + " WHERE entry_type = 'TOWNY_SINK'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double amount = GoldMoney.round(rs.getDouble("amount"));
                if (amount <= 0) {
                    continue;
                }
                String details = rs.getString("details");
                String d = details == null ? "" : details.toLowerCase();
                String key;
                if (d.startsWith("towny:new-town") || d.startsWith("backfill:towny:new-town")) {
                    key = "new_town";
                } else if (d.startsWith("towny:new-nation") || d.startsWith("backfill:towny:new-nation")) {
                    key = "new_nation";
                } else if (d.startsWith("service-fee:")) {
                    key = "service_fees";
                } else if (d.startsWith("towny:claim") || d.startsWith("backfill:towny:claim")
                        || d.startsWith("towny:outpost") || d.startsWith("towny:bonus-townblock")
                        || d.startsWith("backfill:towny:bonus-block")) {
                    key = "claims";
                } else {
                    key = "other";
                }
                out.put(key, GoldMoney.round(out.getOrDefault(key, 0.0) + amount));
            }
        }
        return out;
    }

    private double readBondsPrincipal() {
        try {
            org.bukkit.plugin.Plugin bonds = plugin.getServer().getPluginManager().getPlugin("Root-Bonds");
            if (bonds == null || !bonds.isEnabled()) {
                return 0;
            }
            Object store = bonds.getClass().getMethod("bondStore").invoke(bonds);
            if (store == null) {
                return 0;
            }
            Object total = store.getClass().getMethod("totalActivePrincipal").invoke(store);
            if (total instanceof Number n) {
                return GoldMoney.round(n.doubleValue());
            }
        } catch (ReflectiveOperationException ignored) {
            // Bonds plugin API may not expose this method yet
        }
        return 0;
    }

    public ListTotalsStore store() {
        return store;
    }

    public static Map<String, String> treasuryLabels() {
        return TREASURY_LABELS;
    }

    public static Map<String, String> townyIntakeLabels() {
        return TOWNY_INTAKE_LABELS;
    }

    public static Map<String, String> supplyLabels() {
        return SUPPLY_LABELS;
    }

    public static Map<String, String> poolLabels() {
        return POOL_LABELS;
    }
}
