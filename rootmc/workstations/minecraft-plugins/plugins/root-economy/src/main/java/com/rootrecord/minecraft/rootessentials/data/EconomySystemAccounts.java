package com.rootrecord.minecraft.rootessentials.data;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Vault accounts that are server sinks, not players - hide from /baltop. */
public final class EconomySystemAccounts {

    /** Server Reserve vault (legacy Towny closed_economy.server_account name: towny-server). */
    private static final String SERVER_RESERVE_USERNAME = "Server-Reserve";
    private static final String LEGACY_TOWNY_SERVER_USERNAME = "towny-server";
    private static final UUID SERVER_RESERVE_UUID =
            UUID.fromString("a73f39b0-1b7c-2930-b4a3-ce101812d926");
    /** Ava_Ivy — wallet is the Server Reserve (same vault, not a separate player balance). */
    public static final UUID AVA_IVY_UUID =
            UUID.fromString("78c3de61-0fd6-4800-9eda-cc178eaae34b");
    public static final String AVA_IVY_USERNAME = "Ava_Ivy";

    private static final Set<String> RESERVE_USERNAMES = Set.of(
            SERVER_RESERVE_USERNAME.toLowerCase(Locale.ROOT),
            LEGACY_TOWNY_SERVER_USERNAME,
            AVA_IVY_USERNAME.toLowerCase(Locale.ROOT),
            "ava");

    public static final String TOWN_BANK_PREFIX = "town-";
    public static final String NATION_BANK_PREFIX = "nation-";
    public static final String CLAIM_BANK_PREFIX = "claim-";

    private EconomySystemAccounts() {}

    public static String townyServerUuid() {
        return SERVER_RESERVE_UUID.toString();
    }

    public static UUID townyServerUuidValue() {
        return SERVER_RESERVE_UUID;
    }

    public static String townyServerUsername() {
        return SERVER_RESERVE_USERNAME;
    }

    public static boolean isAvaReserveAlias(UUID uuid, String username) {
        if (uuid != null && AVA_IVY_UUID.equals(uuid)) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        String lower = username.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return "ava_ivy".equals(lower) || "ava".equals(lower);
    }

    /** Ava_Ivy deposits/withdrawals hit the Server Reserve vault. */
    public static UUID resolveWalletUuid(UUID uuid, String username) {
        if (isAvaReserveAlias(uuid, username)) {
            return SERVER_RESERVE_UUID;
        }
        return uuid;
    }

    public static String resolveWalletUsername(UUID uuid, String username) {
        if (isAvaReserveAlias(uuid, username) || isTownyServerAccount(uuid, username)) {
            return SERVER_RESERVE_USERNAME;
        }
        return username;
    }

    public static boolean isTownyServerAccount(UUID uuid, String username) {
        if (uuid != null && (SERVER_RESERVE_UUID.equals(uuid) || AVA_IVY_UUID.equals(uuid))) {
            return true;
        }
        if (username == null) {
            return false;
        }
        return RESERVE_USERNAMES.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isExcludedFromBaltop(String username, UUID uuid) {
        if (uuid != null && (SERVER_RESERVE_UUID.equals(uuid) || AVA_IVY_UUID.equals(uuid))) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        String lower = username.toLowerCase(Locale.ROOT);
        if ("player".equals(lower)) {
            return true;
        }
        return RESERVE_USERNAMES.contains(lower)
                || lower.startsWith(TOWN_BANK_PREFIX)
                || lower.startsWith(NATION_BANK_PREFIX)
                || lower.startsWith(CLAIM_BANK_PREFIX);
    }

    public static boolean isTownBankAccount(String username) {
        return username != null && username.toLowerCase(Locale.ROOT).startsWith(TOWN_BANK_PREFIX);
    }

    public static boolean isNationBankAccount(String username) {
        return username != null && username.toLowerCase(Locale.ROOT).startsWith(NATION_BANK_PREFIX);
    }

    public static boolean isClaimBankAccount(String username) {
        return username != null && username.toLowerCase(Locale.ROOT).startsWith(CLAIM_BANK_PREFIX);
    }

    /** Town / nation / claim Camp banks - not player wallets. */
    public static boolean isBankAccount(String username) {
        return isTownBankAccount(username)
                || isNationBankAccount(username)
                || isClaimBankAccount(username);
    }

    public static String townBankUsername(String townName) {
        if (townName == null || townName.isBlank()) {
            return null;
        }
        return TOWN_BANK_PREFIX + sanitizeBankLeaf(townName);
    }

    public static String nationBankUsername(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return null;
        }
        return NATION_BANK_PREFIX + sanitizeBankLeaf(nationName);
    }

    /**
     * Towny / Vault bank leaves use underscores (Minecraft names can't contain spaces).
     * Always store and look up as {@code A_Town}, never {@code A Town}.
     */
    public static String sanitizeBankLeaf(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replace(' ', '_');
    }

    /** Dedupe key for /baltop towns|nations — spaces and underscores match. */
    public static String bankDisplayKey(String displayLeaf) {
        return sanitizeBankLeaf(displayLeaf).toLowerCase(Locale.ROOT);
    }

    public static String stripTownPrefix(String username) {
        if (username == null) {
            return "";
        }
        String lower = username.toLowerCase(Locale.ROOT);
        if (lower.startsWith(TOWN_BANK_PREFIX)) {
            return sanitizeBankLeaf(username.substring(TOWN_BANK_PREFIX.length()));
        }
        return sanitizeBankLeaf(username);
    }

    public static String stripNationPrefix(String username) {
        if (username == null) {
            return "";
        }
        String lower = username.toLowerCase(Locale.ROOT);
        if (lower.startsWith(NATION_BANK_PREFIX)) {
            return sanitizeBankLeaf(username.substring(NATION_BANK_PREFIX.length()));
        }
        return sanitizeBankLeaf(username);
    }
}
