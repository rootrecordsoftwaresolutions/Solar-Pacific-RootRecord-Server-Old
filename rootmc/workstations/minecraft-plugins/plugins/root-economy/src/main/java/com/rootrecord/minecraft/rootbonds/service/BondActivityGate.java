package com.rootrecord.minecraft.rootbonds.service;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.rootrecord.minecraft.common.InactivityRules;
import com.rootrecord.minecraft.rootbonds.towny.TownyGroups;

/** Same grace window as Root-Upkeep inactivity tax — active holders earn bond coupons. */
public final class BondActivityGate {

    private BondActivityGate() {}

    public static boolean isPlayerActive(UUID uuid, Map<UUID, Instant> logins, int graceDays) {
        if (uuid == null) {
            return false;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return true;
        }
        Instant last = resolveLogin(uuid, logins);
        if (last == null) {
            return false;
        }
        return ChronoUnit.DAYS.between(last, Instant.now()) < graceDays;
    }

    public static boolean isGroupActive(Set<UUID> members, Map<UUID, Instant> logins, int graceDays) {
        Instant last = TownyGroups.groupLastActive(members, logins, graceDays);
        return !InactivityRules.isSubjectToInactivityTax(last, graceDays);
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
