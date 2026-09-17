package com.rootrecord.minecraft.rootessentials.towny;

import com.rootrecord.minecraft.rootessentials.data.EconomySystemAccounts;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Resolve Towny closed-economy bank UUIDs without calling {@code Government.getAccount()} (Vault recursion). */
public final class TownyEconomyAccounts {

    public record VaultAccount(UUID uuid, String username) {}

    private static final long UUID_INDEX_TTL_MS = 60_000L;
    private static volatile long uuidIndexAtMs;
    private static volatile Map<UUID, VaultAccount> uuidIndex = Map.of();

    private TownyEconomyAccounts() {}

    public static VaultAccount resolve(String nameHint, OfflinePlayer player) {
        String name = normalizeName(nameHint);
        if (name == null && player != null) {
            name = normalizeName(player.getName());
        }
        if (name == null) {
            UUID uuid = player != null ? player.getUniqueId() : null;
            VaultAccount byUuid = uuid == null ? null : holderByUuid(uuid).orElse(null);
            if (byUuid != null) {
                return byUuid;
            }
            return new VaultAccount(uuid, "player");
        }

        if (EconomySystemAccounts.isTownyServerAccount(
                player != null ? player.getUniqueId() : null, name)) {
            return new VaultAccount(
                    EconomySystemAccounts.townyServerUuidValue(),
                    EconomySystemAccounts.townyServerUsername());
        }

        if (EconomySystemAccounts.isTownBankAccount(name)) {
            Optional<VaultAccount> town = townBankByName(EconomySystemAccounts.stripTownPrefix(name));
            if (town.isPresent()) {
                return town.get();
            }
            return offlineFallback(name);
        }

        if (EconomySystemAccounts.isNationBankAccount(name)) {
            Optional<VaultAccount> nation = nationBankByName(EconomySystemAccounts.stripNationPrefix(name));
            if (nation.isPresent()) {
                return nation.get();
            }
            return offlineFallback(name);
        }

        UUID uuid = player != null ? player.getUniqueId() : Bukkit.getOfflinePlayer(name).getUniqueId();
        Optional<VaultAccount> holder = holderByUuid(uuid);
        if (holder.isPresent()) {
            return holder.get();
        }
        return new VaultAccount(uuid, name);
    }

    public static Optional<VaultAccount> holderByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        VaultAccount hit = uuidIndex().get(uuid);
        return hit == null ? Optional.empty() : Optional.of(hit);
    }

    /** Username stored in MySQL — never leave town/nation banks as placeholder {@code player}. */
    public static String canonicalStoredUsername(UUID uuid, String username) {
        if (uuid != null) {
            if (EconomySystemAccounts.isTownyServerAccount(uuid, username)) {
                return EconomySystemAccounts.townyServerUsername();
            }
            Optional<VaultAccount> holder = holderByUuid(uuid);
            if (holder.isPresent()) {
                return holder.get().username();
            }
        }
        if (username == null || username.isBlank()) {
            return "player";
        }
        return username.trim();
    }

    public static Optional<VaultAccount> townBankByName(String townName) {
        if (townName == null || townName.isBlank()) {
            return Optional.empty();
        }
        Object town = invokeTowny("getTown", townName.trim());
        return vaultAccountFromHolder(
                town,
                EconomySystemAccounts.townBankUsername(townName.trim()),
                true);
    }

    public static Optional<VaultAccount> nationBankByName(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return Optional.empty();
        }
        Object nation = invokeTowny("getNation", nationName.trim());
        return vaultAccountFromHolder(
                nation,
                EconomySystemAccounts.nationBankUsername(nationName.trim()),
                false);
    }

    /**
     * Read town/nation UUID from the holder object only — never {@code getAccount()} (that re-enters Vault).
     */
    private static Optional<VaultAccount> vaultAccountFromHolder(
            Object holder,
            String bankUsername,
            boolean town) {
        if (holder == null || bankUsername == null || bankUsername.isBlank()) {
            return Optional.empty();
        }
        UUID uuid = uuidOrNull(invokeNoArg(
                holder,
                town ? "getUUID" : "getUUID",
                town ? "getTownUUID" : "getNationUUID"));
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.of(new VaultAccount(uuid, bankUsername));
    }

    private static VaultAccount offlineFallback(String prefixedName) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(prefixedName);
        return new VaultAccount(offline.getUniqueId(), prefixedName);
    }

    private static Map<UUID, VaultAccount> uuidIndex() {
        long now = System.currentTimeMillis();
        if (now - uuidIndexAtMs < UUID_INDEX_TTL_MS && !uuidIndex.isEmpty()) {
            return uuidIndex;
        }
        Map<UUID, VaultAccount> rebuilt = new HashMap<>();
        Object api = townyApi();
        if (api != null) {
            indexHolders(rebuilt, invokeNoArg(api, "getTowns"), true);
            indexHolders(rebuilt, invokeNoArg(api, "getNations"), false);
        }
        uuidIndex = Map.copyOf(rebuilt);
        uuidIndexAtMs = now;
        return uuidIndex;
    }

    private static void indexHolders(Map<UUID, VaultAccount> out, Object holders, boolean town) {
        if (holders == null) {
            return;
        }
        for (Object holder : asCollection(holders)) {
            if (holder == null) {
                continue;
            }
            String displayName = stringOrNull(invokeNoArg(holder, "getName"));
            if (displayName == null) {
                continue;
            }
            String username = town
                    ? EconomySystemAccounts.townBankUsername(displayName)
                    : EconomySystemAccounts.nationBankUsername(displayName);
            vaultAccountFromHolder(holder, username, town).ifPresent(account -> out.put(account.uuid(), account));
        }
    }

    private static Iterable<?> asCollection(Object raw) {
        if (raw instanceof Iterable<?> iterable) {
            return iterable;
        }
        return java.util.List.of();
    }

    private static String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Object invokeTowny(String method, String arg) {
        Object api = townyApi();
        if (api == null) {
            return null;
        }
        try {
            return api.getClass().getMethod(method, String.class).invoke(api, arg);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static Object townyApi() {
        if (TownyReflection.townyPlugin() == null) {
            return null;
        }
        try {
            Class<?> apiClass = TownyReflection.loadClass("com.palmergames.bukkit.towny.TownyAPI");
            return apiClass.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String... methods) {
        if (target == null) {
            return null;
        }
        for (String method : methods) {
            try {
                return target.getClass().getMethod(method).invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // try next
            }
        }
        return null;
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static UUID uuidOrNull(Object raw) {
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
