package com.rootrecord.minecraft.rootmemberships;

import com.rootrecord.minecraft.common.RootMcPermsService;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Grants or revokes a configured permission group only (does not touch rank track).
 */
public final class MembershipGroupSync {

    private MembershipGroupSync() {}

    public static void apply(Player player, String group, boolean active) {
        if (player == null || group == null || group.isBlank()) {
            return;
        }
        String g = group.trim();
        if (applyRootPerms(player, g, active)) {
            return;
        }
        applyVault(player, g, active);
    }

    private static boolean applyRootPerms(Player player, String group, boolean active) {
        RegisteredServiceProvider<RootMcPermsService> rsp =
                Bukkit.getServicesManager().getRegistration(RootMcPermsService.class);
        if (rsp == null || rsp.getProvider() == null) {
            return false;
        }
        RootMcPermsService perms = rsp.getProvider();
        boolean has = perms.hasGroup(player.getUniqueId(), group);
        if (active && !has) {
            perms.grantGroup(player.getUniqueId(), group);
        } else if (!active && has) {
            perms.revokeGroup(player.getUniqueId(), group);
        }
        return true;
    }

    private static void applyVault(Player player, String group, boolean active) {
        RegisteredServiceProvider<Permission> rsp =
                Bukkit.getServicesManager().getRegistration(Permission.class);
        if (rsp == null || rsp.getProvider() == null) {
            return;
        }
        Permission vault = rsp.getProvider();
        boolean has = vault.playerInGroup((String) null, player, group);
        if (active && !has) {
            vault.playerAddGroup((String) null, player, group);
        } else if (!active && has) {
            vault.playerRemoveGroup((String) null, player, group);
        }
    }
}
