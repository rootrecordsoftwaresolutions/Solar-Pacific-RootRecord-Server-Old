package com.rootrecord.minecraft.rootupkeep.towny;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TownyGroups {

    public record NamedGroup(String name, Set<UUID> memberUuids) {}

    private TownyGroups() {}

    public static List<NamedGroup> allTowns() {
        List<NamedGroup> towns = new ArrayList<>();
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return towns;
        }
        for (Object town : TownyReflection.asCollection(TownyReflection.invokeNoArg(api, "getTowns"))) {
            String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName"));
            if (name == null) {
                continue;
            }
            towns.add(new NamedGroup(name, residentsOfTown(town)));
        }
        return towns;
    }

    public static Set<UUID> residentsOfTownName(String townName) {
        Object town = TownyReflection.townByName(townName);
        return town == null ? Set.of() : residentsOfTown(town);
    }

    public static Set<UUID> residentsOfNationName(String nationName) {
        Object nation = TownyReflection.nationByName(nationName);
        if (nation == null) {
            return Set.of();
        }
        Set<UUID> members = new LinkedHashSet<>();
        for (Object town : TownyReflection.asCollection(TownyReflection.invokeNoArg(nation, "getTowns"))) {
            members.addAll(residentsOfTown(town));
        }
        return members;
    }

    public static List<NamedGroup> allNations() {
        List<NamedGroup> nations = new ArrayList<>();
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return nations;
        }
        for (Object nation : TownyReflection.asCollection(TownyReflection.invokeNoArg(api, "getNations"))) {
            String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
            if (name == null) {
                continue;
            }
            Set<UUID> members = new LinkedHashSet<>();
            for (Object town : TownyReflection.asCollection(TownyReflection.invokeNoArg(nation, "getTowns"))) {
                members.addAll(residentsOfTown(town));
            }
            nations.add(new NamedGroup(name, members));
        }
        return nations;
    }

    private static Set<UUID> residentsOfTown(Object town) {
        Set<UUID> uuids = new LinkedHashSet<>();
        for (Object resident : TownyReflection.asCollection(TownyReflection.invokeNoArg(town, "getResidents"))) {
            UUID uuid = TownyReflection.uuidOrNull(resident);
            if (uuid != null) {
                uuids.add(uuid);
            }
        }
        return uuids;
    }

    /**
     * Most recent login among members. Returns null when the group has no members.
     * If any member is online now, returns {@link Instant#now()}.
     */
    public static Instant groupLastActive(Set<UUID> members, Map<UUID, Instant> logins, int graceDays) {
        if (members == null || members.isEmpty()) {
            return null;
        }
        Instant now = Instant.now();
        Instant best = null;
        for (UUID uuid : members) {
            if (Bukkit.getPlayer(uuid) != null) {
                return now;
            }
            Instant login = resolveLogin(uuid, logins);
            if (login == null) {
                continue;
            }
            if (best == null || login.isAfter(best)) {
                best = login;
            }
        }
        return best;
    }

    public static boolean hasActiveMember(Set<UUID> members, Map<UUID, Instant> logins, int graceDays) {
        Instant last = groupLastActive(members, logins, graceDays);
        if (last == null) {
            return false;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(last, Instant.now());
        return days < graceDays;
    }

    private static Instant resolveLogin(UUID uuid, Map<UUID, Instant> logins) {
        Instant login = logins.get(uuid);
        if (login != null) {
            return login;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        long lastPlayed = offline.getLastPlayed();
        return lastPlayed > 0 ? Instant.ofEpochMilli(lastPlayed) : null;
    }
}
