package com.rootrecord.minecraft.common;

/** Result of routing player income through an active loan repayment sweep. */
public record RootMcIncomeSweepResult(double toWallet, double toLoanRepaid) {

    public static RootMcIncomeSweepResult allToWallet(double gross) {
        return new RootMcIncomeSweepResult(gross, 0);
    }
}
