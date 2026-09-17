package com.rootrecord.minecraft.rootessentials.treasury;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rootessentials.data.EconomyStore;
import com.rootrecord.minecraft.rootessentials.data.MonthlyPlaytimeStore;
import com.rootrecord.minecraft.rootessentials.data.TreasuryLedgerStore;
import com.rootrecord.minecraft.rootessentials.util.HstTime;

import java.time.Instant;
import java.util.UUID;

public final class ReserveStatsService {

    private final TreasuryLedgerStore ledger;
    private final MonthlyPlaytimeStore playtime;
    private final TreasuryManager treasury;
    private final EconomyStore economy;

    public ReserveStatsService(
            TreasuryLedgerStore ledger,
            MonthlyPlaytimeStore playtime,
            TreasuryManager treasury,
            EconomyStore economy,
            double dividendPayoutRatio) {
        this.ledger = ledger;
        this.playtime = playtime;
        this.treasury = treasury;
        this.economy = economy;
    }

    public ReserveSnapshot build(UUID playerUuid) throws Exception {
        String currentMonth = HstTime.currentMonthKey();
        String priorMonth = HstTime.previousMonthKey();
        Instant monthStart = HstTime.monthStartUtc(currentMonth);
        Instant monthEnd = HstTime.monthEndUtc(currentMonth);

        TreasuryLedgerStore.LedgerTotals month = ledger.totalsBetween(monthStart, monthEnd);
        TreasuryLedgerStore.LedgerTotals allTime = ledger.totalsAllTime();
        double ledgerMintGross = ledger.totalGoldMinedGross();
        double grantOutflowsPostReset = ledger.totalGrantOutflows();
        double loanRepaymentsAllTime = ledger.totalLoanRepaymentsAllTime();
        double walletGold = economy.sumCirculatingNotes();
        double notesRetiredG = ledger.totalNoteBurnRetired();
        double notesRetiredDonationG = ledger.totalDonationBurnRetired();
        double overIssueShortfallRepaidG = ledger.totalOverIssueShortfallRepaid();
        double taxMiscreditedG = ledger.totalTaxMiscreditedSinceJuly();
        double avgMonthlyNet = ledger.averageMonthlyNetPool();
        double reserveBalance = EconomyBaseline.headlineReserveBalance(allTime.net(), overIssueShortfallRepaidG);
        double totalGoldMined = EconomyBaseline.totalGoldMined(ledgerMintGross);
        double totalOverPrintedGrants = EconomyBaseline.totalOverPrintedGrants(grantOutflowsPostReset);

        TreasuryLedgerStore.LedgerTotals priorMonthTotals =
                ledger.totalsBetween(HstTime.monthStartUtc(priorMonth), HstTime.monthEndUtc(priorMonth));

        PlayerMonthStatus playerInfo = null;
        if (playerUuid != null) {
            long seconds = 0;
            try {
                seconds = playtime.monthlySeconds(playerUuid, currentMonth);
            } catch (Exception ignored) {
                // Playtime schema mismatch must not break /reserve balance display.
            }
            double taxMtd = ledger.playerTaxPaidSince(playerUuid, monthStart);
            playerInfo = new PlayerMonthStatus(seconds, taxMtd);
        }

        return new ReserveSnapshot(
                reserveBalance,
                currentMonth,
                priorMonth,
                month,
                priorMonthTotals,
                allTime,
                ledgerMintGross,
                grantOutflowsPostReset,
                loanRepaymentsAllTime,
                walletGold,
                totalGoldMined,
                notesRetiredG,
                notesRetiredDonationG,
                taxMiscreditedG,
                overIssueShortfallRepaidG,
                totalOverPrintedGrants,
                EconomyBaseline.MAP_262_PRE_RESET_RESERVE,
                avgMonthlyNet,
                playerInfo);
    }

    private static double roundMoney(double value) {
        return GoldMoney.round(value);
    }

    public record ReserveSnapshot(
            double reserveBalance,
            String currentMonthKey,
            String priorMonthKey,
            TreasuryLedgerStore.LedgerTotals currentMonthTotals,
            TreasuryLedgerStore.LedgerTotals priorMonthTotals,
            TreasuryLedgerStore.LedgerTotals allTimeTotals,
            double ledgerMintGross,
            double grantsOverPrintedPostReset,
            double loanRepaymentsAllTime,
            double walletGold,
            double totalGoldMined,
            double notesRetiredG,
            double notesRetiredDonationG,
            double taxMiscreditedG,
            double overIssueShortfallRepaidG,
            double totalOverPrintedGrants,
            double preJuly262ReserveCarryover,
            double averageMonthlyNet,
            PlayerMonthStatus player) {

        /** Post-reset reserve ledger net (inflows âˆ’ outflows). */
        public double postResetLedgerNet() {
            return roundMoney(allTimeTotals.net());
        }

        /** Gross ledger headline (before shortfall settlement). */
        public double grossReserveBalance() {
            return EconomyBaseline.trueReserveBalance(allTimeTotals.net());
        }

        /** Mint backing vs circulating Notes. Reserve display = backing − circulation. */
        public EconomyBaseline.NoteSupplySnapshot noteSupply() {
            return EconomyBaseline.computeNoteSupply(
                    totalGoldMined,
                    walletGold,
                    0,
                    notesRetiredG,
                    notesRetiredDonationG,
                    taxMiscreditedG,
                    overIssueShortfallRepaidG);
        }

        /** Backing − circulating Notes (negative when over-issued). */
        public double reserveUtilization() {
            return noteSupply().reserveNotesG();
        }
    }

    public record PlayerMonthStatus(long monthlyPlaytimeSeconds, double taxPaidMtd) {}

    public static String labelType(TreasuryLedgerType type) {
        return switch (type) {
            case TAX -> "Transaction Taxes";
            case VOTE -> "Vote Rewards";
            case PLAYTIME -> "Playtime Rewards";
            case DEATH -> "Death fees";
            case TOWNY_SINK -> "Server fees (Towny)";
            case LOAN_PRINCIPAL -> "Loan principal";
            case LOAN_INTEREST -> "Loan interest";
            case GRANT -> "Grants";
            case DIVIDEND -> "Treasury payouts";
            case LOAN_DISBURSE -> "Loan disbursements";
            case DONATION -> "Reserve donations (/pay reserve)";
            case NOTE_BURN -> "Notes retired (tax, /mint gold)";
            case BOND_ISSUE -> "Bond issuance";
            case BOND_COUPON -> "Bond coupon payouts";
            case BOND_COUPON_FORFEIT -> "Unclaimed bond coupons returned";
            case BOND_REDEEM -> "Bond redemptions";
            case OPENING -> "Opening balance (pre-ledger)";
            case OTHER -> "Other";
        };
    }

    /** Hidden from public /reserve breakdown (staff payouts are audited separately). */
    public static boolean showInPublicBreakdown(TreasuryLedgerType type) {
        return type != TreasuryLedgerType.GRANT;
    }
}
