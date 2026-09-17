package com.rootrecord.minecraft.rootrewards.data;

public record VoteTotals(int voteCount, double totalGold) {

    public static VoteTotals empty() {
        return new VoteTotals(0, 0.0);
    }
}
