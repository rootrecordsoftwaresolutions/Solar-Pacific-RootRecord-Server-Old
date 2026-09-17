package com.rootrecord.minecraft.common;

import java.util.UUID;

/** Shared economy service for RootRecord plugins without Vault hard dependency. */
public interface RootMcEconomyService {

    double balance(UUID playerId);

    /** Lookup by account id; {@code accountName} is required for town/nation/claim banks. */
    default double balance(UUID accountId, String accountName) {
        return balance(accountId);
    }

    boolean has(UUID playerId, double amount);

    default boolean has(UUID accountId, String accountName, double amount) {
        return balance(accountId, accountName) >= amount;
    }

    boolean withdraw(UUID playerId, double amount);

    /** Withdraw from a named bank account (town / nation / claim). */
    default boolean withdrawAccount(UUID accountId, String accountName, double amount) {
        return withdraw(accountId, amount);
    }

    void deposit(UUID playerId, double amount);

    /** Deposit into a named bank account (town / nation / claim) - no loan income sweep. */
    default void depositAccount(UUID accountId, String accountName, double amount) {
        deposit(accountId, amount);
    }

    /** Deposit player income; loan plugins may sweep part toward repayment before crediting wallet. */
    default void depositIncome(UUID playerId, double amount) {
        deposit(playerId, amount);
    }

    /**
     * Closed-loop service fee: gold already withdrawn from the payer → Server Reserve
     * (transaction tax + sink). Default no-op when economy host has no treasury.
     */
    default void sinkServiceFee(UUID payerId, String payerName, double amount, String channel) {}
}
