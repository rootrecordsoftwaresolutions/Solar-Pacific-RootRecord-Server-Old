package com.rootrecord.minecraft.rootbonds.towny;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Resolves whether a player may manage town/nation auto-bond settings in /bonds. */
public final class TownyLeadership {

    public record TownRole(String name, UUID bankUuid, String bankName) {}

    public record NationRole(String name, UUID bankUuid, String bankName) {}

    private TownyLeadership() {}

    public static Optional<TownRole> mayorTown(Player player) {
        if (player == null || !TownyReflection.isAvailable()) {
            return Optional.empty();
        }
        Object resident = TownyReflection.resident(player);
        Object town = TownyReflection.invokeNoArg(resident, "getTownOrNull", "getTown");
        if (town == null) {
            return Optional.empty();
        }
        UUID mayor = residentUuid(TownyReflection.invokeNoArg(town, "getMayor"));
        if (mayor == null || !mayor.equals(player.getUniqueId())) {
            return Optional.empty();
        }
        String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName"));
        if (name == null) {
            return Optional.empty();
        }
        Object account = TownyReflection.invokeNoArg(town, "getAccount");
        UUID bankUuid = TownyReflection.accountUuid(account);
        String bankName = TownyReflection.accountName(account);
        if (bankName == null || bankName.isBlank()) {
            bankName = "town-" + name;
        }
        if (bankUuid == null) {
            bankUuid = UUID.nameUUIDFromBytes(("town:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return Optional.of(new TownRole(name, bankUuid, bankName));
    }

    public static Optional<NationRole> kingNation(Player player) {
        if (player == null || !TownyReflection.isAvailable()) {
            return Optional.empty();
        }
        Object resident = TownyReflection.resident(player);
        Object nation = TownyReflection.invokeNoArg(resident, "getNationOrNull", "getNation");
        if (nation == null) {
            Object town = TownyReflection.invokeNoArg(resident, "getTownOrNull", "getTown");
            nation = TownyReflection.invokeNoArg(town, "getNationOrNull", "getNation");
        }
        if (nation == null) {
            return Optional.empty();
        }
        UUID king = residentUuid(TownyReflection.invokeNoArg(nation, "getKing"));
        if (king == null || !king.equals(player.getUniqueId())) {
            return Optional.empty();
        }
        String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
        if (name == null) {
            return Optional.empty();
        }
        Object account = TownyReflection.invokeNoArg(nation, "getAccount");
        UUID bankUuid = TownyReflection.accountUuid(account);
        String bankName = TownyReflection.accountName(account);
        if (bankName == null || bankName.isBlank()) {
            bankName = "nation-" + name;
        }
        if (bankUuid == null) {
            bankUuid = UUID.nameUUIDFromBytes(("nation:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return Optional.of(new NationRole(name, bankUuid, bankName));
    }

    private static UUID residentUuid(Object resident) {
        return TownyReflection.uuidOrNull(resident);
    }
}
