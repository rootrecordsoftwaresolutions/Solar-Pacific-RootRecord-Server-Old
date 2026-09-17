package com.rootrecord.minecraft.rootavacore.presence;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.rootrecord.minecraft.rootavacore.AvaConfig;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ava Mannequin fleet — home body plus one hired body per hirer.
 */
public final class AvaPresenceService {

    public enum Mode {
        WANDER,
        HANG,
        FOLLOW
    }

    public static final String BODY_MARKER = "ava_presence_body";
    public static final String HIRE_OWNER_KEY = "ava_hire_owner";
    /** Sentinel crew id for Ava's own-claim body. */
    public static final UUID HOME_CREW = new UUID(0L, 1L);

    private final RootAvaCorePlugin plugin;
    private final NamespacedKey bodyKey;
    private final NamespacedKey hireOwnerKey;
    private final Map<UUID, Crew> crews = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private String activeStack = "none";

    public AvaPresenceService(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
        this.bodyKey = new NamespacedKey(plugin, BODY_MARKER);
        this.hireOwnerKey = new NamespacedKey(plugin, HIRE_OWNER_KEY);
    }

    public NamespacedKey bodyKey() {
        return bodyKey;
    }

    public boolean isAvaBody(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(bodyKey, PersistentDataType.BYTE);
    }

    public UUID crewIdOf(Entity entity) {
        if (!isAvaBody(entity)) return null;
        String raw = entity.getPersistentDataContainer().get(hireOwnerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank() || "home".equalsIgnoreCase(raw)) {
            return HOME_CREW;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return HOME_CREW;
        }
    }

    private Crew home() {
        return crews.computeIfAbsent(HOME_CREW, id -> new Crew(id, "RootMC lead-dev"));
    }

    private Crew crew(UUID id) {
        if (id == null || HOME_CREW.equals(id)) return home();
        return crews.computeIfAbsent(id, k -> new Crew(k, "hired help"));
    }

    public Mannequin body() {
        return home().body;
    }

    public Location anchor() {
        Location a = home().anchor;
        return a == null ? null : a.clone();
    }

    public String activeStack() {
        return activeStack;
    }

    public Mode mode() {
        return home().mode;
    }

    public Location bodyLocation() {
        return bodyLocation(HOME_CREW);
    }

    public Location bodyLocation(UUID crewId) {
        Crew c = crew(crewId);
        if (!c.isSpawned()) return null;
        return c.body.getLocation().clone();
    }

    public UUID followTarget() {
        return home().followTarget;
    }

    public boolean isSpawned() {
        return home().isSpawned();
    }

    public boolean isSpawned(UUID crewId) {
        return crew(crewId).isSpawned();
    }

    public int spawnedCount() {
        int n = 0;
        for (Crew c : crews.values()) {
            if (c.isSpawned()) n++;
        }
        return n;
    }

    public void startIfEnabled() {
        AvaConfig.PresenceConfig cfg = plugin.config().presence();
        if (!plugin.config().enabled() || !cfg.enabled()) {
            stop();
            plugin.getLogger().info("Ava presence idle (disabled in config).");
            return;
        }
        Crew h = home();
        if (ensureBody(h)) {
            holdChunkTicket(h, h.body.getLocation());
            restartTicker();
            purgeUntracked();
            return;
        }
        Location spawnAt = resolveAnchor(cfg, null);
        if (spawnAt == null) {
            plugin.getLogger().warning("Ava presence: no world/spawn — not spawning.");
            return;
        }
        h.mode = Mode.WANDER;
        h.followTarget = null;
        spawnAt(h, spawnAt, cfg);
        purgeUntracked();
    }

    public boolean spawnHere(Location loc) {
        return spawnAtMode(HOME_CREW, loc, Mode.HANG, "RootMC lead-dev");
    }

    public boolean parkAndWander(Location loc) {
        return parkAndWander(HOME_CREW, loc);
    }

    public boolean parkAndWander(UUID crewId, Location loc) {
        return spawnAtMode(crewId, loc, Mode.WANDER, subtitleFor(crewId));
    }

    public boolean parkAndWork(Location loc) {
        return parkAndWork(HOME_CREW, loc);
    }

    public boolean parkAndWork(UUID crewId, Location loc) {
        return spawnAtMode(crewId, loc, Mode.HANG, subtitleFor(crewId));
    }

    public void appearWorking(Location work) {
        appearWorking(HOME_CREW, work);
    }

    public void appearWorking(UUID crewId, Location work) {
        if (work == null || work.getWorld() == null) return;
        if (!plugin.config().presence().enabled()) return;
        Crew c = crew(crewId);
        if (!ensureBody(c)) {
            spawnAtMode(crewId, work, Mode.HANG, subtitleFor(crewId));
            if (!ensureBody(c)) return;
        }
        if (c.mode == Mode.FOLLOW) {
            c.mode = Mode.HANG;
            c.followTarget = null;
        }
        Location stand = findStand(work.clone().add(0.5, 0, 0.5));
        if (stand == null) {
            stand = work.clone().add(0.5, 1, 0.5);
        }
        if (!stand.getBlock().isPassable()) {
            stand = findStand(work.clone().add(1.5, 0, 0.5));
            if (stand == null) stand = work.clone().add(0.5, 1, 0.5);
        }
        Location lookFrom = stand.clone();
        Vector to = work.toVector().add(new Vector(0.5, 0.5, 0.5)).subtract(lookFrom.toVector());
        if (to.lengthSquared() > 0.01) {
            lookFrom.setDirection(to);
        }
        stand.setYaw(lookFrom.getYaw());
        stand.setPitch(0f);
        c.body.teleport(stand);
        c.body.setRotation(stand.getYaw(), 0f);
        c.anchor = stand.clone();
        holdChunkTicket(c, stand);
        if (c.mode != Mode.HANG) {
            c.mode = Mode.HANG;
        }
    }

    public boolean hang() {
        return hang(HOME_CREW);
    }

    public boolean hang(UUID crewId) {
        Crew c = crew(crewId);
        if (!ensureSpawned(c)) return false;
        c.mode = Mode.HANG;
        c.followTarget = null;
        if (c.body != null) {
            c.anchor = c.body.getLocation().clone();
        }
        restartTicker();
        return true;
    }

    public boolean follow(Player target) {
        return follow(HOME_CREW, target);
    }

    public boolean follow(UUID crewId, Player target) {
        if (target == null || !target.isOnline()) return false;
        Crew c = crew(crewId);
        c.subtitle = HOME_CREW.equals(crewId) ? "RootMC lead-dev" : ("hired by " + target.getName());
        if (!ensureSpawned(c) && !spawnAtMode(crewId, target.getLocation(), Mode.FOLLOW, c.subtitle)) {
            return false;
        }
        c.mode = Mode.FOLLOW;
        c.followTarget = target.getUniqueId();
        if (c.body != null) {
            applySubtitle(c.body, c.subtitle);
        }
        restartTicker();
        return true;
    }

    /** Spawn or refresh a hired Ava body for this player. */
    public boolean spawnHire(UUID hirerId, Location at, String hirerName) {
        if (hirerId == null || at == null) return false;
        Crew c = crew(hirerId);
        c.subtitle = hirerName == null || hirerName.isBlank() ? "hired help" : ("hired by " + hirerName);
        return spawnAtMode(hirerId, at, Mode.HANG, c.subtitle);
    }

    public void despawnHire(UUID hirerId) {
        if (hirerId == null || HOME_CREW.equals(hirerId)) return;
        Crew c = crews.remove(hirerId);
        if (c == null) return;
        releaseChunkTicket(c);
        if (c.body != null) {
            try {
                c.body.remove();
            } catch (Throwable ignored) {
            }
        }
        purgeHireOrphans(activeHireIds());
    }

    public boolean stopFollow() {
        return stopFollow(HOME_CREW);
    }

    public boolean stopFollow(UUID crewId) {
        Crew c = crew(crewId);
        c.mode = Mode.WANDER;
        c.followTarget = null;
        if (c.body != null) {
            c.anchor = c.body.getLocation().clone();
        }
        restartTicker();
        return true;
    }

    /** Despawn home body only — hired crews stay. */
    public void despawn() {
        Crew h = crews.remove(HOME_CREW);
        if (h != null) {
            releaseChunkTicket(h);
            if (h.body != null) {
                try {
                    h.body.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        purgeUntracked();
        if (crews.isEmpty()) {
            stopTasksOnly();
            activeStack = "none";
        }
    }

    public void reload() {
        Location keep = isSpawned() ? home().body.getLocation().clone() : null;
        Mode keepMode = home().mode;
        UUID keepFollow = home().followTarget;
        Crew h = crews.remove(HOME_CREW);
        if (h != null) {
            releaseChunkTicket(h);
            if (h.body != null) {
                try {
                    h.body.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        AvaConfig.PresenceConfig cfg = plugin.config().presence();
        if (!plugin.config().enabled() || !cfg.enabled()) {
            return;
        }
        Crew next = home();
        next.mode = keepMode;
        next.followTarget = keepFollow;
        if (keep != null) {
            spawnAt(next, keep, cfg);
        } else {
            startIfEnabled();
        }
    }

    /** Plugin disable — remove every Ava mannequin. */
    public void stop() {
        stopTasksOnly();
        for (Crew c : crews.values()) {
            releaseChunkTicket(c);
            if (c.body != null) {
                try {
                    c.body.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        crews.clear();
        purgeAllMarked();
        activeStack = "none";
    }

    private Set<UUID> activeHireIds() {
        Set<UUID> ids = new HashSet<>();
        for (UUID id : crews.keySet()) {
            if (!HOME_CREW.equals(id)) ids.add(id);
        }
        return ids;
    }

    private String subtitleFor(UUID crewId) {
        if (crewId == null || HOME_CREW.equals(crewId)) return "RootMC lead-dev";
        Crew c = crews.get(crewId);
        if (c != null && c.subtitle != null && !c.subtitle.isBlank()) return c.subtitle;
        Player p = Bukkit.getPlayer(crewId);
        return p != null ? "hired by " + p.getName() : "hired help";
    }

    private boolean spawnAtMode(UUID crewId, Location loc, Mode spawnMode, String subtitle) {
        AvaConfig.PresenceConfig cfg = plugin.config().presence();
        if (!cfg.enabled()) return false;
        if (loc == null || loc.getWorld() == null) return false;
        Crew c = crew(crewId);
        c.subtitle = subtitle == null ? subtitleFor(crewId) : subtitle;
        c.mode = spawnMode;
        if (spawnMode != Mode.FOLLOW) {
            c.followTarget = null;
        }
        if (ensureBody(c)) {
            Location stand = findStand(loc.clone());
            if (stand == null) stand = loc.clone();
            stand.setPitch(0f);
            c.body.teleport(stand);
            applySubtitle(c.body, c.subtitle);
            c.anchor = stand.clone();
            holdChunkTicket(c, stand);
            restartTicker();
            return true;
        }
        spawnAt(c, loc.clone(), cfg);
        return c.isSpawned();
    }

    private boolean ensureBody(Crew c) {
        if (c.isSpawned()) return true;
        return rebindExistingBody(c);
    }

    private boolean rebindExistingBody(Crew c) {
        if (c.isSpawned()) return true;
        String want = HOME_CREW.equals(c.id) ? "home" : c.id.toString();
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!(e instanceof Mannequin m) || !isAvaBody(m) || !m.isValid() || m.isDead()) continue;
                String owner = m.getPersistentDataContainer().get(hireOwnerKey, PersistentDataType.STRING);
                if (owner == null || owner.isBlank()) owner = "home";
                if (!want.equalsIgnoreCase(owner) && !(HOME_CREW.equals(c.id) && "home".equalsIgnoreCase(owner))) {
                    continue;
                }
                c.body = m;
                if (c.anchor == null) c.anchor = m.getLocation().clone();
                activeStack = "native-mannequin";
                restartTicker();
                return true;
            }
        }
        return false;
    }

    private boolean ensureSpawned(Crew c) {
        if (ensureBody(c)) return true;
        AvaConfig.PresenceConfig cfg = plugin.config().presence();
        if (!cfg.enabled()) return false;
        Location spawnAt = resolveAnchor(cfg, c.anchor);
        if (spawnAt == null) return false;
        spawnAt(c, spawnAt, cfg);
        return c.isSpawned();
    }

    private void spawnAt(Crew c, Location loc, AvaConfig.PresenceConfig cfg) {
        World world = loc.getWorld();
        if (world == null) return;
        Location safe = loc.clone();
        safe.setPitch(0f);
        if (ensureBody(c)) {
            c.body.teleport(safe);
            applySubtitle(c.body, c.subtitle);
            c.anchor = safe.clone();
            holdChunkTicket(c, safe);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - c.lastSpawnAtMs < 5_000L) {
            return;
        }
        purgeUntracked();
        try {
            Mannequin spawned = world.spawn(safe, Mannequin.class, m -> applyBody(m, cfg, c));
            c.body = spawned;
        } catch (Throwable t) {
            plugin.getLogger().warning("Ava presence spawn failed: " + t.getMessage());
            c.body = null;
            return;
        }
        c.lastSpawnAtMs = now;
        c.anchor = safe.clone();
        activeStack = "native-mannequin";
        holdChunkTicket(c, safe);
        restartTicker();
        plugin.getLogger().info(
                "Ava presence spawned (" + (HOME_CREW.equals(c.id) ? "home" : "hire:" + c.id) + "/" + c.mode + ") at "
                        + world.getName() + " "
                        + Math.round(safe.getX()) + ","
                        + Math.round(safe.getY()) + ","
                        + Math.round(safe.getZ()));
    }

    private void restartTicker() {
        stopTasksOnly();
        if (spawnedCount() == 0) return;
        AvaConfig.PresenceConfig cfg = plugin.config().presence();
        long interval = Math.max(10L, Math.min(cfg.followIntervalTicks(), cfg.wanderIntervalTicks()));
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, interval, interval);
    }

    private void tickAll() {
        AvaConfig.PresenceConfig cfg = plugin.config().presence();
        for (Crew c : crews.values()) {
            if (!c.isSpawned()) continue;
            switch (c.mode) {
                case FOLLOW -> tickFollow(c, cfg);
                case HANG -> tickHang(c);
                case WANDER -> tickWander(c, cfg);
            }
        }
    }

    private void tickHang(Crew c) {
        if (c.anchor == null || c.body == null) return;
        Location here = c.body.getLocation();
        float yaw = here.getYaw() + ThreadLocalRandom.current().nextFloat(-12f, 12f);
        c.body.setRotation(yaw, 0f);
        if (here.distanceSquared(c.anchor) > 1.5) {
            c.body.teleport(c.anchor);
        }
    }

    private void tickFollow(Crew c, AvaConfig.PresenceConfig cfg) {
        if (c.followTarget == null) {
            c.mode = Mode.WANDER;
            return;
        }
        Player target = Bukkit.getPlayer(c.followTarget);
        if (target == null || !target.isOnline()) {
            c.mode = Mode.HANG;
            c.followTarget = null;
            if (c.body != null) c.anchor = c.body.getLocation().clone();
            return;
        }
        Location tLoc = target.getLocation();
        Location here = c.body.getLocation();
        if (!here.getWorld().equals(tLoc.getWorld())) {
            c.body.teleport(behind(tLoc, cfg.followDistance()));
            holdChunkTicket(c, tLoc);
            return;
        }
        double dist = here.distance(tLoc);
        double desired = cfg.followDistance();
        double max = cfg.followMaxDistance();
        Vector to = tLoc.toVector().subtract(here.toVector());
        if (to.lengthSquared() > 0.01) {
            Location look = here.clone().setDirection(to);
            c.body.setRotation(look.getYaw(), 0f);
        }
        if (dist > max && cfg.followTeleportIfFar()) {
            Location at = behind(tLoc, desired);
            c.body.teleport(at);
            holdChunkTicket(c, at);
            return;
        }
        if (dist > desired + 0.6) {
            Location step = behind(tLoc, desired);
            Location ground = findStand(step);
            if (ground != null) {
                c.body.teleport(ground);
                holdChunkTicket(c, ground);
            }
        }
    }

    private void tickWander(Crew c, AvaConfig.PresenceConfig cfg) {
        if (!cfg.wanderEnabled() || cfg.wanderRadius() <= 0 || c.anchor == null) {
            tickHang(c);
            return;
        }
        Location here = c.body.getLocation();
        float yaw = here.getYaw() + ThreadLocalRandom.current().nextFloat(-25f, 25f);
        c.body.setRotation(yaw, 0f);
        if (ThreadLocalRandom.current().nextDouble() > 0.55) return;
        double radius = cfg.wanderRadius();
        double ox = ThreadLocalRandom.current().nextDouble(-radius, radius);
        double oz = ThreadLocalRandom.current().nextDouble(-radius, radius);
        Location target = c.anchor.clone().add(ox, 0, oz);
        target.setY(c.anchor.getY());
        Location ground = findStand(target);
        if (ground == null) return;
        if (ground.distanceSquared(c.anchor) > radius * radius) {
            ground = c.anchor.clone();
        }
        c.body.teleport(ground);
        holdChunkTicket(c, ground);
        Vector dir = ground.toVector().subtract(here.toVector());
        if (dir.lengthSquared() > 0.01) {
            Location look = ground.clone().setDirection(dir);
            c.body.setRotation(look.getYaw(), 0f);
        }
    }

    private static Location behind(Location target, double distance) {
        Location base = target.clone();
        Vector back = base.getDirection().normalize().multiply(-distance);
        Location at = base.add(back);
        at.setY(target.getY());
        at.setYaw(target.getYaw());
        at.setPitch(0f);
        Location ground = findStand(at);
        return ground != null ? ground : at;
    }

    private void holdChunkTicket(Crew c, Location loc) {
        if (c == null || loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        if (c.ticketWorld == world && c.ticketCx == cx && c.ticketCz == cz) return;
        releaseChunkTicket(c);
        try {
            world.addPluginChunkTicket(cx, cz, plugin);
            c.ticketWorld = world;
            c.ticketCx = cx;
            c.ticketCz = cz;
        } catch (Throwable t) {
            plugin.getLogger().fine("Ava chunk ticket: " + t.getMessage());
        }
    }

    private void releaseChunkTicket(Crew c) {
        if (c == null || c.ticketWorld == null) return;
        try {
            c.ticketWorld.removePluginChunkTicket(c.ticketCx, c.ticketCz, plugin);
        } catch (Throwable ignored) {
        }
        c.ticketWorld = null;
        c.ticketCx = Integer.MIN_VALUE;
        c.ticketCz = Integer.MIN_VALUE;
    }

    private void applyBody(Mannequin m, AvaConfig.PresenceConfig cfg, Crew c) {
        m.getPersistentDataContainer().set(bodyKey, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(
                hireOwnerKey,
                PersistentDataType.STRING,
                HOME_CREW.equals(c.id) ? "home" : c.id.toString());
        m.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(cfg.displayName()));
        m.setCustomNameVisible(true);
        applySubtitle(m, c.subtitle);
        m.setImmovable(false);
        m.setCollidable(false);
        m.setSilent(true);
        m.setRemoveWhenFarAway(false);
        m.setPersistent(true);
        m.setCanPickupItems(false);
        m.setInvulnerable(cfg.invulnerable());
        m.setGravity(true);
        m.setAI(false);
        try {
            var maxHealth = m.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(20.0);
            }
            m.setHealth(20.0);
        } catch (Throwable ignored) {
        }
        if (m.getEquipment() != null) {
            m.getEquipment().clear();
        }
        try {
            applySkin(m, cfg);
        } catch (Throwable t) {
            plugin.getLogger().warning("Ava presence skin apply failed: " + t.getMessage());
        }
    }

    private void applySubtitle(Mannequin m, String subtitle) {
        String line = subtitle == null || subtitle.isBlank() ? "RootMC lead-dev" : subtitle;
        m.setDescription(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + line));
    }

    private void applySkin(Mannequin m, AvaConfig.PresenceConfig cfg) {
        String skinName = cfg.skinName();
        if (skinName == null || skinName.isBlank()) skinName = "Ava_Ivy";
        if (skinName.length() > 16) skinName = skinName.substring(0, 16);
        UUID uuid;
        String rawUuid = cfg.skinUuid();
        if (rawUuid != null && !rawUuid.isBlank()) {
            try {
                uuid = UUID.fromString(rawUuid.trim());
            } catch (IllegalArgumentException ex) {
                uuid = UUID.nameUUIDFromBytes(("RootAvaPresence:" + skinName).getBytes(StandardCharsets.UTF_8));
            }
        } else {
            uuid = UUID.nameUUIDFromBytes(("RootAvaPresence:" + skinName).getBytes(StandardCharsets.UTF_8));
        }
        ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile()
                .name(skinName)
                .uuid(uuid);
        String texture = cfg.skinTexture();
        String signature = cfg.skinTextureSignature();
        if (texture != null && !texture.isBlank()) {
            try {
                if (signature != null && !signature.isBlank()) {
                    builder.addProperty(new ProfileProperty("textures", texture.trim(), signature.trim()));
                } else {
                    builder.addProperty(new ProfileProperty("textures", texture.trim()));
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Ava presence texture property rejected: " + t.getMessage());
            }
        }
        m.setProfile(builder.build());
    }

    private void purgeUntracked() {
        Set<Mannequin> keep = new HashSet<>();
        for (Crew c : crews.values()) {
            if (c.body != null) keep.add(c.body);
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!isAvaBody(e) || e == null) continue;
                if (keep.contains(e)) continue;
                try {
                    e.remove();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void purgeHireOrphans(Set<UUID> keepHirers) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!isAvaBody(e)) continue;
                UUID id = crewIdOf(e);
                if (id == null || HOME_CREW.equals(id)) continue;
                if (keepHirers != null && keepHirers.contains(id)) continue;
                try {
                    e.remove();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void purgeAllMarked() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (isAvaBody(e)) {
                    try {
                        e.remove();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private void stopTasksOnly() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private static Location findStand(Location approx) {
        World world = approx.getWorld();
        if (world == null) return null;
        Location probe = approx.clone();
        for (int dy = 0; dy <= 3; dy++) {
            Location at = probe.clone().add(0, -dy, 0);
            Location below = at.clone().add(0, -1, 0);
            if (!at.getBlock().isPassable()) continue;
            if (below.getBlock().getType().isAir()) continue;
            if (!below.getBlock().getType().isSolid()) continue;
            at.setYaw(approx.getYaw());
            at.setPitch(0f);
            return at;
        }
        return approx;
    }

    private Location resolveAnchor(AvaConfig.PresenceConfig cfg, Location preferred) {
        if (preferred != null && preferred.getWorld() != null) {
            return preferred.clone();
        }
        if (!cfg.useWorldSpawn() && cfg.world() != null && !cfg.world().isBlank()) {
            World w = Bukkit.getWorld(cfg.world());
            if (w != null) {
                return new Location(w, cfg.x(), cfg.y(), cfg.z(), cfg.yaw(), 0f);
            }
        }
        String worldName = cfg.world();
        World world = worldName != null && !worldName.isBlank()
                ? Bukkit.getWorld(worldName)
                : Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) return null;
        if (cfg.useWorldSpawn()) {
            Location spawn = world.getSpawnLocation().clone();
            spawn.setYaw(cfg.yaw());
            spawn.setPitch(0f);
            return spawn;
        }
        return new Location(world, cfg.x(), cfg.y(), cfg.z(), cfg.yaw(), 0f);
    }

    private static final class Crew {
        final UUID id;
        String subtitle;
        Mannequin body;
        Location anchor;
        Mode mode = Mode.WANDER;
        UUID followTarget;
        long lastSpawnAtMs;
        World ticketWorld;
        int ticketCx = Integer.MIN_VALUE;
        int ticketCz = Integer.MIN_VALUE;

        Crew(UUID id, String subtitle) {
            this.id = id;
            this.subtitle = subtitle;
        }

        boolean isSpawned() {
            return body != null && body.isValid() && !body.isDead();
        }
    }
}
