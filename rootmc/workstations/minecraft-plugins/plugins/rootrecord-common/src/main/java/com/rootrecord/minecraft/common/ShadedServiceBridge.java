package com.rootrecord.minecraft.common;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves SPI services registered by another plugin when {@code rootrecord-common} is shaded
 * into each jar (duplicate interface classes break {@link org.bukkit.plugin.ServicesManager}).
 */
public final class ShadedServiceBridge {

    private ShadedServiceBridge() {}

    public static RootMcTreasuryService resolveTreasury(Plugin consumer) {
        RootMcTreasuryService local = localRegistration(RootMcTreasuryService.class);
        if (local != null) {
            return local;
        }
        Plugin host = firstEnabledPlugin("Root-Economy", "Root-Essentials");
        if (host == null) {
            if (consumer != null) {
                consumer.getLogger().warning(
                        "Treasury bridge: Root-Economy is not loaded — install/enable root-economy.jar.");
            }
            return null;
        }
        try {
            Object treasury = host.getClass().getMethod("treasury").invoke(host);
            if (treasury == null) {
                consumer.getLogger().warning(
                        "Treasury bridge: " + host.getName()
                                + " treasury() is null (MySQL economy not initialized?).");
                return null;
            }
            boolean hasCredit = false;
            for (Method method : treasury.getClass().getMethods()) {
                if ("creditTreasury".equals(method.getName()) && method.getParameterCount() == 5) {
                    hasCredit = true;
                    break;
                }
            }
            if (!hasCredit) {
                consumer.getLogger().severe(
                        "Treasury bridge: " + host.getName()
                                + " is too old — update root-economy (creditTreasury missing).");
                return null;
            }
            return new ReflectiveTreasury(consumer, treasury);
        } catch (ReflectiveOperationException ex) {
            consumer.getLogger().warning("Treasury bridge failed: " + rootCause(ex));
            return null;
        }
    }

    public static RootMcLoanService resolveLoans(Plugin consumer) {
        RootMcLoanService local = localRegistration(RootMcLoanService.class);
        if (local != null) {
            return local;
        }
        Plugin loans = firstEnabledPlugin("Root-Loans", "Root-Economy", "Root-Essentials");
        if (loans == null) {
            return null;
        }
        try {
            Object service = loans.getClass().getMethod("loans").invoke(loans);
            if (service == null) {
                return null;
            }
            return new ReflectiveLoans(consumer, service);
        } catch (ReflectiveOperationException ex) {
            consumer.getLogger().warning("Loan bridge failed: " + rootCause(ex));
            return null;
        }
    }

    public static RootMcPermsService resolvePerms(Plugin consumer) {
        RootMcPermsService local = localRegistration(RootMcPermsService.class);
        if (local != null) {
            return local;
        }
        Plugin perms = Bukkit.getPluginManager().getPlugin("Root-Perms");
        if (perms == null || !perms.isEnabled()) {
            return null;
        }
        try {
            Object api = perms.getClass().getMethod("permsApi").invoke(perms);
            if (api == null) {
                return null;
            }
            return new ReflectivePerms(consumer, api);
        } catch (ReflectiveOperationException ex) {
            consumer.getLogger().warning("Perms bridge failed: " + rootCause(ex));
            return null;
        }
    }

    public static RootMcWildernessBlockNotifier resolveWildernessBlockNotifier(Plugin consumer) {
        RootMcWildernessBlockNotifier local = localRegistration(RootMcWildernessBlockNotifier.class);
        if (local != null) {
            return local;
        }
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Root-Essentials");
        if (essentials == null || !essentials.isEnabled()) {
            return null;
        }
        try {
            Object notifier = essentials.getClass().getMethod("wildernessBlockNotifier").invoke(essentials);
            if (notifier == null) {
                return null;
            }
            return new ReflectiveWildernessBlockNotifier(notifier);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public static RootMcClaimTerritoryService resolveClaimTerritory(Plugin consumer) {
        RootMcClaimTerritoryService local = localRegistration(RootMcClaimTerritoryService.class);
        if (local != null) {
            return local;
        }
        Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
        if (claims == null || !claims.isEnabled()) {
            return null;
        }
        try {
            Object service = claims.getClass().getMethod("claimTerritory").invoke(claims);
            if (service == null) {
                return null;
            }
            return new ReflectiveClaimTerritory(service);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public static RootMcPublicReachout resolvePublicReachout(Plugin consumer) {
        RootMcPublicReachout local = localRegistration(RootMcPublicReachout.class);
        if (local != null) {
            return local;
        }
        Plugin rootmc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (rootmc == null || !rootmc.isEnabled()) {
            return null;
        }
        try {
            Object reachout = rootmc.getClass().getMethod("publicReachout").invoke(rootmc);
            if (reachout == null) {
                return null;
            }
            return new ReflectivePublicReachout(reachout);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    /**
     * Resolve Discord/Slack API (Root-Core comms, or legacy Root-Discord).
     * Returns null when missing — callers must drop Discord sends.
     */
    public static RootDiscordApi resolveDiscord(Plugin consumer) {
        RootDiscordApi local = localRegistration(RootDiscordApi.class);
        if (local != null) {
            return local;
        }
        Plugin core = Bukkit.getPluginManager().getPlugin(RootDiscordSupport.PLUGIN_NAME);
        if (core != null && core.isEnabled()) {
            RootDiscordApi fromCore = reflectiveDiscordApi(core, "commsApi", "discordApi");
            if (fromCore != null) {
                return fromCore;
            }
        }
        Plugin legacy = Bukkit.getPluginManager().getPlugin(RootDiscordSupport.LEGACY_PLUGIN_NAME);
        if (legacy == null || !legacy.isEnabled()) {
            return null;
        }
        return reflectiveDiscordApi(legacy, "discordApi", "commsApi");
    }

    private static RootDiscordApi reflectiveDiscordApi(Plugin host, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Object api = host.getClass().getMethod(methodName).invoke(host);
                if (api == null) {
                    continue;
                }
                if (api instanceof RootDiscordApi typed) {
                    return typed;
                }
                return new ReflectiveDiscordApi(api);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    public static RootMcVotePerk resolveVotePerk(Plugin consumer) {
        RootMcVotePerk local = localRegistration(RootMcVotePerk.class);
        if (local != null) {
            return local;
        }
        Plugin play = firstEnabledPlugin("Root-Play");
        if (play == null) {
            return null;
        }
        try {
            Object perk = play.getClass().getMethod("votePerk").invoke(play);
            if (perk == null) {
                return null;
            }
            if (perk instanceof RootMcVotePerk typed) {
                return typed;
            }
            return new ReflectiveVotePerk(perk);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public static RootMcNewPlayerGrace resolveNewPlayerGrace(Plugin consumer) {
        RootMcNewPlayerGrace local = localRegistration(RootMcNewPlayerGrace.class);
        if (local != null) {
            return local;
        }
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Root-Essentials");
        if (essentials == null || !essentials.isEnabled()) {
            return null;
        }
        try {
            Object grace = essentials.getClass().getMethod("newPlayerGraceBridge").invoke(essentials);
            if (grace == null) {
                return null;
            }
            return new ReflectiveNewPlayerGrace(grace);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public static RootMcEnderChestService resolveEnderChest(Plugin consumer) {
        RootMcEnderChestService local = localRegistration(RootMcEnderChestService.class);
        if (local != null) {
            return local;
        }
        Plugin economy = firstEnabledPlugin("Root-Economy");
        if (economy == null) {
            return null;
        }
        try {
            Object service = economy.getClass().getMethod("doubleEnderChest").invoke(economy);
            if (service instanceof RootMcEnderChestService ec) {
                return ec;
            }
            return null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public static RootMcBondTransferService resolveBondTransfer(Plugin consumer) {
        RootMcBondTransferService local = localRegistration(RootMcBondTransferService.class);
        if (local != null) {
            return local;
        }
        Plugin bonds = firstEnabledPlugin("Root-Bonds", "Root-Economy", "Root-Essentials");
        if (bonds == null) {
            return null;
        }
        try {
            Object service = bonds.getClass().getMethod("bondTransfer").invoke(bonds);
            if (service == null) {
                return null;
            }
            return new ReflectiveBondTransfer(consumer, service);
        } catch (ReflectiveOperationException ex) {
            consumer.getLogger().warning("Bond transfer bridge failed: " + rootCause(ex));
            return null;
        }
    }

    public static RootMcBondIncomeService resolveBondIncome(Plugin consumer) {
        RootMcBondIncomeService local = localRegistration(RootMcBondIncomeService.class);
        if (local != null) {
            return local;
        }
        Plugin bonds = firstEnabledPlugin("Root-Bonds", "Root-Economy", "Root-Essentials");
        if (bonds == null) {
            return null;
        }
        try {
            Object service = bonds.getClass().getMethod("bondIncome").invoke(bonds);
            if (service == null) {
                return null;
            }
            return new ReflectiveBondIncome(consumer, service);
        } catch (ReflectiveOperationException ex) {
            consumer.getLogger().warning("Bond income bridge failed: " + rootCause(ex));
            return null;
        }
    }

    private static Plugin firstEnabledPlugin(String... names) {
        for (String name : names) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {
                return plugin;
            }
        }
        return null;
    }

    private static <T> T localRegistration(Class<T> type) {
        RegisteredServiceProvider<T> rsp = Bukkit.getServicesManager().getRegistration(type);
        return rsp != null ? rsp.getProvider() : null;
    }

    private static String rootCause(ReflectiveOperationException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private static final class ReflectiveTreasury implements RootMcTreasuryService {

        private final Plugin consumer;
        private final Object delegate;

        private ReflectiveTreasury(Plugin consumer, Object delegate) {
            this.consumer = consumer;
            this.delegate = delegate;
        }

        @Override
        public boolean transactionTaxEnabled() {
            return invoke(boolean.class, "transactionTaxEnabled");
        }

        @Override
        public double transactionTaxRate() {
            return invoke(double.class, "transactionTaxRate");
        }

        @Override
        public double effectiveTransactionTaxRate() {
            try {
                return invoke(double.class, "effectiveTransactionTaxRate");
            } catch (RuntimeException ex) {
                return transactionTaxRate();
            }
        }

        @Override
        public double withholdTransactionTax(UUID payerUuid, String payerName, double gross, String channel) {
            return invoke(double.class, "withholdTransactionTax", payerUuid, payerName, gross, channel);
        }

        @Override
        public void creditTreasury(
                double amount,
                TreasuryLedgerType type,
                UUID sourceUuid,
                String sourceName,
                String details) {
            invokeVoid("creditTreasury", amount, type, sourceUuid, sourceName, details);
        }

        @Override
        public DeathFeeSettlement settleDeathFee(
                UUID victimUuid,
                String victimName,
                UUID killerUuid,
                String killerName,
                double victimBalancePercent,
                double treasuryShareOfFee,
                double minFeeGold) {
            Object raw = invoke(
                    Object.class,
                    "settleDeathFee",
                    victimUuid,
                    victimName,
                    killerUuid,
                    killerName,
                    victimBalancePercent,
                    treasuryShareOfFee,
                    minFeeGold);
            return copyDeathFeeSettlement(raw);
        }

        @Override
        public void depositClosedLoopVault(double amount) {
            invokeVoid("depositClosedLoopVault", amount);
        }

        @Override
        public void settleClosedLoopPayment(UUID payerUuid, String payerName, double gross, String channel) {
            invokeVoid("settleClosedLoopPayment", payerUuid, payerName, gross, channel);
        }

        @Override
        public boolean grantToPlayer(
                UUID recipientUuid,
                String recipientName,
                double amount,
                UUID operatorUuid,
                String operatorName,
                String reason) {
            return invoke(boolean.class, "grantToPlayer", recipientUuid, recipientName, amount, operatorUuid, operatorName, reason);
        }

        @Override
        public RootMcIncomeSweepResult payVoteReward(UUID recipientUuid, String recipientName, double amount, String service) {
            try {
                Method method = findMethod(
                        delegate.getClass(),
                        "payVoteReward",
                        recipientUuid,
                        recipientName,
                        amount,
                        service);
                Object[] bridged = bridgeArgs(
                        method.getParameterTypes(),
                        new Object[] {recipientUuid, recipientName, amount, service});
                Object result = method.invoke(delegate, bridged);
                return result == null ? null : copyIncomeSweep(result);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public RootMcIncomeSweepResult payPlaytimeReward(
                UUID recipientUuid, String recipientName, double amount, String details) {
            try {
                Method method = findMethod(
                        delegate.getClass(),
                        "payPlaytimeReward",
                        recipientUuid,
                        recipientName,
                        amount,
                        details);
                Object[] bridged = bridgeArgs(
                        method.getParameterTypes(),
                        new Object[] {recipientUuid, recipientName, amount, details});
                Object result = method.invoke(delegate, bridged);
                return result == null ? null : copyIncomeSweep(result);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public RootMcIncomeSweepResult payTryReward(
                UUID recipientUuid, String recipientName, double amount, String tryId) {
            try {
                Method method = findMethod(
                        delegate.getClass(),
                        "payTryReward",
                        recipientUuid,
                        recipientName,
                        amount,
                        tryId);
                Object[] bridged = bridgeArgs(
                        method.getParameterTypes(),
                        new Object[] {recipientUuid, recipientName, amount, tryId});
                Object result = method.invoke(delegate, bridged);
                return result == null ? null : copyIncomeSweep(result);
            } catch (ReflectiveOperationException ex) {
                // Older essentials jar without payTryReward — fall back to grant (no loan sweep).
                boolean ok = grantToPlayer(
                        recipientUuid,
                        recipientName,
                        amount,
                        invoke(UUID.class, "treasuryUuid"),
                        invoke(String.class, "treasuryUsername"),
                        "root-try:" + (tryId == null ? "" : tryId));
                return ok ? RootMcIncomeSweepResult.allToWallet(amount) : null;
            }
        }

        @Override
        public boolean payDividend(UUID recipientUuid, String recipientName, double amount, String monthKey) {
            return invoke(boolean.class, "payDividend", recipientUuid, recipientName, amount, monthKey);
        }

        @Override
        public boolean payBondCouponWallet(UUID recipientUuid, String recipientName, double amount, String details) {
            return invoke(boolean.class, "payBondCouponWallet", recipientUuid, recipientName, amount, details);
        }

        @Override
        public boolean payBondRedeemWallet(UUID recipientUuid, String recipientName, double amount, String details) {
            return invoke(boolean.class, "payBondRedeemWallet", recipientUuid, recipientName, amount, details);
        }

        @Override
        public boolean debitTreasuryPhysical(
                double amount,
                TreasuryLedgerType type,
                UUID playerUuid,
                String playerName,
                String details) {
            return invoke(
                    boolean.class,
                    "debitTreasuryPhysical",
                    amount,
                    type,
                    playerUuid,
                    playerName,
                    details);
        }

        @Override
        public boolean disburseLoan(UUID borrowerUuid, String borrowerName, double principal) {
            return invokeBoolean("disburseLoan", borrowerUuid, borrowerName, principal);
        }

        @Override
        public boolean disburseTownLoan(String townName, UUID mayorUuid, double principal) {
            return invokeBoolean("disburseTownLoan", townName, mayorUuid, principal);
        }

        @Override
        public boolean receiveTownLoanRepayment(
                String townName,
                UUID mayorUuid,
                double principalPart,
                double interestPart) {
            return invokeBoolean(
                    "receiveTownLoanRepayment",
                    townName,
                    mayorUuid,
                    principalPart,
                    interestPart);
        }

        @Override
        public boolean receiveLoanRepayment(
                UUID payerUuid,
                String payerName,
                double principalPart,
                double interestPart,
                boolean withdrawFromPayerWallet) {
            return invokeBoolean(
                    "receiveLoanRepayment",
                    payerUuid,
                    payerName,
                    principalPart,
                    interestPart,
                    withdrawFromPayerWallet);
        }

        @Override
        public double treasuryBalance() {
            return invoke(double.class, "treasuryBalance");
        }

        @Override
        public double headlineReserveBalance() {
            return invoke(double.class, "headlineReserveBalance");
        }

        @Override
        public double displayedReserveBalance() {
            try {
                return invoke(double.class, "displayedReserveBalance");
            } catch (IllegalStateException ignored) {
                try {
                    return invoke(double.class, "reserveUtilization");
                } catch (IllegalStateException ignored2) {
                    return headlineReserveBalance();
                }
            }
        }

        @Override
        public double reserveLedgerNet() {
            try {
                return invoke(double.class, "reserveLedgerNet");
            } catch (IllegalStateException ignored) {
                try {
                    return invoke(double.class, "currentLedgerNet");
                } catch (IllegalStateException ignored2) {
                    return headlineReserveBalance();
                }
            }
        }

        @Override
        public List<TreasuryLedgerEntry> ledgerEntriesAfter(long afterMysqlId, int limit) {
            Object raw = invoke(Object.class, "ledgerEntriesAfter", afterMysqlId, limit);
            return copyLedgerEntries(raw);
        }

        @Override
        public UUID treasuryUuid() {
            return invoke(UUID.class, "treasuryUuid");
        }

        @Override
        public String treasuryUsername() {
            return invoke(String.class, "treasuryUsername");
        }

        private List<TreasuryLedgerEntry> copyLedgerEntries(Object raw) {
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<TreasuryLedgerEntry> out = new ArrayList<>();
            for (Object row : list) {
                if (row == null) {
                    continue;
                }
                try {
                    out.add(new TreasuryLedgerEntry(
                            ((Number) invokeOn(row, "mysqlId")).longValue(),
                            toLedgerType(invokeOn(row, "type")),
                            ((Number) invokeOn(row, "amount")).doubleValue(),
                            (UUID) invokeOn(row, "fromUuid"),
                            (UUID) invokeOn(row, "toUuid"),
                            String.valueOf(invokeOn(row, "details")),
                            String.valueOf(invokeOn(row, "createdAt"))));
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // skip malformed row or unknown ledger type until all plugins are on the same common build
                }
            }
            return List.copyOf(out);
        }

        private boolean invokeBoolean(String name, Object... args) {
            try {
                return invoke(boolean.class, name, args);
            } catch (RuntimeException ex) {
                consumer.getLogger().warning("Treasury." + name + " failed: " + rootCause(ex));
                return false;
            }
        }

        private <T> T invoke(Class<T> returnType, String name, Object... args) {
            try {
                Method method = findMethod(delegate.getClass(), name, args);
                Object[] bridged = bridgeArgs(method.getParameterTypes(), args);
                Object result = method.invoke(delegate, bridged);
                return convertReturn(returnType, result);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private void invokeVoid(String name, Object... args) {
            try {
                Method method = findMethod(delegate.getClass(), name, args);
                Object[] bridged = bridgeArgs(method.getParameterTypes(), args);
                method.invoke(delegate, bridged);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private static String rootCause(RuntimeException ex) {
            if (ex.getCause() instanceof ReflectiveOperationException reflective) {
                return ShadedServiceBridge.rootCause(reflective);
            }
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        }
    }

    private static final class ReflectiveLoans implements RootMcLoanService {

        private final Plugin consumer;
        private final Object delegate;

        private ReflectiveLoans(Plugin consumer, Object delegate) {
            this.consumer = consumer;
            this.delegate = delegate;
        }

        @Override
        public RootMcIncomeSweepResult applyIncome(UUID uuid, String username, double grossIncome) {
            try {
                Method method = findMethod(delegate.getClass(), "applyIncome", uuid, username, grossIncome);
                Object result = method.invoke(delegate, uuid, username, grossIncome);
                return copyIncomeSweep(result);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public Optional<LoanBalanceSummary> balanceSummary(UUID uuid) {
            try {
                Method method = findMethod(delegate.getClass(), "balanceSummary", uuid);
                Object result = method.invoke(delegate, uuid);
                if (!(result instanceof Optional<?> optional)) {
                    return Optional.empty();
                }
                if (optional.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(copyLoanSummary(optional.get()));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private static LoanBalanceSummary copyLoanSummary(Object foreign) throws ReflectiveOperationException {
            return new LoanBalanceSummary(
                    ((Number) invokeOn(foreign, "owed")).doubleValue(),
                    ((Number) invokeOn(foreign, "maxLoan")).doubleValue(),
                    ((Number) invokeOn(foreign, "takesInRolling24h")).intValue(),
                    ((Number) invokeOn(foreign, "maxTakesPer24h")).intValue(),
                    ((Number) invokeOn(foreign, "millisUntilNextTakeSlot")).longValue());
        }
    }

    private static TreasuryLedgerType toLedgerType(Object value) {
        String name = value instanceof Enum<?> e ? e.name() : String.valueOf(value);
        try {
            return TreasuryLedgerType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return TreasuryLedgerType.OTHER;
        }
    }

    private static Method findMethod(Class<?> type, String name, Object... args) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            return method;
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Object[] bridgeArgs(Class<?>[] paramTypes, Object[] args) throws ReflectiveOperationException {
        Object[] bridged = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            bridged[i] = bridgeArg(paramTypes[i], args[i]);
        }
        return bridged;
    }

    private static Object bridgeArg(Class<?> paramType, Object arg) throws ReflectiveOperationException {
        if (arg == null) {
            return null;
        }
        if (paramType.isInstance(arg)) {
            return arg;
        }
        if (paramType.isEnum() && arg instanceof Enum<?> localEnum) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object foreign = Enum.valueOf((Class<Enum>) paramType.asSubclass(Enum.class), localEnum.name());
            return foreign;
        }
        return arg;
    }

    private static RootMcIncomeSweepResult copyIncomeSweep(Object foreign) throws ReflectiveOperationException {
        return new RootMcIncomeSweepResult(
                ((Number) invokeOn(foreign, "toWallet")).doubleValue(),
                ((Number) invokeOn(foreign, "toLoanRepaid")).doubleValue());
    }

    private static DeathFeeSettlement copyDeathFeeSettlement(Object foreign) {
        if (foreign == null) {
            return null;
        }
        if (foreign instanceof DeathFeeSettlement settlement) {
            return settlement;
        }
        try {
            return new DeathFeeSettlement(
                    ((Number) invokeOn(foreign, "grossFee")).doubleValue(),
                    ((Number) invokeOn(foreign, "treasuryAmount")).doubleValue(),
                    ((Number) invokeOn(foreign, "killerAmount")).doubleValue());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class ReflectiveWildernessBlockNotifier implements RootMcWildernessBlockNotifier {

        private final Object delegate;

        private ReflectiveWildernessBlockNotifier(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onWildernessBlockChange(
                UUID playerId,
                String playerName,
                Material material,
                String worldName,
                int blockX,
                int blockY,
                int blockZ,
                boolean placing) {
            try {
                Method method = findMethod(
                        delegate.getClass(),
                        "onWildernessBlockChange",
                        playerId,
                        playerName,
                        material,
                        worldName,
                        blockX,
                        blockY,
                        blockZ,
                        placing);
                method.invoke(delegate, playerId, playerName, material, worldName, blockX, blockY, blockZ, placing);
            } catch (ReflectiveOperationException ignored) {
                // optional bridge
            }
        }
    }

    private static final class ReflectiveClaimTerritory implements RootMcClaimTerritoryService {

        private final Object delegate;

        private ReflectiveClaimTerritory(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isClaimed(String worldName, int blockX, int blockZ) {
            try {
                Method method = findMethod(delegate.getClass(), "isClaimed", worldName, blockX, blockZ);
                Object[] bridged = bridgeArgs(method.getParameterTypes(), new Object[] {worldName, blockX, blockZ});
                Object result = method.invoke(delegate, bridged);
                return result instanceof Boolean b ? b : Boolean.FALSE;
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        @Override
        public boolean isWildernessFeeExempt(UUID playerId, String worldName, int blockX, int blockZ) {
            try {
                Method method = findMethod(
                        delegate.getClass(), "isWildernessFeeExempt", playerId, worldName, blockX, blockZ);
                Object[] bridged = bridgeArgs(
                        method.getParameterTypes(), new Object[] {playerId, worldName, blockX, blockZ});
                Object result = method.invoke(delegate, bridged);
                return result instanceof Boolean b ? b : Boolean.FALSE;
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        @Override
        public int territoryBufferBlocks() {
            try {
                Method method = findMethod(delegate.getClass(), "territoryBufferBlocks");
                Object result = method.invoke(delegate);
                return result instanceof Number n ? n.intValue() : 0;
            } catch (ReflectiveOperationException ex) {
                return 0;
            }
        }

        @Override
        public String creditWildernessDestroyFee(
                String worldName, int blockX, int blockZ, double amountG, String payerName) {
            try {
                Method method = findMethod(
                        delegate.getClass(),
                        "creditWildernessDestroyFee",
                        worldName,
                        blockX,
                        blockZ,
                        amountG,
                        payerName);
                Object[] bridged = bridgeArgs(
                        method.getParameterTypes(),
                        new Object[] {worldName, blockX, blockZ, amountG, payerName});
                Object result = method.invoke(delegate, bridged);
                return result instanceof String s ? s : null;
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        }
    }

    private static final class ReflectiveVotePerk implements RootMcVotePerk {

        private final Object delegate;

        private ReflectiveVotePerk(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public int requiredVotes() {
            try {
                return invoke(int.class, delegate, "requiredVotes");
            } catch (RuntimeException ex) {
                return 5;
            }
        }

        @Override
        public int votesInWindow(UUID playerId) {
            try {
                return invoke(int.class, delegate, "votesInWindow", playerId);
            } catch (RuntimeException ex) {
                return 0;
            }
        }

        @Override
        public boolean active(UUID playerId) {
            try {
                return invoke(boolean.class, delegate, "active", playerId);
            } catch (RuntimeException ex) {
                return false;
            }
        }

        @Override
        public double taxMultiplier(UUID playerId) {
            try {
                return invoke(double.class, delegate, "taxMultiplier", playerId);
            } catch (RuntimeException ex) {
                return 1.0d;
            }
        }

        @Override
        public double deathFeeMultiplier(UUID playerId) {
            try {
                return invoke(double.class, delegate, "deathFeeMultiplier", playerId);
            } catch (RuntimeException ex) {
                return 1.0d;
            }
        }

        @Override
        public long expiresAtEpochMs(UUID playerId) {
            try {
                return invoke(long.class, delegate, "expiresAtEpochMs", playerId);
            } catch (RuntimeException ex) {
                return 0L;
            }
        }

        @Override
        public void invalidate(UUID playerId) {
            try {
                invoke(void.class, delegate, "invalidate", playerId);
            } catch (RuntimeException ignored) {
            }
        }

        private static <T> T invoke(Class<T> returnType, Object target, String name, Object... args) {
            try {
                Method method = findMethod(target.getClass(), name, args);
                Object[] bridged = bridgeArgs(method.getParameterTypes(), args);
                Object result = method.invoke(target, bridged);
                if (returnType == void.class) {
                    return null;
                }
                return convertReturn(returnType, result);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class ReflectiveNewPlayerGrace implements RootMcNewPlayerGrace {

        private final Object delegate;

        private ReflectiveNewPlayerGrace(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean inGracePeriod(UUID playerId) {
            try {
                return invoke(boolean.class, delegate, "inGracePeriod", playerId);
            } catch (RuntimeException ex) {
                return false;
            }
        }

        @Override
        public long graceRemainingMs(UUID playerId) {
            try {
                return invoke(long.class, delegate, "graceRemainingMs", playerId);
            } catch (RuntimeException ex) {
                return 0L;
            }
        }

        @Override
        public boolean exemptFromDeathTax(UUID playerId) {
            try {
                return invoke(boolean.class, delegate, "exemptFromDeathTax", playerId);
            } catch (RuntimeException ex) {
                return inGracePeriod(playerId);
            }
        }

        private static <T> T invoke(Class<T> returnType, Object target, String name, Object... args) {
            try {
                Method method = findMethod(target.getClass(), name, args);
                Object[] bridged = bridgeArgs(method.getParameterTypes(), args);
                Object result = method.invoke(target, bridged);
                return convertReturn(returnType, result);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertReturn(Class<T> returnType, Object result) {
        if (result == null) {
            return null;
        }
        if (returnType.isInstance(result)) {
            return (T) result;
        }
        if (returnType == RootMcIncomeSweepResult.class) {
            try {
                return (T) copyIncomeSweep(result);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
        if (returnType.isPrimitive() && result instanceof Number number) {
            if (returnType == boolean.class) {
                return (T) Boolean.valueOf(number.intValue() != 0);
            }
            if (returnType == double.class) {
                return (T) Double.valueOf(number.doubleValue());
            }
            if (returnType == int.class) {
                return (T) Integer.valueOf(number.intValue());
            }
            if (returnType == long.class) {
                return (T) Long.valueOf(number.longValue());
            }
        }
        if (returnType == boolean.class && result instanceof Boolean b) {
            return (T) b;
        }
        return (T) result;
    }

    private static final class ReflectiveDiscordApi implements RootDiscordApi {
        private final Object target;

        ReflectiveDiscordApi(Object target) {
            this.target = target;
        }

        @Override
        public boolean isReady() {
            try {
                Object v = target.getClass().getMethod("isReady").invoke(target);
                return v instanceof Boolean b && b;
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        @Override
        public void postChatLine(String username, String message, String kind) {
            try {
                target.getClass()
                        .getMethod("postChatLine", String.class, String.class, String.class)
                        .invoke(target, username, message, kind);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public void postReachout(String username, String uuidOrNull, String message, String kind) {
            try {
                target.getClass()
                        .getMethod("postReachout", String.class, String.class, String.class, String.class)
                        .invoke(target, username, uuidOrNull, message, kind);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public void uploadServerLog(File file, String caption) {
            try {
                target.getClass()
                        .getMethod("uploadServerLog", File.class, String.class)
                        .invoke(target, file, caption);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public void reload() {
            try {
                target.getClass().getMethod("reload").invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static final class ReflectivePublicReachout implements RootMcPublicReachout {
        private final Object target;

        ReflectivePublicReachout(Object target) {
            this.target = target;
        }

        @Override
        public void recordTreasuryOutflow(
                String category,
                String playerName,
                UUID playerUuid,
                double gold,
                boolean notifyImmediately) {
            try {
                target.getClass()
                        .getMethod(
                                "recordTreasuryOutflow",
                                String.class,
                                String.class,
                                UUID.class,
                                double.class,
                                boolean.class)
                        .invoke(target, category, playerName, playerUuid, gold, notifyImmediately);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public void relayGlobalBroadcast(String coloredOrPlainMessage, String kind) {
            try {
                target.getClass()
                        .getMethod("relayGlobalBroadcast", String.class, String.class)
                        .invoke(target, coloredOrPlainMessage, kind);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public String hourlyAnnouncerLine() {
            try {
                return (String) target.getClass().getMethod("hourlyAnnouncerLine").invoke(target);
            } catch (ReflectiveOperationException ex) {
                return "";
            }
        }
    }

    private static Object invokeOn(Object target, String accessor) throws ReflectiveOperationException {
        try {
            return target.getClass().getMethod(accessor).invoke(target);
        } catch (NoSuchMethodException ex) {
            if (target.getClass().isRecord()) {
                for (RecordComponent component : target.getClass().getRecordComponents()) {
                    if (component.getName().equals(accessor)) {
                        return component.getAccessor().invoke(target);
                    }
                }
            }
            throw ex;
        }
    }

    private static final class ReflectiveBondTransfer implements RootMcBondTransferService {
        private final Plugin consumer;
        private final Object target;

        ReflectiveBondTransfer(Plugin consumer, Object target) {
            this.consumer = consumer;
            this.target = target;
        }

        @Override
        public boolean isBondCertificate(org.bukkit.inventory.ItemStack stack) {
            try {
                Object result = target.getClass().getMethod("isBondCertificate", org.bukkit.inventory.ItemStack.class)
                        .invoke(target, stack);
                return Boolean.TRUE.equals(result);
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        @Override
        public int transferCertificates(org.bukkit.entity.Player newOwner, org.bukkit.inventory.ItemStack... stacks) {
            try {
                Object result = target.getClass()
                        .getMethod("transferCertificates", org.bukkit.entity.Player.class, org.bukkit.inventory.ItemStack[].class)
                        .invoke(target, newOwner, stacks);
                return result instanceof Number n ? n.intValue() : 0;
            } catch (ReflectiveOperationException ex) {
                consumer.getLogger().warning("Bond transfer failed: " + rootCause(ex));
                return 0;
            }
        }

        @Override
        public int transferCertificatesTo(java.util.UUID newOwnerUuid, String newOwnerName, org.bukkit.inventory.ItemStack... stacks) {
            try {
                Object result = target.getClass()
                        .getMethod(
                                "transferCertificatesTo",
                                java.util.UUID.class,
                                String.class,
                                org.bukkit.inventory.ItemStack[].class)
                        .invoke(target, newOwnerUuid, newOwnerName, stacks);
                return result instanceof Number n ? n.intValue() : 0;
            } catch (ReflectiveOperationException ex) {
                consumer.getLogger().warning("Bond transfer failed: " + rootCause(ex));
                return 0;
            }
        }
    }

    private static final class ReflectiveBondIncome implements RootMcBondIncomeService {
        private final Plugin consumer;
        private final Object target;

        ReflectiveBondIncome(Plugin consumer, Object target) {
            this.consumer = consumer;
            this.target = target;
        }

        @Override
        public void recordTreasuryIncome(double amount, String type, UUID sourceUuid, String details) {
            try {
                target.getClass()
                        .getMethod("recordTreasuryIncome", double.class, String.class, UUID.class, String.class)
                        .invoke(target, amount, type, sourceUuid, details);
            } catch (ReflectiveOperationException ex) {
                consumer.getLogger().warning("Bond income dispatch failed: " + rootCause(ex));
            }
        }

        @Override
        public void suspendGovernmentBondCoupons(UUID accountUuid) {
            try {
                target.getClass()
                        .getMethod("suspendGovernmentBondCoupons", UUID.class)
                        .invoke(target, accountUuid);
            } catch (ReflectiveOperationException ex) {
                consumer.getLogger().warning("Bond coupon suspend failed: " + rootCause(ex));
            }
        }
    }

    private static final class ReflectivePerms implements RootMcPermsService {
        private final Plugin consumer;
        private final Object target;

        ReflectivePerms(Plugin consumer, Object target) {
            this.consumer = consumer;
            this.target = target;
        }

        @Override
        public boolean hasGroup(UUID playerId, String groupId) {
            return invokeBool("hasGroup", playerId, groupId);
        }

        @Override
        public boolean grantGroup(UUID playerId, String groupId) {
            return invokeBool("grantGroup", playerId, groupId);
        }

        @Override
        public boolean revokeGroup(UUID playerId, String groupId) {
            return invokeBool("revokeGroup", playerId, groupId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public int highestTrackIndex(UUID playerId, List<String> trackOrder) {
            try {
                Object result = target.getClass()
                        .getMethod("highestTrackIndex", UUID.class, List.class)
                        .invoke(target, playerId, trackOrder);
                return result instanceof Number n ? n.intValue() : -1;
            } catch (ReflectiveOperationException ex) {
                consumer.getLogger().warning("Perms.highestTrackIndex failed: " + rootCause(ex));
                return -1;
            }
        }

        @Override
        public boolean has(UUID playerId, String permission) {
            return invokeBool("has", playerId, permission);
        }

        @Override
        public void refresh(org.bukkit.entity.Player player) {
            try {
                target.getClass().getMethod("refresh", org.bukkit.entity.Player.class).invoke(target, player);
            } catch (ReflectiveOperationException ex) {
                consumer.getLogger().warning("Perms.refresh failed: " + rootCause(ex));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public java.util.Set<String> groupsOf(UUID playerId) {
            try {
                Object result = target.getClass().getMethod("groupsOf", UUID.class).invoke(target, playerId);
                if (result instanceof java.util.Set<?> set) {
                    return (java.util.Set<String>) set;
                }
            } catch (ReflectiveOperationException ex) {
                consumer.getLogger().warning("Perms.groupsOf failed: " + rootCause(ex));
            }
            return java.util.Set.of();
        }

        @Override
        public String groupDisplay(String groupId) {
            return invokeString("groupDisplay", groupId);
        }

        @Override
        public String groupPrefix(String groupId) {
            return invokeString("groupPrefix", groupId);
        }

        @Override
        public boolean ensureUser(UUID playerId, String username) {
            return invokeBool("ensureUser", playerId, username);
        }

        private boolean invokeBool(String name, Object... args) {
            try {
                Class<?>[] types = new Class<?>[args.length];
                for (int i = 0; i < args.length; i++) {
                    types[i] = args[i] instanceof UUID ? UUID.class : String.class;
                }
                Object result = target.getClass().getMethod(name, types).invoke(target, args);
                return Boolean.TRUE.equals(result);
            } catch (ReflectiveOperationException ex) {
                consumer.getLogger().warning("Perms." + name + " failed: " + rootCause(ex));
                return false;
            }
        }

        private String invokeString(String name, String arg) {
            try {
                Object result = target.getClass().getMethod(name, String.class).invoke(target, arg);
                return result == null ? "" : String.valueOf(result);
            } catch (ReflectiveOperationException ex) {
                return "";
            }
        }
    }
}
