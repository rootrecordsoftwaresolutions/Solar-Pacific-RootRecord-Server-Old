package com.rootrecord.minecraft.rootessentials.service;

import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opt-in {@code /radar}: 1 ping/sec, 1 G/min, 4-block ore compass bar.
 * Action bar is a classic NESW dash tape + up to 3 ore pips — not skills text.
 * Idle 10 pings without moving → auto off. Skills XP yields the action bar while scanning.
 */
public final class GoldProspectService implements Listener {

    public static final double PING_COST = 1.0d / 60.0d;
    public static final int IDLE_PINGS = 10;
    private static final long PING_TICKS = 20L;

    private final RootEconomyPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private BukkitTask task;

    public GoldProspectService(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, PING_TICKS);
        plugin.getLogger().info("Ore radar ready · /radar · 4-block compass · 1G/min · idle-off 10s.");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sessions.clear();
    }

    public boolean isActive(UUID uuid) {
        return uuid != null && sessions.containsKey(uuid);
    }

    public boolean isActive(Player player) {
        return player != null && isActive(player.getUniqueId());
    }

    /** Toggle radar. {@code true} if now on. */
    public boolean toggle(Player player) {
        if (isActive(player)) {
            deactivate(player, "off");
            return false;
        }
        return activate(player);
    }

    public boolean activate(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            player.sendMessage(plugin.colorize("&eRadar is survival/adventure only."));
            return false;
        }
        if (isActive(player)) {
            return true;
        }
        double bal = plugin.balance(player.getUniqueId(), player.getName());
        if (bal + 1e-9 < PING_COST) {
            player.sendMessage(plugin.colorize(
                    "&cRadar needs &f" + plugin.money(PING_COST) + " G &cper ping (1 G/min). Balance &f"
                            + plugin.money(bal) + " G&c."));
            return false;
        }
        Location loc = player.getLocation();
        sessions.put(player.getUniqueId(), new Session(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 0));
        player.sendMessage(plugin.colorize(
                "&aRadar on &8· &fNESW tape &8· &e1 G/min &8· &7stand still 10s to auto-off"));
        ping(player);
        return true;
    }

    public void deactivate(Player player, String reason) {
        if (player == null) {
            return;
        }
        Session removed = sessions.remove(player.getUniqueId());
        if (removed == null) {
            return;
        }
        if ("idle".equals(reason)) {
            player.sendMessage(plugin.colorize("&eRadar off &8· &7no movement for 10 pings"));
        } else if ("broke".equals(reason)) {
            player.sendMessage(plugin.colorize("&cRadar off &8· &7not enough G (1 G/min)"));
        } else if ("mode".equals(reason)) {
            player.sendMessage(plugin.colorize("&eRadar off &8· &7creative/spectator"));
        } else {
            player.sendMessage(plugin.colorize("&7Radar off."));
        }
        player.sendActionBar(Component.empty());
    }

    public Hit detect(Player player) {
        return detectGold(player);
    }

    public Hit detectGold(Player player) {
        return scan(player, true);
    }

    public Hit detectClosest(Player player) {
        return scan(player, false);
    }

    public GoldSiteRates.Site ratesHere(Player player) {
        double solar = plugin.solarMining() != null ? plugin.solarMining().multiplier() : 1.0d;
        Location loc = player != null ? player.getLocation() : null;
        Hit hit = player != null ? detectGold(player) : null;
        if (hit != null) {
            return hit.site();
        }
        return GoldSiteRates.at(loc, null, solar);
    }

    private Hit scan(Player player, boolean goldOnly) {
        return scanAround(player, goldOnly).closest();
    }

    private Scan scanAround(Player player, boolean goldOnly) {
        if (player == null || !player.isOnline() || player.getWorld() == null) {
            return Scan.EMPTY;
        }
        Location origin = player.getLocation().add(0, 1.0, 0);
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int r = GoldSiteRates.DETECT_RADIUS;
        int r2 = r * r;
        Block closest = null;
        OreKind closestKind = null;
        int best = r2 + 1;
        Set<OreKind> nearby = EnumSet.noneOf(OreKind.class);
        java.util.List<Pip> pips = new java.util.ArrayList<>();
        Location eye = player.getEyeLocation();
        float yaw = player.getLocation().getYaw();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int dist2 = dx * dx + dy * dy + dz * dz;
                    if (dist2 > r2) {
                        continue;
                    }
                    Block block = player.getWorld().getBlockAt(ox + dx, oy + dy, oz + dz);
                    OreKind kind = OreKind.of(block.getType());
                    if (kind == null) {
                        continue;
                    }
                    if (goldOnly && kind != OreKind.GOLD) {
                        continue;
                    }
                    nearby.add(kind);
                    double rel = relativeYaw(eye, yaw, block);
                    pips.add(new Pip(kind, rel, dy, dist2));
                    if (dist2 < best) {
                        closest = block;
                        closestKind = kind;
                        best = dist2;
                    }
                }
            }
        }
        if (closest == null || closestKind == null) {
            return Scan.EMPTY;
        }
        double solar = plugin.solarMining() != null ? plugin.solarMining().multiplier() : 1.0d;
        GoldSiteRates.Site site = GoldSiteRates.at(closest.getLocation(), closest.getType(), solar);
        Hit hit = new Hit(
                closest,
                Math.max(1, (int) Math.round(Math.sqrt(best))),
                bearing(player, closest),
                site,
                closestKind,
                nearby);
        return new Scan(hit, pips);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session == null) {
                continue;
            }
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
                deactivate(player, "mode");
                continue;
            }
            Location loc = player.getLocation();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            if (x == session.x && y == session.y && z == session.z) {
                session.idlePings++;
            } else {
                session.x = x;
                session.y = y;
                session.z = z;
                session.idlePings = 0;
            }
            if (session.idlePings >= IDLE_PINGS) {
                deactivate(player, "idle");
                continue;
            }
            ping(player);
        }
    }

    private void ping(Player player) {
        if (!chargePing(player)) {
            deactivate(player, "broke");
            return;
        }
        Scan sweep = scanAround(player, false);
        player.sendActionBar(compassBar(player, sweep.pips()));
        if (sweep.closest() != null) {
            sparkle(player, sweep.closest().ore(), sweep.closest().kind());
        }
    }

    private boolean chargePing(Player player) {
        try {
            boolean ok = plugin.withdraw(player.getUniqueId(), player.getName(), PING_COST);
            if (!ok) {
                return false;
            }
            if (plugin.treasury() != null) {
                plugin.treasury().creditTreasury(
                        PING_COST,
                        TreasuryLedgerType.OTHER,
                        player.getUniqueId(),
                        player.getName(),
                        "radar ping");
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Radar ping charge failed for " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private static void sparkle(Player player, Block ore, OreKind kind) {
        Location at = ore.getLocation().add(0.5, 0.55, 0.5);
        player.spawnParticle(Particle.DUST, at, 8, 0.28, 0.28, 0.28, 0, kind.sparkle());
    }

    private static String bearing(Player player, Block ore) {
        Location eye = player.getEyeLocation();
        Vector to = ore.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 1e-6) {
            return "here";
        }
        double dy = to.getY();
        String vert = dy > 1.4 ? "↑" : (dy < -1.4 ? "↓" : "");
        double yaw = Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        String compass = cardinal8(yaw);
        return vert.isEmpty() ? compass : vert + compass;
    }

    /** Player-relative yaw to a block: 0 = ahead, +90 = right, -90 = left. */
    private static double relativeYaw(Location eye, float playerYaw, Block ore) {
        double dx = ore.getX() + 0.5 - eye.getX();
        double dz = ore.getZ() + 0.5 - eye.getZ();
        if (dx * dx + dz * dz < 1e-8) {
            return 0;
        }
        double oreYaw = Math.toDegrees(Math.atan2(-dx, dz));
        return wrap180(oreYaw - playerYaw);
    }

    private static String cardinal8(double mcYaw) {
        double heading = wrap360(mcYaw + 180.0);
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(heading / 45.0) % 8];
    }

    /**
     * Classic scrolling NESW tape: dashes stay, N/E/S/W never get eaten,
     * ▲ is ahead. At most 3 colored * pips. Elevation lives in the suffix.
     */
    private static Component compassBar(Player player, java.util.List<Pip> pips) {
        final int width = 29;
        final int mid = width / 2;
        final double degPer = 360.0 / width;
        double heading = wrap360(player.getLocation().getYaw() + 180.0);

        OreKind[] oreAt = new OreKind[width];
        int[] distAt = new int[width];
        java.util.Arrays.fill(distAt, Integer.MAX_VALUE);
        OreKind closestKind = null;
        int closestDy = 0;
        int closestDist = Integer.MAX_VALUE;
        int closestCount = 0;
        if (pips != null) {
            for (Pip pip : pips) {
                int slot = Math.floorMod(mid + (int) Math.round(pip.relDeg() / degPer), width);
                if (pip.dist2() < distAt[slot]) {
                    oreAt[slot] = pip.kind();
                    distAt[slot] = pip.dist2();
                }
                if (pip.dist2() < closestDist) {
                    closestDist = pip.dist2();
                    closestKind = pip.kind();
                    closestDy = pip.dy() > 1 ? 1 : (pip.dy() < -1 ? -1 : 0);
                }
            }
            if (closestKind != null) {
                for (Pip pip : pips) {
                    if (pip.kind() == closestKind) {
                        closestCount++;
                    }
                }
            }
            keepClosestOreSlots(oreAt, distAt, 3);
        }

        Component bar = Component.empty();
        for (int i = 0; i < width; i++) {
            double slotHeading = wrap360(heading + (i - mid) * degPer);
            String card = cardinal4(slotHeading, degPer * 0.40);
            if (i == mid) {
                NamedTextColor color = oreAt[i] != null ? oreAt[i].text() : NamedTextColor.GOLD;
                bar = bar.append(Component.text("▲", color, TextDecoration.BOLD));
                continue;
            }
            if (card != null) {
                NamedTextColor color = oreAt[i] != null ? oreAt[i].text() : NamedTextColor.WHITE;
                bar = bar.append(Component.text(card, color));
                continue;
            }
            if (oreAt[i] != null) {
                bar = bar.append(Component.text("*", oreAt[i].text()));
                continue;
            }
            bar = bar.append(Component.text("-", NamedTextColor.DARK_GRAY));
        }

        if (closestKind != null) {
            String vert = closestDy > 0 ? " ↑" : (closestDy < 0 ? " ↓" : "");
            String here = closestDist <= 1 ? " here" : "";
            String count = closestCount > 1 ? " ×" + closestCount : "";
            bar = bar.append(Component.text("  "))
                    .append(Component.text(closestKind.label() + here + vert + count, closestKind.text()));
        }
        return bar;
    }

    /** Keep at most {@code max} ore pips so a vein doesn't paint the whole tape. */
    private static void keepClosestOreSlots(OreKind[] oreAt, int[] distAt, int max) {
        int n = 0;
        for (OreKind kind : oreAt) {
            if (kind != null) {
                n++;
            }
        }
        if (n <= max) {
            return;
        }
        int[] best = new int[max];
        java.util.Arrays.fill(best, Integer.MAX_VALUE);
        for (int dist : distAt) {
            if (dist == Integer.MAX_VALUE) {
                continue;
            }
            for (int i = 0; i < max; i++) {
                if (dist < best[i]) {
                    for (int j = max - 1; j > i; j--) {
                        best[j] = best[j - 1];
                    }
                    best[i] = dist;
                    break;
                }
            }
        }
        int cutoff = best[max - 1];
        int kept = 0;
        for (int i = 0; i < oreAt.length; i++) {
            if (oreAt[i] == null) {
                continue;
            }
            if (kept >= max || distAt[i] > cutoff) {
                oreAt[i] = null;
                distAt[i] = Integer.MAX_VALUE;
            } else {
                kept++;
            }
        }
    }

    private static String cardinal4(double heading, double threshold) {
        double[] marks = {0, 90, 180, 270};
        String[] labels = {"N", "E", "S", "W"};
        for (int i = 0; i < marks.length; i++) {
            if (Math.abs(wrap180(heading - marks[i])) <= threshold) {
                return labels[i];
            }
        }
        return null;
    }

    private static double wrap360(double deg) {
        double n = deg % 360.0;
        return n < 0 ? n + 360.0 : n;
    }

    private static double wrap180(double deg) {
        double n = wrap360(deg);
        return n > 180.0 ? n - 360.0 : n;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public record Hit(
            Block ore,
            int blocks,
            String bearing,
            GoldSiteRates.Site site,
            OreKind kind,
            Set<OreKind> nearby) {
        public Hit(Block ore, int blocks, String bearing, GoldSiteRates.Site site) {
            this(ore, blocks, bearing, site, OreKind.GOLD, Set.of(OreKind.GOLD));
        }
    }

    private record Pip(OreKind kind, double relDeg, int dy, int dist2) {}

    private record Scan(Hit closest, java.util.List<Pip> pips) {
        static final Scan EMPTY = new Scan(null, java.util.List.of());
    }

    private static final class Session {
        int x;
        int y;
        int z;
        int idlePings;

        Session(int x, int y, int z, int idlePings) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.idlePings = idlePings;
        }
    }
}
