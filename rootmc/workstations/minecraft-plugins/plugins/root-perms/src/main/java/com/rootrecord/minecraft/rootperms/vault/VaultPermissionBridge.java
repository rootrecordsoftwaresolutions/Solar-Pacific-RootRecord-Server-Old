package com.rootrecord.minecraft.rootperms.vault;

import com.rootrecord.minecraft.rootperms.service.PermsServiceImpl;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.Locale;
import java.util.UUID;

/** Vault Permission bridge — register only when Vault.jar is present (Towny hosts). */
public final class VaultPermissionBridge extends Permission {

    private final Plugin plugin;
    private final PermsServiceImpl perms;

    public VaultPermissionBridge(Plugin plugin, PermsServiceImpl perms) {
        this.plugin = plugin;
        this.perms = perms;
    }

    public static boolean tryRegister(Plugin plugin, PermsServiceImpl perms) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        try {
            VaultPermissionBridge bridge = new VaultPermissionBridge(plugin, perms);
            Bukkit.getServicesManager().register(Permission.class, bridge, plugin, ServicePriority.Highest);
            plugin.getLogger().info("Vault Permission bridge registered (Towny / third-party).");
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("Vault Permission bridge failed: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public String getName() {
        return "Root-Perms";
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public boolean hasSuperPermsCompat() {
        return true;
    }

    @Override
    public boolean playerHas(String world, String player, String permission) {
        UUID uuid = resolveUuid(player);
        return uuid != null && perms.has(uuid, permission);
    }

    @Override
    public boolean playerAdd(String world, String player, String permission) {
        UUID uuid = resolveUuid(player);
        if (uuid == null || permission == null) {
            return false;
        }
        try {
            perms.store().addUserNode(
                    uuid,
                    com.rootrecord.minecraft.rootperms.data.PermsStore.SCOPE_GLOBAL,
                    com.rootrecord.minecraft.rootperms.data.PermsStore.KIND_PERMISSION,
                    permission.toLowerCase(Locale.ROOT));
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                perms.refresh(online);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean playerRemove(String world, String player, String permission) {
        UUID uuid = resolveUuid(player);
        if (uuid == null || permission == null) {
            return false;
        }
        try {
            perms.store().removeUserNode(
                    uuid,
                    com.rootrecord.minecraft.rootperms.data.PermsStore.SCOPE_GLOBAL,
                    com.rootrecord.minecraft.rootperms.data.PermsStore.KIND_PERMISSION,
                    permission.toLowerCase(Locale.ROOT));
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                perms.refresh(online);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean groupHas(String world, String group, String permission) {
        try {
            return perms.store().groupNodes(group).stream()
                    .anyMatch(n -> "permission".equals(n.kind())
                            && n.value().equalsIgnoreCase(permission));
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean groupAdd(String world, String group, String permission) {
        return false;
    }

    @Override
    public boolean groupRemove(String world, String group, String permission) {
        return false;
    }

    @Override
    public boolean playerInGroup(String world, String player, String group) {
        UUID uuid = resolveUuid(player);
        return uuid != null && perms.hasGroup(uuid, group);
    }

    @Override
    public boolean playerAddGroup(String world, String player, String group) {
        UUID uuid = resolveUuid(player);
        return uuid != null && perms.grantGroup(uuid, group);
    }

    @Override
    public boolean playerRemoveGroup(String world, String player, String group) {
        UUID uuid = resolveUuid(player);
        return uuid != null && perms.revokeGroup(uuid, group);
    }

    @Override
    public String[] getPlayerGroups(String world, String player) {
        UUID uuid = resolveUuid(player);
        if (uuid == null) {
            return new String[0];
        }
        return perms.groupsOf(uuid).toArray(String[]::new);
    }

    @Override
    public String getPrimaryGroup(String world, String player) {
        UUID uuid = resolveUuid(player);
        if (uuid == null) {
            return perms.config().defaultGroup();
        }
        try {
            return perms.store().primaryGroup(uuid);
        } catch (Exception ex) {
            return perms.config().defaultGroup();
        }
    }

    @Override
    public String[] getGroups() {
        return perms.allGroupIds();
    }

    @Override
    public boolean hasGroupSupport() {
        return true;
    }

    private static UUID resolveUuid(String player) {
        if (player == null || player.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(player);
        if (online != null) {
            return online.getUniqueId();
        }
        return Bukkit.getOfflinePlayer(player).getUniqueId();
    }
}
