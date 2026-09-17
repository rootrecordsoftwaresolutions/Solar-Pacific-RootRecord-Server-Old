package com.rootrecord.minecraft.rootrewards.data;

/** Result of inserting a vote row (totals + auto-increment id for token idempotency). */
public record VoteInsert(VoteTotals totals, long voteId) {
    public static VoteInsert empty() {
        return new VoteInsert(VoteTotals.empty(), -1L);
    }
}
