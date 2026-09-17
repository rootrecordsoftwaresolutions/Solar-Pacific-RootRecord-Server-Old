package com.rootrecord.minecraft.common;

import java.util.UUID;

/** Closed-loop treasury: taxes, votes, death recycling, grants, dividends. */
public interface RootMcTreasuryService {

    boolean transactionTaxEnabled();

    double transactionTaxRate();

    /** Effective withheld rate (dynamic shortfall when enabled, else {@link #transactionTaxRate()}). */
    default double effectiveTransactionTaxRate() {
        return transactionTaxRate();
    }

    /**
     * Withdraws {@code gross} from payer; credits {@code tax} to treasury. Recipient is not credited —
     * caller should {@code depositIncome(net)} separately (loan sweep).
     *
     * @return net amount for recipient, or {@code -1} on failure
     */
    double withholdTransactionTax(UUID payerUuid, String payerName, double gross, String channel);

    /** Credits treasury without debiting a player (tax sinks, death fees, service fees, etc.). */
    void creditTreasury(
            double amount,
            TreasuryLedgerType type,
            UUID sourceUuid,
            String sourceName,
            String details);

    /**
     * Withdraws a death fee from the victim, credits the reserve share (DEATH ledger), pays killer share,
     * and records killer payout on the treasury ledger (OTHER audit row).
     *
     * @return settlement breakdown, or {@code null} if no fee applied
     */
    DeathFeeSettlement settleDeathFee(
            UUID victimUuid,
            String victimName,
            UUID killerUuid,
            String killerName,
            double victimBalancePercent,
            double treasuryShareOfFee,
            double minFeeGold);

    /** Vault balance only — closed-loop Towny flows (no treasury ledger row). */
    void depositClosedLoopVault(double amount);

    /**
     * Player already paid {@code gross} via Vault withdraw. Credits transaction tax to the ledger (TAX);
     * remainder goes to the reserve vault without a ledger row.
     */
    void settleClosedLoopPayment(UUID payerUuid, String payerName, double gross, String channel);

    /** Operator grant: treasury → player (logged). */
    boolean grantToPlayer(
            UUID recipientUuid,
            String recipientName,
            double amount,
            UUID operatorUuid,
            String operatorName,
            String reason);

    /** Activity dividend: treasury → player (logged as DIVIDEND). */
    boolean payDividend(UUID recipientUuid, String recipientName, double amount, String monthKey);

    /** Bond coupon credited to a wallet (player or town/nation bank). */
    boolean payBondCouponWallet(UUID recipientUuid, String recipientName, double amount, String details);

    /** Bond principal credited to a wallet when notes are auto-redeemed (e.g. expired coupons). */
    boolean payBondRedeemWallet(UUID recipientUuid, String recipientName, double amount, String details);

    /**
     * Debits the Server Reserve for a physical Gold payout (bond coupon / redemption).
     * Does not credit the player wallet — caller issues gold items.
     */
    boolean debitTreasuryPhysical(
            double amount,
            TreasuryLedgerType type,
            UUID playerUuid,
            String playerName,
            String details);

    /** Personal loan: treasury → player (logged as LOAN_DISBURSE). */
    boolean disburseLoan(UUID borrowerUuid, String borrowerName, double principal);

    /** Town loan: Server Reserve → town bank (logged as LOAN_DISBURSE, details {@code town-loan:Name}). */
    boolean disburseTownLoan(String townName, UUID mayorUuid, double principal);

    /** Town loan repayment: town bank → Server Reserve. */
    boolean receiveTownLoanRepayment(
            String townName,
            UUID mayorUuid,
            double principalPart,
            double interestPart);

    /** Vote reward: treasury → player (logged as VOTE). Loan income sweep applies. */
    RootMcIncomeSweepResult payVoteReward(UUID recipientUuid, String recipientName, double amount, String service);

    /** Playtime milestone: treasury → player (logged as PLAYTIME). Loan income sweep applies. */
    RootMcIncomeSweepResult payPlaytimeReward(
            UUID recipientUuid, String recipientName, double amount, String details);

    /** Root-Try reward: treasury → player (logged as GRANT, details {@code root-try:id}). Loan income sweep applies. */
    RootMcIncomeSweepResult payTryReward(
            UUID recipientUuid, String recipientName, double amount, String tryId);

    /**
     * Loan repayment: player → treasury. Principal and interest are logged separately.
     * When {@code withdrawFromPayerWallet} is false, only the treasury is credited (income sweep).
     */
    boolean receiveLoanRepayment(
            UUID payerUuid,
            String payerName,
            double principalPart,
            double interestPart,
            boolean withdrawFromPayerWallet);

    /** towny-server vault wallet (operational float — not the public reserve headline). */
    double treasuryBalance();

    /** Opening + post-reset ledger net − shortfall settlements (matches /reserve and rootmc.net). */
    default double headlineReserveBalance() {
        return treasuryBalance();
    }

    /**
     * /reserve {@code Reserve} line: mint backing − circulating Notes. Can be negative.
     * Ava coin flips only when this is 0+.
     */
    default double displayedReserveBalance() {
        return headlineReserveBalance();
    }

    /** Post-reset treasury ledger net (July 1+); may be negative. Bond payouts pause below zero. */
    default double reserveLedgerNet() {
        return headlineReserveBalance();
    }

    java.util.List<TreasuryLedgerEntry> ledgerEntriesAfter(long afterMysqlId, int limit);

    record TreasuryLedgerEntry(
            long mysqlId,
            TreasuryLedgerType type,
            double amount,
            UUID fromUuid,
            UUID toUuid,
            String details,
            String createdAt) {}

    UUID treasuryUuid();

    String treasuryUsername();
}
