package com.rootrecord.minecraft.rootessentials.treasury;


import com.rootrecord.minecraft.common.GoldMoney;

/** Paper 26.2 fresh-map economy baseline (2026-07-01 00:00 HST). */
public final class EconomyBaseline {

    /** Server Reserve ledger net at June 2026 month-end Ã¢â‚¬â€ carryover, not gold mined. */
    public static final double MAP_262_PRE_RESET_RESERVE = 2097.43;

    /** June 2026 staff/treasury GRANT outflows on the old map (over-printed). */
    public static final double MAP_262_PRE_RESET_GRANTS_OVER_PRINTED = 4400.0;

    /** June 2026 Activity Dividend returned at the July boundary. */
    public static final double MAP_262_JUNE_DIVIDEND_RETURNED = 1048.72;

    /** Reserve after June dividend return Ã¢â‚¬â€ opens the post-reset ledger era. */
    public static final double MAP_262_TRUE_RESERVE_OPENING =
            roundMoney(MAP_262_PRE_RESET_RESERVE - MAP_262_JUNE_DIVIDEND_RETURNED + 0.52);

    /**
     * Opening carryover baked into /reserve Balance + Notes. Towny Paper 26.2 default;
     * Claims fresh wipe sets {@code economy.true-reserve-opening: 0}.
     */
    private static volatile double openingCarryoverG = MAP_262_TRUE_RESERVE_OPENING;

    public static void setOpeningCarryoverG(double openingG) {
        openingCarryoverG = roundMoney(openingG);
    }

    public static double openingCarryoverG() {
        return openingCarryoverG;
    }

    public record SupplyIntegrityCheck(
            double julyOpeningG,
            double currentReserveG,
            double reserveDeltaG,
            double goldMinedMintG,
            boolean zeroMintWithReserveDrop,
            String status,
            String summary) {}

    /** Reserve-issued G (Notes) vs commodity gold mined via /mint. */
    public record NoteSupplySnapshot(
            double goldMinedG,
            double playerNotesG,
            double reserveNotesG,
            double totalNotesG,
            double overIssueG,
            double surplusMintHeadroomG,
            Double backingRatio,
            Double backingPct,
            String status,
            String summary,
            double notesRetiredG,
            double notesRetiredDonationG,
            double backingG,
            double taxMiscreditedG) {

        /** Over-issued Notes Ãƒ -  total Notes Ã¢â‚¬â€ input for dynamic transaction tax. */
        public double inflationPressureRatio() {
            if (totalNotesG <= 0.01) {
                return 0;
            }
            return roundMoney(Math.max(0, overIssueG) / totalNotesG);
        }

        public double inflationPressurePct() {
            return roundMoney(inflationPressureRatio() * 100.0);
        }

        public boolean overIssued() {
            return overIssueG > 0.01;
        }

        /** Unbacked share (100% Ã¢Ë†' backing) Ã¢â‚¬â€ tax falls as backing rises; 0% when fully backed. */
        public double backingShortfallRate() {
            if (!overIssued() || backingRatio == null) {
                return 0;
            }
            return roundMoney(Math.max(0, 1.0 - backingRatio));
        }

        public double backingShortfallPct() {
            return roundMoney(backingShortfallRate() * 100.0);
        }

        /** Dynamic tax = shortfall Ãƒ -  factor (factor applied in {@link TreasuryManager}). */
        public double dynamicTaxRate(double shortfallFactor) {
            if (!overIssued()) {
                return 0;
            }
            double factor = Math.max(0, Math.min(1, shortfallFactor));
            return roundMoney(backingShortfallRate() * factor);
        }

        public double dynamicTaxPct(double shortfallFactor) {
            return roundMoney(dynamicTaxRate(shortfallFactor) * 100.0);
        }
    }

    private EconomyBaseline() {}

    /** Reserve vs July opening when post-reset /mint has not yet offset outflows. */
    public static SupplyIntegrityCheck supplyIntegrity(double postResetLedgerNet, double postResetMintG) {
        double opening = openingCarryoverG;
        double ledgerNet = roundMoney(postResetLedgerNet);
        double currentReserve = trueReserveBalance(ledgerNet);
        double delta = ledgerNet;
        double mint = roundMoney(Math.max(0, postResetMintG));
        boolean zeroMintDrop = mint < 0.01 && currentReserve < opening - 0.01;
        String status = zeroMintDrop ? "reserve_below_opening_no_mint" : "ok";
        String summary = zeroMintDrop
                ? "Reserve is "
                        + roundMoney(opening - currentReserve)
                        + " G below July opening with no post-reset /mint Ã¢â‚¬â€ outflows exceed carryover."
                : "Reserve and July mining metrics within expected bounds.";
        return new SupplyIntegrityCheck(opening, currentReserve, delta, mint, zeroMintDrop, status, summary);
    }

    /** Physical gold mined via /mint (post-reset ledger gross). */
    public static double totalGoldMined(double postResetLedgerMintGross) {
        return roundMoney(Math.max(0, postResetLedgerMintGross));
    }

    /** @deprecated Alias for {@link #totalGoldMined}. */
    public static double totalGoldMinted(
            double walletGold,
            double reserveBalance,
            double postResetLedgerMint,
            double loanRepaymentsAllTime) {
        return totalGoldMined(postResetLedgerMint);
    }

    public static double totalOverPrintedGrants(double postResetGrantOutflows) {
        return roundMoney(
                MAP_262_PRE_RESET_GRANTS_OVER_PRINTED + Math.max(0, postResetGrantOutflows));
    }

    /** July opening carryover + post-reset reserve ledger net (gross ledger headline). */
    public static double trueReserveBalance(double postResetLedgerNet) {
        return roundMoney(openingCarryoverG + roundMoney(postResetLedgerNet));
    }

    /** Player-facing reserve Ã¢â‚¬â€ gross headline minus over-issue shortfall settlements. */
    public static double headlineReserveBalance(double postResetLedgerNet, double overIssueShortfallRepaidG) {
        return roundMoney(
                Math.max(0, trueReserveBalance(postResetLedgerNet) - roundMoney(overIssueShortfallRepaidG)));
    }

    public static NoteSupplySnapshot computeNoteSupply(
            double goldMinedG, double playerWalletG, double reserveG) {
        return computeNoteSupply(goldMinedG, playerWalletG, reserveG, 0, 0, 0);
    }

    /**
     * Circulating Notes = player + town + nation wallets (pass as {@code playerWalletG}).
     * Mint backing = {@code /mint hand|all} + gold-ore Notes gross (not raw gold items).
     * Displayed Reserve = backing − circulation (negative when over-issued).
     * {@code reserveG} is unused (kept for call-site compat).
     */
    public static NoteSupplySnapshot computeNoteSupply(
            double goldMinedG,
            double playerWalletG,
            double reserveG,
            double notesRetiredG,
            double notesRetiredDonationG,
            double taxMiscreditedG) {
        return computeNoteSupply(
                goldMinedG, playerWalletG, reserveG, notesRetiredG, notesRetiredDonationG, taxMiscreditedG, 0);
    }

    public static NoteSupplySnapshot computeNoteSupply(
            double goldMinedG,
            double playerWalletG,
            double reserveG,
            double notesRetiredG,
            double notesRetiredDonationG,
            double taxMiscreditedG,
            double overIssueShortfallRepaidG) {
        double mined = roundMoney(Math.max(0, goldMinedG));
        double backing = mined;
        double playerNotes = roundMoney(Math.max(0, playerWalletG));
        double totalNotes = playerNotes;
        double reserveNotes = roundMoney(backing - totalNotes);
        double notesRetired = roundMoney(Math.max(0, notesRetiredG));
        double notesRetiredDonation = roundMoney(Math.max(0, notesRetiredDonationG));
        double taxMiscredited = roundMoney(Math.max(0, taxMiscreditedG));
        double overIssue = roundMoney(Math.max(0, totalNotes - backing));
        double surplusMint = roundMoney(Math.max(0, backing - totalNotes));
        Double backingRatio = totalNotes > 0.01 ? roundMoney(backing / totalNotes) : null;
        Double backingPct = backingRatio != null ? roundMoney(backingRatio * 100.0) : null;
        String status = overIssue > 0.01 ? "over_issued" : "fully_backed";

        String summary;
        if (overIssue > 0.01) {
            summary = String.format(
                    java.util.Locale.US,
                    "%.3f G unbacked Notes remain (%.3f G /mint backing vs %.3f G circulating).%s%s "
                            + "Map-merge carryover, grants, votes, and dividends issued Notes without /mint backing.",
                    overIssue,
                    backing,
                    totalNotes,
                    notesRetired > 0.01
                            ? String.format(java.util.Locale.US, " %.3f G overrun paid down.", notesRetired)
                            : "",
                    notesRetiredDonation > 0.01
                            ? String.format(
                                    java.util.Locale.US,
                                    " %.3f G legacy /pay reserve burns (pre-credit).",
                                    notesRetiredDonation)
                            : "");
        } else {
            summary = String.format(
                    java.util.Locale.US,
                    "Notes outstanding (%.3f G) are covered by %.3f G /mint backing since map opening.",
                    totalNotes,
                    backing);
        }

        return new NoteSupplySnapshot(
                mined,
                playerNotes,
                reserveNotes,
                totalNotes,
                overIssue,
                surplusMint,
                backingRatio,
                backingPct,
                status,
                summary,
                notesRetired,
                notesRetiredDonation,
                backing,
                taxMiscredited);
    }

    /**
     * Activity Dividend pool: monthly reserve net share plus surplus mint headroom refund when gold mined exceeds Notes.
     */
    public static double dividendRefundPool(
            double monthlyLedgerNet, NoteSupplySnapshot supply, double payoutRatio) {
        if (monthlyLedgerNet < -0.01) {
            return 0;
        }
        double ratio = Math.max(0.0, Math.min(1.0, payoutRatio));
        double base = roundMoney(Math.max(0, monthlyLedgerNet) * ratio);
        double surplus = supply.surplusMintHeadroomG();
        if (surplus > 0.01) {
            return roundMoney(base + surplus * ratio);
        }
        return base;
    }

    private static double roundMoney(double value) {
        return GoldMoney.round(value);
    }
}
