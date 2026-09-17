package com.rootrecord.minecraft.rootcore.transfer;

import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Cached transfer mesh + Player.transfer helpers. */
public final class TransferMeshService {

    private final RootCorePlugin plugin;
    private final TransferMeshClient client;
    private final AtomicReference<List<MeshPeer>> peers = new AtomicReference<>(List.of());
    private volatile long fetchedAtMs;
    private BukkitTask pollTask;
    private volatile boolean evacuating;

    public TransferMeshService(RootCorePlugin plugin) {
        this.plugin = plugin;
        this.client = new TransferMeshClient(plugin);
    }

    public void startOrReload() {
        stop();
        if (!plugin.yamlConfig().config().getBoolean("transfer.enabled", true)) {
            plugin.getLogger().info("Transfer mesh disabled (transfer.enabled=false).");
            return;
        }
        // Local peers first so /goto works even when the Worker mesh is down (530 / tunnel).
        List<MeshPeer> local = loadLocalPeers();
        if (!local.isEmpty()) {
            peers.set(new CopyOnWriteArrayList<>(local));
            fetchedAtMs = System.currentTimeMillis();
            plugin.getLogger().info("Transfer mesh local peers: " + local.size() + " (API refresh still runs).");
        }
        refreshAsync();
        long minutes = Math.max(1, plugin.yamlConfig().config().getLong("transfer.poll-minutes", 1));
        long ticks = minutes * 60L * 20L;
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshQuiet, ticks, ticks);
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    public List<MeshPeer> peers() {
        return peers.get();
    }

    public MeshPeer find(String slugOrAlias) {
        String want = MeshPeer.normalizeAlias(slugOrAlias);
        for (MeshPeer peer : peers.get()) {
            if (peer.matches(want) || peer.matches(slugOrAlias)) {
                return peer;
            }
        }
        return null;
    }

    public void refreshAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshQuiet);
    }

    public void refreshNowBlocking() {
        applyPeers(fetchMerged());
    }

    private void refreshQuiet() {
        try {
            applyPeers(fetchMerged());
        } catch (Exception ex) {
            plugin.getLogger().warning("Transfer mesh refresh failed: " + ex.getMessage());
            ensureLocalFallback();
        }
    }

    private void applyPeers(List<MeshPeer> next) {
        if (next == null || next.isEmpty()) {
            ensureLocalFallback();
            return;
        }
        peers.set(new CopyOnWriteArrayList<>(next));
        fetchedAtMs = System.currentTimeMillis();
        plugin.getLogger().info("Transfer mesh loaded: " + next.size() + " peer(s).");
    }

    private List<MeshPeer> fetchMerged() {
        List<MeshPeer> local = loadLocalPeers();
        List<MeshPeer> remote = List.of();
        try {
            remote = client.fetchPeers();
        } catch (Exception ex) {
            plugin.getLogger().warning("Transfer mesh refresh failed: " + ex.getMessage());
        }
        if (remote.isEmpty()) {
            return local;
        }
        if (local.isEmpty()) {
            return remote;
        }
        // Prefer API host/port when present; keep local-only peers (e.g. test) that API omitted.
        List<MeshPeer> out = new ArrayList<>(remote);
        for (MeshPeer lp : local) {
            boolean seen = false;
            for (MeshPeer rp : remote) {
                if (rp.matches(lp.slug())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                out.add(lp);
            }
        }
        return out;
    }

    private void ensureLocalFallback() {
        if (!peers.get().isEmpty()) {
            return;
        }
        List<MeshPeer> local = loadLocalPeers();
        if (!local.isEmpty()) {
            peers.set(new CopyOnWriteArrayList<>(local));
            fetchedAtMs = System.currentTimeMillis();
            plugin.getLogger().warning("Transfer mesh using local-peers fallback (" + local.size() + ").");
        }
    }

    private List<MeshPeer> loadLocalPeers() {
        ConfigurationSection section = plugin.yamlConfig().config().getConfigurationSection("transfer.local-peers");
        if (section == null) {
            return List.of();
        }
        List<MeshPeer> out = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            ConfigurationSection peer = section.getConfigurationSection(key);
            if (peer == null) {
                continue;
            }
            if (!peer.getBoolean("enabled", true)) {
                continue;
            }
            String host = peer.getString("host", "");
            int port = peer.getInt("port", 0);
            if (host == null || host.isBlank() || port <= 0) {
                continue;
            }
            String slug = MeshPeer.normalizeAlias(key);
            if (slug.isBlank()) {
                slug = key.trim().toLowerCase(Locale.ROOT);
            }
            String label = peer.getString("label", slug);
            List<String> aliases = peer.getStringList("aliases");
            out.add(new MeshPeer(
                    slug,
                    label == null || label.isBlank() ? slug : label,
                    host.trim(),
                    port,
                    peer.getString("kind", "official"),
                    peer.getString("server-id", null),
                    peer.contains("online") ? peer.getBoolean("online") : null,
                    aliases == null ? List.of() : List.copyOf(aliases)));
        }
        return out;
    }

    public boolean stale(long maxAgeMs) {
        return System.currentTimeMillis() - fetchedAtMs > maxAgeMs;
    }

    public boolean transfer(Player player, MeshPeer dest, boolean announce) {
        if (player == null || dest == null || !player.isOnline()) {
            return false;
        }
        if (("test".equalsIgnoreCase(dest.slug()) || (dest.online() != null && !dest.online()))
                && !tcpReachable(dest, 1500)) {
            player.sendMessage(color("&c" + dest.displayLabel() + " looks offline. Try again later."));
            return false;
        }
        if (announce) {
            player.sendMessage(color("&7Connecting to &f" + dest.displayLabel() + "&7…"));
        }
        try {
            Bukkit.broadcastMessage(color("&a" + player.getName() + " &7transferred to &f" + dest.displayLabel()));
            player.transfer(dest.host(), dest.port());
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Transfer to " + dest.host() + ":" + dest.port() + " failed: " + ex.getMessage());
            player.sendMessage(color("&cCould not connect to &f" + dest.displayLabel() + "&c: &f"
                    + (ex.getMessage() == null ? "unknown" : ex.getMessage())));
            return false;
        }
    }

    public void evacuateForRestart(Runnable after) {
        if (evacuating) {
            if (after != null) {
                after.run();
            }
            return;
        }
        if (!plugin.yamlConfig().config().getBoolean("transfer.evacuate-on-stop", true)) {
            if (after != null) {
                after.run();
            }
            return;
        }
        String preferred = plugin.yamlConfig().config().getString("transfer.evacuate-to", "");
        MeshPeer peer = preferred == null || preferred.isBlank() ? null : find(preferred);
        if (peer == null) {
            for (MeshPeer p : peers.get()) {
                if (!"test".equalsIgnoreCase(p.slug())) {
                    peer = p;
                    break;
                }
            }
        }
        if (peer == null || Bukkit.getOnlinePlayers().isEmpty()) {
            if (after != null) {
                after.run();
            }
            return;
        }
        evacuating = true;
        MeshPeer dest = peer;
        Bukkit.broadcastMessage(color("&eThis server is restarting — transferring you to &f" + dest.displayLabel() + "&e…"));
        List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : snapshot) {
            if (player.isOnline()) {
                transfer(player, dest, false);
            }
        }
        long delay = Math.max(10, plugin.yamlConfig().config().getLong("transfer.evacuate-delay-ticks", 40));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (after != null) {
                    after.run();
                }
            } finally {
                evacuating = false;
            }
        }, delay);
    }

    private static boolean tcpReachable(MeshPeer dest, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(dest.host(), dest.port()), Math.max(500, timeoutMillis));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static String color(String s) {
        return s == null ? "" : s.replace('&', '\u00A7');
    }
}
