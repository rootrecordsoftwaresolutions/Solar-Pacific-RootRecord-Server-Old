package com.rootrecord.minecraft.rootperms.service;

import com.rootrecord.minecraft.common.RootMcPermsService;
import com.rootrecord.minecraft.rootperms.config.PermsConfig;
import com.rootrecord.minecraft.rootperms.data.PermsStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PermsServiceImpl implements RootMcPermsService {

    /** Vanilla / Paper commands that normally require operator. */
    private static final Set<String> VANILLA_OP_COMMANDS = Set.of(
            "op", "deop",
            "ban", "ban-ip", "pardon", "pardon-ip", "banlist",
            "whitelist", "kick",
            "stop", "restart",
            "save-all", "save-on", "save-off",
            "fill", "clone", "setblock", "summon", "give", "clear", "kill",
            "effect", "enchant", "experience", "xp", "gamemode", "tp", "teleport",
            "spreadplayers", "playsound", "stopsound",
            "title", "tellraw", "data", "datapack",
            "debug", "function", "forceload", "jfr",
            "locate", "loot", "particle", "perf", "place",
            "publish", "reload", "ride", "say", "schedule", "scoreboard",
            "seed", "setworldspawn", "spawnpoint", "spectate",
            "tag", "team", "tick", "trigger", "time", "weather", "difficulty",
            "attribute", "bossbar", "return", "send", "random", "gamerule",
            "worldborder", "execute", "advancement", "recipe", "item", "damage");

    private final JavaPlugin plugin;
    private final PermsConfig config;
    private final PermsStore store;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> groupCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> permCache = new ConcurrentHashMap<>();

    public PermsServiceImpl(JavaPlugin plugin, PermsConfig config, PermsStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
    }

    public PermsConfig config() {
        return config;
    }

    public PermsStore store() {
        return store;
    }

    public void clearCaches() {
        groupCache.clear();
        permCache.clear();
    }

    @Override
    public boolean ensureUser(UUID playerId, String username) {
        try {
            store.ensureUser(playerId, username);
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().warning("ensureUser failed: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasGroup(UUID playerId, String groupId) {
        if (playerId == null || groupId == null || groupId.isBlank()) {
            return false;
        }
        return groupsOf(playerId).contains(groupId.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean grantGroup(UUID playerId, String groupId) {
        if (playerId == null || groupId == null || groupId.isBlank()) {
            return false;
        }
        String id = groupId.toLowerCase(Locale.ROOT);
        try {
            store.ensureUser(playerId, Bukkit.getOfflinePlayer(playerId).getName());
            store.addUserNode(playerId, PermsStore.SCOPE_GLOBAL, PermsStore.KIND_GROUP, id);
            store.setPrimaryGroup(playerId, higherPrimary(playerId, id));
            invalidate(playerId);
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                refresh(online);
            }
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "grantGroup failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    private String higherPrimary(UUID playerId, String newlyGranted) throws SQLException {
        List<String> track = config.playerTrack();
        int newIdx = track.indexOf(newlyGranted);
        String current = store.primaryGroup(playerId);
        int curIdx = track.indexOf(current.toLowerCase(Locale.ROOT));
        if (newIdx >= 0 && newIdx >= curIdx) {
            return newlyGranted;
        }
        return current;
    }

    @Override
    public boolean revokeGroup(UUID playerId, String groupId) {
        if (playerId == null || groupId == null || groupId.isBlank()) {
            return false;
        }
        String id = groupId.toLowerCase(Locale.ROOT);
        try {
            store.removeUserNode(playerId, PermsStore.SCOPE_GLOBAL, PermsStore.KIND_GROUP, id);
            invalidate(playerId);
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                refresh(online);
            }
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().warning("revokeGroup failed: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public int highestTrackIndex(UUID playerId, List<String> trackOrder) {
        if (trackOrder == null || trackOrder.isEmpty()) {
            return -1;
        }
        Set<String> groups = groupsOf(playerId);
        int highest = -1;
        for (int i = 0; i < trackOrder.size(); i++) {
            if (groups.contains(trackOrder.get(i).toLowerCase(Locale.ROOT))) {
                highest = i;
            }
        }
        return highest;
    }

    @Override
    public boolean has(UUID playerId, String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        String node = permission.toLowerCase(Locale.ROOT);
        Set<String> perms = permissionsOf(playerId);
        if (perms.contains("-" + node)) {
            return false;
        }
        if (perms.contains(node) || perms.contains("*")) {
            return true;
        }
        Player online = Bukkit.getPlayer(playerId);
        return online != null && online.hasPermission(permission);
    }

    @Override
    public void refresh(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        ensureUser(uuid, player.getName());
        invalidate(uuid);
        PermissionAttachment old = attachments.remove(uuid);
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
            }
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        Set<String> groups = groupsOf(uuid);
        for (String group : groups) {
            attachment.setPermission("group." + group, true);
        }

        Set<String> nodes = permissionsOf(uuid);
        boolean star = nodes.contains("*");
        for (String node : nodes) {
            if ("*".equals(node)) {
                continue;
            }
            if (node.startsWith("-") && node.length() > 1) {
                attachment.setPermission(node.substring(1), false);
            } else {
                attachment.setPermission(node, true);
            }
        }
        if (star) {
            grantOpCommandAccess(attachment);
            if (!player.isOp()) {
                player.setOp(true);
            }
        }
        player.recalculatePermissions();
    }

    /** Expand {@code *} into registered plugin perms + vanilla OP command nodes. */
    private void grantOpCommandAccess(PermissionAttachment attachment) {
        attachment.setPermission("*", true);
        for (Permission permission : Bukkit.getPluginManager().getPermissions()) {
            String name = permission.getName();
            if (name != null && !name.isBlank()) {
                attachment.setPermission(name, true);
            }
        }
        for (String cmd : VANILLA_OP_COMMANDS) {
            attachment.setPermission("minecraft.command." + cmd, true);
            attachment.setPermission("bukkit.command." + cmd, true);
        }
    }

    public void clearAttachment(Player player) {
        if (player == null) {
            return;
        }
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
            }
        }
        invalidate(player.getUniqueId());
    }

    @Override
    public Set<String> groupsOf(UUID playerId) {
        return groupCache.computeIfAbsent(playerId, id -> {
            try {
                return store.resolveGroups(id);
            } catch (SQLException | RuntimeException ex) {
                plugin.getLogger().warning("groupsOf failed: " + ex.getMessage());
                return Set.of(config.defaultGroup());
            }
        });
    }

    private Set<String> permissionsOf(UUID playerId) {
        return permCache.computeIfAbsent(playerId, id -> {
            try {
                return store.resolvePermissions(id);
            } catch (SQLException | RuntimeException ex) {
                plugin.getLogger().warning("permissionsOf failed: " + ex.getMessage());
                return Set.of();
            }
        });
    }

    private void invalidate(UUID playerId) {
        groupCache.remove(playerId);
        permCache.remove(playerId);
    }

    @Override
    public String groupDisplay(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return "";
        }
        try {
            PermsStore.GroupRow row = store.group(groupId);
            return row == null ? groupId : row.display();
        } catch (SQLException ex) {
            return groupId;
        }
    }

    @Override
    public String groupPrefix(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return "";
        }
        try {
            PermsStore.GroupRow row = store.group(groupId);
            return row == null || row.prefix() == null ? "" : row.prefix();
        } catch (SQLException ex) {
            return "";
        }
    }

    public String[] allGroupIds() {
        try {
            return store.allGroups().keySet().toArray(String[]::new);
        } catch (SQLException ex) {
            return config.seedGroups().keySet().toArray(String[]::new);
        }
    }

    public Map<UUID, PermissionAttachment> attachmentsView() {
        return new HashMap<>(attachments);
    }
}
