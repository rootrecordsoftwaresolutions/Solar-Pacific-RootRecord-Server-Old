package com.rootrecord.minecraft.rootterritories.nation;

import com.rootrecord.minecraft.rootterritories.geom.InfluenceGeometry;
import com.rootrecord.minecraft.rootterritories.model.PlotRect;
import com.rootrecord.minecraft.rootterritories.towny.TownyReflection;
import com.rootrecord.minecraft.rootterritories.towny.TownyTerritorySource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TownyTownAccess {

    private TownyTownAccess() {}

    public static boolean isAvailable() {
        return TownyReflection.isAvailable();
    }

    public static Set<String> allNationNames() {
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return Set.of();
        }
        Object nations = TownyReflection.invokeNoArg(api, "getNations");
        List<String> names = new ArrayList<>();
        for (Object nation : TownyReflection.asCollection(nations)) {
            String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(nation, "getName"));
            if (name != null) {
                names.add(name);
            }
        }
        return Set.copyOf(names);
    }

    public static Optional<Object> townByName(String townName) {
        if (townName == null || townName.isBlank()) {
            return Optional.empty();
        }
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return Optional.empty();
        }
        Object town = TownyReflection.invoke(api, "getTown", townName);
        return town == null ? Optional.empty() : Optional.of(town);
    }

    public static Optional<String> playerTownName(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        Object resident = resident(player);
        if (resident == null) {
            return Optional.empty();
        }
        Object town = TownyReflection.invokeNoArg(resident, "getTownOrNull", "getTown");
        return Optional.ofNullable(TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName")));
    }

    public static boolean isMayorOf(Player player, String townName) {
        if (player == null || townName == null || townName.isBlank()) {
            return false;
        }
        return townByName(townName)
                .map(town -> {
                    Object mayor = TownyReflection.invokeNoArg(town, "getMayor");
                    if (mayor == null) {
                        return false;
                    }
                    Object uuid = TownyReflection.invokeNoArg(mayor, "getUUID", "getUniqueId");
                    if (uuid instanceof UUID id) {
                        return id.equals(player.getUniqueId());
                    }
                    String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(mayor, "getName"));
                    return name != null && name.equalsIgnoreCase(player.getName());
                })
                .orElse(false);
    }

    public static boolean townHasNation(String townName) {
        return townByName(townName)
                .map(town -> TownyReflection.invokeNoArg(town, "getNationOrNull", "getNation") != null)
                .orElse(false);
    }

    public static boolean nationExists(String nationName) {
        if (nationName == null || nationName.isBlank()) {
            return false;
        }
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return false;
        }
        Object nation = TownyReflection.invoke(api, "getNation", nationName);
        return nation != null;
    }

    public static boolean townsTouch(Object townA, Object townB, double buffer) {
        List<PlotRect> a = TownyTerritorySource.plotsFromTown(townA);
        List<PlotRect> b = TownyTerritorySource.plotsFromTown(townB);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return InfluenceGeometry.regionsTouch(a, buffer, b, buffer);
    }

    public static List<String> normalizeTownList(Collection<String> raw) {
        return raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim())
                .distinct()
                .collect(Collectors.toList());
    }

    private static Object resident(Player player) {
        Object api = TownyReflection.townyApi();
        if (api == null) {
            return null;
        }
        return TownyReflection.invoke(api, "getResident", player);
    }

    public static String townBankAccountName(String townName) {
        return "town-" + townName;
    }
}
