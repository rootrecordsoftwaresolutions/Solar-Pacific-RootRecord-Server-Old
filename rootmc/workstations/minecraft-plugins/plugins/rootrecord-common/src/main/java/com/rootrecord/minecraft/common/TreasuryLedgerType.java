package com.rootrecord.minecraft.common;

/** Treasury ledger entry categories (MySQL + cloud sync). */
public enum TreasuryLedgerType {
    TAX,
    VOTE,
    /** Playtime milestone reward — loan income sweep applies. */
    PLAYTIME,
    DEATH,
    GRANT,
    DIVIDEND,
    TOWNY_SINK,
    LOAN_DISBURSE,
    LOAN_PRINCIPAL,
    LOAN_INTEREST,
    BOND_ISSUE,
    BOND_COUPON,
    /** Unclaimed bond coupon returned to reserve after claim window expires. */
    BOND_COUPON_FORFEIT,
    BOND_REDEEM,
    DONATION,
    /** Unbacked Notes destroyed (dynamic tax while over-issued) — no reserve credit. */
    NOTE_BURN,
    OTHER,
    OPENING
}
