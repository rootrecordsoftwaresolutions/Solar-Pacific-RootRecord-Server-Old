package com.rootrecord.minecraft.rootterritories.towny;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Online Towny residents for wilderness intrusion alerts. */
public final class TownyResidents {

    private TownyResidents() {}

    public static Set<Player> onlineTownResidents(String townName) {
        Set<Player> online = new LinkedHashSet<>();
        if (townName == null || townName.isBlank()) {
            return online;
        }
        Object town = townByName(townName);
        if (town == null) {
            return online;
        }
        for (Object resident : TownyReflection.asCollection(TownyReflection.invokeNoArg(town, "getResidents"))) {
            addOnline(online, resident);
        }
        return online;
    }

    public static Set<Player> onlineNationResidents(String nationName) {
        Set<Player> online = new LinkedHashSet<>();
        if (nationName == null || nationName.isBlank()) {
            return online;
        }
        Object nation = nationByName(nationName);
        if (nation == null) {
            return online;
        }
        for (Object town : TownyReflection.asCollection(TownyReflection.invokeNoArg(nation, "getTowns"))) {
            for (Object resident : TownyReflection.asCollection(TownyReflection.invokeNoArg(town, "getResidents"))) {
                addOnline(online, resident);
            }
        }
        return online;
    }

    public static boolean isResidentOfTown(Player player, String townName) {
        if (player == null || townName == null) {
            return false;
        }
        String playerTown = playerTownName(player);
        return playerTown != null && playerTown.equalsIgnoreCase(townName);
    }

    public static boolean isResidentOfNation(Player player, String nationName) {
        if (player == null || nationName == null) {
            return false;
        }
        String playerNation = playerNationName(player);
        return playerNation != null && playerNation.equalsIgnoreCase(nationName);
    }

    /** True if any town resident has {@code player} on their {@code /res friend} list. */
    public static boolean isFriendOfAnyTownResident(Player player, String townName) {
        Object actor = resident(player);
        Object town = townByName(townName);
        if (actor == null || town == null) {
            return false;
        }
        for (Object member : TownyReflection.asCollection(TownyReflection.invokeNoArg(town, "getResidents"))) {
            if (residentHasFriend(member, actor)) {
                return true;
            }
        }
        return false;
    }

    /** True if any nation member has {@code player} on their {@code /res friend} list. */
    public static boolean isFriendOfAnyNationResident(Player player, String nationName) {
        Object actor = resident(player);
        Object nation = nationByName(nationName);
        if (actor == null || nation == null) {
            return false;
        }
        for (Object town : TownyReflection.asCollection(TownyReflection.invokeNoArg(nation, "getTowns"))) {
            for (Object member : TownyReflection.asCollection(TownyReflection.invokeNoArg(town, "getResidents"))) {
                if (residentHasFriend(member, actor)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean residentHasFriend(Object resident, Object possibleFriend) {
        if (resident == null || possibleFriend == null) {
            return false;
        }
        Object has = TownyReflection.invoke(resident, "hasFriend", possibleFriend);
        return has instanceof Boolean bool && bool;
    }

    public static String playerTownName(Player player) {
        Object resident = resident(player);
        if (resident == null) {
            return null;
        }
        Object town = TownyReflection.invokeNoArg(resident, "getTown");
        return town == null ? null : TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName"));
    }

    public static String playerNationName(Player player) {
        Object resident = resident(player);
        if (resident == null) {
            return null;
        }
        Object town = TownyReflection.invokeNoArg(resident, "getTown");
        if (town == null) {
            return null;
        }
        Object nation = TownyReflection.invokeNoArg(town, "getNation", "getNationOrNull");
        return nation == null ? null : TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
    }

    public static String townNationName(String townName) {
        Object town = townByName(townName);
        if (town == null) {
            return null;
        }
        Object nation = TownyReflection.invokeNoArg(town, "getNation", "getNationOrNull");
        return nation == null ? null : TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
    }

    public static boolean areNationsAllied(String nationA, String nationB) {
        if (nationA == null || nationB == null || nationA.isBlank() || nationB.isBlank()) {
            return false;
        }
        if (nationA.equalsIgnoreCase(nationB)) {
            return false;
        }
        Object a = nationByName(nationA);
        Object b = nationByName(nationB);
        if (a == null || b == null) {
            return false;
        }
        Object allied = TownyReflection.invoke(a, "isAlly", b);
        if (allied instanceof Boolean bool) {
            return bool;
        }
        allied = TownyReflection.invoke(a, "hasAlly", b);
        if (allied instanceof Boolean bool) {
            return bool;
        }
        allied = TownyReflection.invoke(a, "isAlliedNation", b);
        return allied instanceof Boolean bool && bool;
    }

    private static Object resident(Player player) {
        Object api = TownyReflection.townyApi();
        if (api == null || player == null) {
            return null;
        }
        Object resident = invokeGetResident(api, player);
        if (resident != null) {
            return resident;
        }
        resident = invokeGetResident(api, player.getUniqueId());
        if (resident != null) {
            return resident;
        }
        return invokeGetResident(api, player.getName());
    }

    private static Object invokeGetResident(Object api, Object arg) {
        if (api == null || arg == null) {
            return null;
        }
        return TownyReflection.invoke(api, "getResident", arg);
    }

    private static Object townByName(String townName) {
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return null;
        }
        try {
            return api.getClass().getMethod("getTown", String.class).invoke(api, townName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object nationByName(String nationName) {
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return null;
        }
        try {
            return api.getClass().getMethod("getNation", String.class).invoke(api, nationName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addOnline(Set<Player> online, Object resident) {
        if (resident == null) {
            return;
        }
        Object playerObj = TownyReflection.invokeNoArg(resident, "getPlayer");
        if (playerObj instanceof Player player && player.isOnline()) {
            online.add(player);
            return;
        }
        Object uuidObj = TownyReflection.invokeNoArg(resident, "getUUID", "getUniqueId");
        if (uuidObj instanceof UUID uuid) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                online.add(player);
                return;
            }
        }
        String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(resident, "getName"));
        if (name != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (offline.isOnline() && offline.getPlayer() != null) {
                online.add(offline.getPlayer());
            }
        }
    }
}
