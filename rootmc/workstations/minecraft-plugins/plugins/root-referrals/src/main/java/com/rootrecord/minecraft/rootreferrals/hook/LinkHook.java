package com.rootrecord.minecraft.rootreferrals.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Best-effort Discord link status via RootMC cloud API (reflection). */
public final class LinkHook {

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, CachedLink> cache = new ConcurrentHashMap<>();
    private static final long CACHE_MS = 30_000L;

    public LinkHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public LinkInfo linkInfo(UUID uuid) {
        if (uuid == null) {
            return LinkInfo.unlinked();
        }
        long now = System.currentTimeMillis();
        CachedLink cached = cache.get(uuid);
        if (cached != null && now - cached.atMs < CACHE_MS) {
            return cached.info;
        }
        LinkInfo resolved = resolve(uuid);
        cache.put(uuid, new CachedLink(resolved, now));
        return resolved;
    }

    public void invalidate(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid);
        }
    }

    private LinkInfo resolve(UUID uuid) {
        Plugin rootMc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (rootMc == null) {
            return LinkInfo.unlinked();
        }
        try {
            Method cloudMethod = rootMc.getClass().getMethod("cloud");
            Object cloud = cloudMethod.invoke(rootMc);
            if (cloud == null) {
                return LinkInfo.unlinked();
            }
            Method linkStatus = cloud.getClass().getMethod("linkStatus", String.class);
            Object status = linkStatus.invoke(cloud, uuid.toString());
            if (status == null) {
                return LinkInfo.unlinked();
            }
            boolean linked = readBoolean(status, "linked");
            boolean discordLinked = readBoolean(status, "discordLinked");
            if (!linked && !discordLinked) {
                discordLinked = readJsonFlag(status, "discord_linked");
            }
            String accountId = readString(status, "accountId");
            if (accountId == null) {
                accountId = readString(status, "account_id");
            }
            String discordUserId = readDiscordUserId(status);
            String identity = discordUserId != null && !discordUserId.isBlank()
                    ? discordUserId
                    : (accountId != null && !accountId.isBlank() ? accountId : null);
            return new LinkInfo(linked, discordLinked, identity, null);
        } catch (Exception e) {
            plugin.getLogger().fine("LinkHook resolve failed for " + uuid + ": " + e.getMessage());
            return LinkInfo.unlinked();
        }
    }

    private static String readDiscordUserId(Object status) {
        String direct = readString(status, "discordUserId");
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        direct = readString(status, "discord_user_id");
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        try {
            Method m = status.getClass().getMethod("getDiscordUserId");
            Object v = m.invoke(status);
            if (v != null) {
                return v.toString();
            }
        } catch (Exception ignored) {
            // record accessor optional
        }
        if (status instanceof java.lang.Record) {
            for (Method accessor : status.getClass().getMethods()) {
                if (accessor.getParameterCount() != 0 || accessor.getReturnType() == void.class) {
                    continue;
                }
                String name = accessor.getName().toLowerCase();
                if (name.contains("discord") && name.contains("id")) {
                    try {
                        Object v = accessor.invoke(status);
                        if (v != null && !v.toString().isBlank()) {
                            return v.toString();
                        }
                    } catch (Exception ignored) {
                        // try next accessor
                    }
                }
            }
        }
        return null;
    }

    private static boolean readBoolean(Object target, String field) {
        try {
            Method m = target.getClass().getMethod(field);
            Object v = m.invoke(target);
            return v instanceof Boolean b && b;
        } catch (Exception ignored) {
            // fall through
        }
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(target);
            return v instanceof Boolean b && b;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean readJsonFlag(Object target, String snake) {
        return readBoolean(target, snake) || readBoolean(target, toCamel(snake));
    }

    private static String readString(Object target, String field) {
        try {
            Method m = target.getClass().getMethod(field);
            Object v = m.invoke(target);
            return v != null ? v.toString() : null;
        } catch (Exception ignored) {
            // fall through
        }
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(target);
            return v != null ? v.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String toCamel(String snake) {
        if (snake == null || !snake.contains("_")) {
            return snake;
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private record CachedLink(LinkInfo info, long atMs) {}

    public record LinkInfo(boolean linked, boolean discordLinked, String identity, String lastKnownIp) {
        static LinkInfo unlinked() {
            return new LinkInfo(false, false, null, null);
        }
    }
}
