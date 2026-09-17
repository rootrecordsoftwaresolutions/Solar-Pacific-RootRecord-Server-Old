package com.rootrecord.minecraft.rootupkeep.service;

import com.rootrecord.minecraft.common.RootMcBondIncomeService;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin;
import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;
import com.rootrecord.minecraft.rootupkeep.data.LastLoginStore;
import com.rootrecord.minecraft.rootupkeep.towny.TownyGroups;
import com.rootrecord.minecraft.rootupkeep.towny.TownyReflection;
import com.rootrecord.minecraft.rootupkeep.util.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InactivityTaxService {

    public enum TargetKind {
        PLAYER,
        TOWN,
        NATION
    }

    public record TaxResult(
            TargetKind kind,
            String displayName,
            String accountName,
            UUID playerUuid,
            double amount,
            double ratePercent,
            long inactiveDays) {}

    private final UpkeepConfig config;
    private final LastLoginStore logins;
    private final Economy economy;
    private final RootMcTreasuryService treasury;
    private final RootUpkeepPlugin plugin;
    private final DecimalFormat money = new DecimalFormat("0.##");
    private final DecimalFormat rate = new DecimalFormat("0.##");

    public InactivityTaxService(RootUpkeepPlugin plugin,
            UpkeepConfig config,
            LastLoginStore logins,
            Economy economy,
            RootMcTreasuryService treasury) {
        this.plugin = plugin;
        this.config = config;
        this.logins = logins;
        this.economy = economy;
        this.treasury = treasury;
    }

    public List<TaxResult> collectDueTaxes() throws Exception {
        List<TaxResult> due = new ArrayList<>();
        Map<UUID, Instant> lastLogins = logins.loadAllLastLogins();

        for (Player online : Bukkit.getOnlinePlayers()) {
            lastLogins.put(online.getUniqueId(), Instant.now());
        }

        for (Map.Entry<UUID, Instant> entry : lastLogins.entrySet()) {
            addPlayerDue(due, entry.getKey(), entry.getValue(), lastLogins);
        }

        if (TownyReflection.isAvailable()) {
            for (TownyGroups.NamedGroup town : TownyGroups.allTowns()) {
                addGroupDue(due, TargetKind.TOWN, town.name(), "town-" + town.name(), town.memberUuids(), lastLogins);
            }
            for (TownyGroups.NamedGroup nation : TownyGroups.allNations()) {
                addGroupDue(due, TargetKind.NATION, nation.name(), "nation-" + nation.name(), nation.memberUuids(), lastLogins);
            }
        }
        return due;
    }

    private void addPlayerDue(List<TaxResult> due, UUID uuid, Instant lastLogin, Map<UUID, Instant> cache) {
        Instant activeAt = resolveLogin(uuid, lastLogin, cache);
        InactivityTaxRates.Tier tier = InactivityTaxRates.tierFor(config, activeAt);
        if (tier == null) {
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        double balance = economy.getBalance(offline);
        double tax = InactivityTaxRates.taxAmount(config, balance, tier);
        if (tax <= 0) {
            return;
        }
        String name = offline.getName() == null ? uuid.toString().substring(0, 8) : offline.getName();
        due.add(new TaxResult(TargetKind.PLAYER, name, null, uuid, tax, tier.ratePercent(), tier.inactiveDays()));
    }

    private void addGroupDue(
            List<TaxResult> due,
            TargetKind kind,
            String displayName,
            String accountName,
            java.util.Set<UUID> members,
            Map<UUID, Instant> lastLogins) {
        if (TownyGroups.hasActiveMember(members, lastLogins, config.graceDays())) {
            return;
        }
        Instant lastActive = TownyGroups.groupLastActive(members, lastLogins, config.graceDays());
        InactivityTaxRates.Tier tier = InactivityTaxRates.tierFor(config, lastActive);
        if (tier == null) {
            return;
        }
        OfflinePlayer account = Bukkit.getOfflinePlayer(accountName);
        double balance = economy.getBalance(account);
        double tax = InactivityTaxRates.taxAmount(config, balance, tier);
        if (tax <= 0) {
            return;
        }
        due.add(new TaxResult(kind, displayName, accountName, null, tax, tier.ratePercent(), tier.inactiveDays()));
    }

    public boolean apply(TaxResult result) {
        if (economy == null || treasury == null) {
            return false;
        }
        OfflinePlayer account;
        if (result.kind() == TargetKind.PLAYER) {
            account = result.playerUuid() != null
                    ? Bukkit.getOfflinePlayer(result.playerUuid())
                    : Bukkit.getOfflinePlayer(result.displayName());
        } else {
            account = Bukkit.getOfflinePlayer(result.accountName());
        }
        if (!economy.has(account, result.amount())) {
            double balance = economy.getBalance(account);
            if (balance < config.minBalance()) {
                return false;
            }
        }
        if (!economy.withdrawPlayer(account, result.amount()).transactionSuccess()) {
            return false;
        }
        String channel = "inactivity-tax:" + result.kind().name().toLowerCase();
        treasury.creditTreasury(
                result.amount(),
                TreasuryLedgerType.TAX,
                account.getUniqueId(),
                account.getName() == null ? result.displayName() : account.getName(),
                channel);
        if (result.kind() == TargetKind.TOWN || result.kind() == TargetKind.NATION) {
            RootMcBondIncomeService bonds = ShadedServiceBridge.resolveBondIncome(plugin.host());
            if (bonds != null) {
                bonds.suspendGovernmentBondCoupons(account.getUniqueId());
            }
        }
        InactivityTaxAwayNotifier.recordApplied(plugin, result);
        Messages.broadcast(formatBroadcast(result));
        return true;
    }

    private String formatBroadcast(TaxResult result) {
        String template = switch (result.kind()) {
            case PLAYER -> config.playerTaxMessage();
            case TOWN -> config.townTaxMessage();
            case NATION -> config.nationTaxMessage();
        };
        return colorize(template
                .replace("{target}", result.displayName())
                .replace("{amount}", money.format(result.amount()))
                .replace("{rate}", rate.format(result.ratePercent()))
                .replace("{days}", String.valueOf(result.inactiveDays())));
    }

    public String formatRunComplete(int count) {
        return colorize(config.runCompleteMessage().replace("{count}", String.valueOf(count)));
    }

    private Instant resolveLogin(UUID uuid, Instant known, Map<UUID, Instant> cache) {
        if (Bukkit.getPlayer(uuid) != null) {
            return Instant.now();
        }
        if (known != null) {
            return known;
        }
        Instant cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        long lastPlayed = Bukkit.getOfflinePlayer(uuid).getLastPlayed();
        return lastPlayed > 0 ? Instant.ofEpochMilli(lastPlayed) : null;
    }

    private static String colorize(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
