package com.rootrecord.minecraft.rootavacore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Settings from plugins/RootMC/root-ava-core.yml. */
public final class AvaConfig {

    private final boolean enabled;
    private final String prefix;
    private final String statusLine;
    private final String disabled;
    private final String noPermission;
    private final String reloaded;
    private final PresenceConfig presence;
    private final PowersConfig powers;
    private final HireConfig hire;
    private final AutonomyConfig autonomy;
    private final SchematicConfig schematics;
    private final EconomyConfig economy;
    private final boolean meshWorldTransfers;
    private final String meshWorldPeer;

    public AvaConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("enabled", true);
        this.prefix = cfg.getString("messages.prefix", "&bAva &8· ");
        this.statusLine = cfg.getString(
                "messages.status-line",
                "&7v{version} &8· &f{online} online &8· &f{tps} TPS &8· &aAva companion online");
        this.disabled = cfg.getString("messages.disabled", "&cRoot-Ava-Core is disabled.");
        this.noPermission = cfg.getString("messages.no-permission", "&cNo permission.");
        this.reloaded = cfg.getString("messages.reloaded", "&aRoot-Ava-Core reloaded.");
        this.presence = PresenceConfig.from(cfg.getConfigurationSection("presence"));
        this.powers = PowersConfig.from(cfg.getConfigurationSection("powers"));
        this.hire = HireConfig.from(cfg.getConfigurationSection("hire"));
        this.autonomy = AutonomyConfig.from(cfg.getConfigurationSection("autonomy"));
        this.schematics = SchematicConfig.from(cfg.getConfigurationSection("schematics"));
        this.economy = EconomyConfig.from(cfg.getConfigurationSection("economy"));
        this.meshWorldTransfers = cfg.getBoolean("mesh.world-transfers", false);
        String peer = cfg.getString("mesh.world-peer", "ava");
        this.meshWorldPeer = peer == null || peer.isBlank() ? "ava" : peer.trim();
    }

    public boolean enabled() {
        return enabled;
    }

    public String prefix() {
        return prefix;
    }

    public String statusLine() {
        return statusLine;
    }

    public String disabled() {
        return disabled;
    }

    public String noPermission() {
        return noPermission;
    }

    public String reloaded() {
        return reloaded;
    }

    public PresenceConfig presence() {
        return presence;
    }

    public PowersConfig powers() {
        return powers;
    }

    public HireConfig hire() {
        return hire;
    }

    public AutonomyConfig autonomy() {
        return autonomy;
    }

    public SchematicConfig schematics() {
        return schematics;
    }

    public boolean meshWorldTransfers() {
        return meshWorldTransfers;
    }

    public String meshWorldPeer() {
        return meshWorldPeer;
    }

    public EconomyConfig economy() {
        return economy;
    }

    /** Wallet + playtime/bonus parity with other players; claim-bank spend. */
    public static final class EconomyConfig {
        private final boolean enabled;
        private final long tickSeconds;
        private final boolean creditPlaytimeWhileWorking;
        private final boolean payPlaytimeMilestones;
        private final boolean claimBonusWhenOnline;
        private final boolean autoClaimBankTopup;
        private final double bankReserveGold;
        private final double walletFloatGold;
        private final boolean autoExpand;
        private final int expandMaxPerHour;

        private EconomyConfig(
                boolean enabled,
                long tickSeconds,
                boolean creditPlaytimeWhileWorking,
                boolean payPlaytimeMilestones,
                boolean claimBonusWhenOnline,
                boolean autoClaimBankTopup,
                double bankReserveGold,
                double walletFloatGold,
                boolean autoExpand,
                int expandMaxPerHour) {
            this.enabled = enabled;
            this.tickSeconds = tickSeconds;
            this.creditPlaytimeWhileWorking = creditPlaytimeWhileWorking;
            this.payPlaytimeMilestones = payPlaytimeMilestones;
            this.claimBonusWhenOnline = claimBonusWhenOnline;
            this.autoClaimBankTopup = autoClaimBankTopup;
            this.bankReserveGold = bankReserveGold;
            this.walletFloatGold = walletFloatGold;
            this.autoExpand = autoExpand;
            this.expandMaxPerHour = expandMaxPerHour;
        }

        static EconomyConfig from(ConfigurationSection sec) {
            if (sec == null) {
                return new EconomyConfig(true, 60, true, true, true, true, 150, 25, true, 2);
            }
            return new EconomyConfig(
                    sec.getBoolean("enabled", true),
                    Math.max(15, sec.getLong("tick-seconds", 60)),
                    sec.getBoolean("credit-playtime-while-working", true),
                    sec.getBoolean("pay-playtime-milestones", true),
                    sec.getBoolean("claim-bonus-when-online", true),
                    sec.getBoolean("auto-claim-bank-topup", true),
                    Math.max(0, sec.getDouble("bank-reserve-gold", 150)),
                    Math.max(0, sec.getDouble("wallet-float-gold", 25)),
                    sec.getBoolean("auto-expand", true),
                    Math.max(0, sec.getInt("expand-max-per-hour", 2)));
        }

        public boolean enabled() {
            return enabled;
        }

        public long tickSeconds() {
            return tickSeconds;
        }

        public boolean creditPlaytimeWhileWorking() {
            return creditPlaytimeWhileWorking;
        }

        public boolean payPlaytimeMilestones() {
            return payPlaytimeMilestones;
        }

        public boolean claimBonusWhenOnline() {
            return claimBonusWhenOnline;
        }

        public boolean autoClaimBankTopup() {
            return autoClaimBankTopup;
        }

        public double bankReserveGold() {
            return bankReserveGold;
        }

        public double walletFloatGold() {
            return walletFloatGold;
        }

        public boolean autoExpand() {
            return autoExpand;
        }

        public int expandMaxPerHour() {
            return expandMaxPerHour;
        }
    }

    /** Claim AABB → .schem + summary JSON for Ava core vision / build-sim. */
    public static final class SchematicConfig {
        private final boolean enabled;
        private final int yBelow;
        private final int yAbove;
        private final boolean buildEnabled;
        private final int buildBlocksPerTick;
        private final int buildPeriodTicks;
        private final long autoCaptureSeconds;
        private final boolean includeTerritory;
        private final int territoryExtraBlocks;

        private SchematicConfig(
                boolean enabled,
                int yBelow,
                int yAbove,
                boolean buildEnabled,
                int buildBlocksPerTick,
                int buildPeriodTicks,
                long autoCaptureSeconds,
                boolean includeTerritory,
                int territoryExtraBlocks) {
            this.enabled = enabled;
            this.yBelow = yBelow;
            this.yAbove = yAbove;
            this.buildEnabled = buildEnabled;
            this.buildBlocksPerTick = buildBlocksPerTick;
            this.buildPeriodTicks = buildPeriodTicks;
            this.autoCaptureSeconds = autoCaptureSeconds;
            this.includeTerritory = includeTerritory;
            this.territoryExtraBlocks = territoryExtraBlocks;
        }

        static SchematicConfig from(ConfigurationSection sec) {
            if (sec == null) {
                return new SchematicConfig(true, 32, 48, true, 6, 4, 300, true, 24);
            }
            return new SchematicConfig(
                    sec.getBoolean("enabled", true),
                    Math.max(0, sec.getInt("y-below", 32)),
                    Math.max(0, sec.getInt("y-above", 48)),
                    sec.getBoolean("build-enabled", true),
                    Math.max(1, sec.getInt("build-blocks-per-tick", 6)),
                    Math.max(1, sec.getInt("build-period-ticks", 4)),
                    Math.max(0, sec.getLong("auto-capture-seconds", 300)),
                    sec.getBoolean("include-territory", true),
                    Math.max(0, sec.getInt("territory-extra-blocks", 24)));
        }

        public boolean enabled() {
            return enabled;
        }

        public int yBelow() {
            return yBelow;
        }

        public int yAbove() {
            return yAbove;
        }

        public boolean buildEnabled() {
            return buildEnabled;
        }

        public int buildBlocksPerTick() {
            return buildBlocksPerTick;
        }

        public int buildPeriodTicks() {
            return buildPeriodTicks;
        }

        /** 0 = off; otherwise periodic auto-capture of Ava claims. */
        public long autoCaptureSeconds() {
            return autoCaptureSeconds;
        }

        public boolean includeTerritory() {
            return includeTerritory;
        }

        /** Extra blocks past claim radius for vision capture (capped territory). */
        public int territoryExtraBlocks() {
            return territoryExtraBlocks;
        }
    }

    /** Idle claim work when not hired — mine materials, chests, gifts. */
    public static final class AutonomyConfig {
        private final boolean enabled;
        private final long tickSeconds;
        private final int gatherBlocksPerTick;
        private final int gatherRadius;
        private final int maxChests;
        private final int vacuumRadius;
        private final boolean pauseWhenHired;
        private final boolean placeChests;
        private final boolean buildEnabled;
        private final int buildBlocksPerTick;
        private final boolean workTerritory;

        private AutonomyConfig(
                boolean enabled,
                long tickSeconds,
                int gatherBlocksPerTick,
                int gatherRadius,
                int maxChests,
                int vacuumRadius,
                boolean pauseWhenHired,
                boolean placeChests,
                boolean buildEnabled,
                int buildBlocksPerTick,
                boolean workTerritory) {
            this.enabled = enabled;
            this.tickSeconds = tickSeconds;
            this.gatherBlocksPerTick = gatherBlocksPerTick;
            this.gatherRadius = gatherRadius;
            this.maxChests = maxChests;
            this.vacuumRadius = vacuumRadius;
            this.pauseWhenHired = pauseWhenHired;
            this.placeChests = placeChests;
            this.buildEnabled = buildEnabled;
            this.buildBlocksPerTick = buildBlocksPerTick;
            this.workTerritory = workTerritory;
        }

        static AutonomyConfig from(ConfigurationSection sec) {
            if (sec == null) {
                return new AutonomyConfig(true, 8, 8, 16, 12, 6, false, true, true, 4, true);
            }
            return new AutonomyConfig(
                    sec.getBoolean("enabled", true),
                    Math.max(3, sec.getLong("tick-seconds", 8)),
                    Math.max(1, sec.getInt("gather-blocks-per-tick", 8)),
                    Math.max(4, sec.getInt("gather-radius", 16)),
                    Math.max(1, sec.getInt("max-chests", 12)),
                    Math.max(2, sec.getInt("vacuum-radius", 6)),
                    sec.getBoolean("pause-when-hired", false),
                    sec.getBoolean("place-chests", true),
                    sec.getBoolean("build-enabled", true),
                    Math.max(1, sec.getInt("build-blocks-per-tick", 4)),
                    sec.getBoolean("work-territory", true));
        }

        public boolean enabled() {
            return enabled;
        }

        public long tickSeconds() {
            return tickSeconds;
        }

        public int gatherBlocksPerTick() {
            return gatherBlocksPerTick;
        }

        public int gatherRadius() {
            return gatherRadius;
        }

        public int maxChests() {
            return maxChests;
        }

        public int vacuumRadius() {
            return vacuumRadius;
        }

        public boolean pauseWhenHired() {
            return pauseWhenHired;
        }

        public boolean placeChests() {
            return placeChests;
        }

        public boolean buildEnabled() {
            return buildEnabled;
        }

        public int buildBlocksPerTick() {
            return buildBlocksPerTick;
        }

        public boolean workTerritory() {
            return workTerritory;
        }
    }

    public static final class HireConfig {
        private final boolean enabled;
        private final double sidekickGold;
        private final double sidekickPerMinute;
        private final int sidekickMaxMinutes;
        private final double mineBase;
        private final double minePerBlock;
        private final double teardownBase;
        private final double teardownPerBlock;
        private final double chunkBase;
        private final double chunkPerBlock;
        private final int chunkMaxVolume;
        private final double minedownBase;
        private final double minedownPerBlock;
        private final int minedownMaxVolume;
        private final int minedownSize;
        private final int defaultRadius;
        private final int maxRadius;
        private final int maxVolume;
        private final int blocksPerTick;
        private final int tickPeriod;
        private final int teardownDepth;
        private final int concurrentJobs;
        private final int hourMinutes;
        private final double hourGold;

        private HireConfig(
                boolean enabled,
                double sidekickGold,
                double sidekickPerMinute,
                int sidekickMaxMinutes,
                double mineBase,
                double minePerBlock,
                double teardownBase,
                double teardownPerBlock,
                double chunkBase,
                double chunkPerBlock,
                int chunkMaxVolume,
                double minedownBase,
                double minedownPerBlock,
                int minedownMaxVolume,
                int minedownSize,
                int defaultRadius,
                int maxRadius,
                int maxVolume,
                int blocksPerTick,
                int tickPeriod,
                int teardownDepth,
                int concurrentJobs,
                int hourMinutes,
                double hourGold) {
            this.enabled = enabled;
            this.sidekickGold = sidekickGold;
            this.sidekickPerMinute = sidekickPerMinute;
            this.sidekickMaxMinutes = sidekickMaxMinutes;
            this.mineBase = mineBase;
            this.minePerBlock = minePerBlock;
            this.teardownBase = teardownBase;
            this.teardownPerBlock = teardownPerBlock;
            this.chunkBase = chunkBase;
            this.chunkPerBlock = chunkPerBlock;
            this.chunkMaxVolume = chunkMaxVolume;
            this.minedownBase = minedownBase;
            this.minedownPerBlock = minedownPerBlock;
            this.minedownMaxVolume = minedownMaxVolume;
            this.minedownSize = minedownSize;
            this.defaultRadius = defaultRadius;
            this.maxRadius = maxRadius;
            this.maxVolume = maxVolume;
            this.blocksPerTick = blocksPerTick;
            this.tickPeriod = tickPeriod;
            this.teardownDepth = teardownDepth;
            this.concurrentJobs = concurrentJobs;
            this.hourMinutes = hourMinutes;
            this.hourGold = hourGold;
        }

        static HireConfig from(ConfigurationSection sec) {
            if (sec == null) {
                return new HireConfig(
                        true, 1, 0, 30, 1, 0, 1, 0, 0, 0.01, 500000, 0, 0.01, 8000, 3,
                        8, 24, 12000, 8, 20, 12, 6, 60, 1);
            }
            return new HireConfig(
                    sec.getBoolean("enabled", true),
                    Math.max(0, sec.getDouble("sidekick-gold", 1)),
                    Math.max(0, sec.getDouble("sidekick-gold-per-minute", 0)),
                    Math.max(1, sec.getInt("sidekick-max-minutes", 30)),
                    Math.max(0, sec.getDouble("mine-base-gold", 1)),
                    Math.max(0, sec.getDouble("mine-gold-per-block", 0)),
                    Math.max(0, sec.getDouble("teardown-base-gold", 1)),
                    Math.max(0, sec.getDouble("teardown-gold-per-block", 0)),
                    Math.max(0, sec.getDouble("chunk-base-gold", 0)),
                    Math.max(0, sec.getDouble("chunk-gold-per-block", 0.01)),
                    Math.max(100, sec.getInt("chunk-max-volume", 500000)),
                    Math.max(0, sec.getDouble("minedown-base-gold", 0)),
                    Math.max(0, sec.getDouble("minedown-gold-per-block", 0.01)),
                    Math.max(100, sec.getInt("minedown-max-volume", 8000)),
                    Math.max(1, sec.getInt("minedown-size", 3)),
                    Math.max(1, sec.getInt("default-radius", 8)),
                    Math.max(1, sec.getInt("max-radius", 24)),
                    Math.max(100, sec.getInt("max-volume", 12000)),
                    Math.max(1, sec.getInt("blocks-per-tick", 8)),
                    Math.max(1, sec.getInt("tick-period", 20)),
                    Math.max(1, sec.getInt("teardown-depth", 12)),
                    Math.max(1, sec.getInt("concurrent-jobs", 6)),
                    Math.max(1, sec.getInt("hour-minutes", 60)),
                    Math.max(0, sec.getDouble("hour-gold", 1)));
        }

        public boolean enabled() {
            return enabled;
        }

        public double sidekickGold() {
            return sidekickGold;
        }

        public double sidekickPerMinute() {
            return sidekickPerMinute;
        }

        public int sidekickMaxMinutes() {
            return sidekickMaxMinutes;
        }

        public double mineBase() {
            return mineBase;
        }

        public double minePerBlock() {
            return minePerBlock;
        }

        public double teardownBase() {
            return teardownBase;
        }

        public double teardownPerBlock() {
            return teardownPerBlock;
        }

        public int defaultRadius() {
            return defaultRadius;
        }

        public int maxRadius() {
            return maxRadius;
        }

        public int maxVolume() {
            return maxVolume;
        }

        public int blocksPerTick() {
            return blocksPerTick;
        }

        public int tickPeriod() {
            return tickPeriod;
        }

        public int teardownDepth() {
            return teardownDepth;
        }

        public int concurrentJobs() {
            return concurrentJobs;
        }

        public int hourMinutes() {
            return hourMinutes;
        }

        public double hourGold() {
            return hourGold;
        }

        public double chunkBase() {
            return chunkBase;
        }

        public double chunkPerBlock() {
            return chunkPerBlock;
        }

        public int chunkMaxVolume() {
            return chunkMaxVolume;
        }

        public double minedownBase() {
            return minedownBase;
        }

        public double minedownPerBlock() {
            return minedownPerBlock;
        }

        public int minedownMaxVolume() {
            return minedownMaxVolume;
        }

        public int minedownSize() {
            return minedownSize;
        }
    }

    public static final class PowersConfig {
        private final boolean invulnerable;
        private final boolean judgmentKill;
        private final boolean godGear;
        private final boolean permanentBuffs;
        private final int hasteAmplifier;
        private final int speedAmplifier;
        private final UUID avaUuid;
        private final String avaName;
        private final List<String> neverKillNames;
        private final List<UUID> neverKillUuids;
        private final int territoryBufferBlocks;

        private PowersConfig(
                boolean invulnerable,
                boolean judgmentKill,
                boolean godGear,
                boolean permanentBuffs,
                int hasteAmplifier,
                int speedAmplifier,
                UUID avaUuid,
                String avaName,
                List<String> neverKillNames,
                List<UUID> neverKillUuids,
                int territoryBufferBlocks) {
            this.invulnerable = invulnerable;
            this.judgmentKill = judgmentKill;
            this.godGear = godGear;
            this.permanentBuffs = permanentBuffs;
            this.hasteAmplifier = hasteAmplifier;
            this.speedAmplifier = speedAmplifier;
            this.avaUuid = avaUuid;
            this.avaName = avaName;
            this.neverKillNames = neverKillNames;
            this.neverKillUuids = neverKillUuids;
            this.territoryBufferBlocks = territoryBufferBlocks;
        }

        static PowersConfig from(ConfigurationSection sec) {
            UUID uuid = parseUuid(
                    sec != null
                            ? sec.getString("ava-uuid", "78c3de61-0fd6-4800-9eda-cc178eaae34b")
                            : "78c3de61-0fd6-4800-9eda-cc178eaae34b");
            List<String> names = new ArrayList<>();
            if (sec != null) {
                for (String n : sec.getStringList("never-kill-names")) {
                    if (n != null && !n.isBlank()) names.add(n.trim().toLowerCase(Locale.ROOT));
                }
            }
            if (names.isEmpty()) {
                names.add("alexrs94");
                names.add("melee__");
                names.add("melee");
            }
            List<UUID> uuids = new ArrayList<>();
            if (sec != null) {
                for (String u : sec.getStringList("never-kill-uuids")) {
                    UUID parsed = parseUuid(u);
                    if (parsed != null) uuids.add(parsed);
                }
            }
            if (uuids.isEmpty()) {
                UUID alex = parseUuid("3e660994-b16c-4714-bc15-9081aa928729");
                if (alex != null) uuids.add(alex);
            }
            return new PowersConfig(
                    sec == null || sec.getBoolean("invulnerable", true),
                    sec == null || sec.getBoolean("judgment-kill", true),
                    sec == null || sec.getBoolean("god-gear", true),
                    sec == null || sec.getBoolean("permanent-buffs", true),
                    Math.max(0, sec != null ? sec.getInt("haste-amplifier", 2) : 2),
                    Math.max(0, sec != null ? sec.getInt("speed-amplifier", 1) : 1),
                    uuid,
                    sec != null ? sec.getString("ava-name", "Ava_Ivy") : "Ava_Ivy",
                    names,
                    uuids,
                    Math.max(0, sec != null ? sec.getInt("territory-buffer-blocks", 48) : 48));
        }

        static PowersConfig defaults() {
            return from(null);
        }

        private static UUID parseUuid(String raw) {
            if (raw == null || raw.isBlank()) return null;
            try {
                String s = raw.trim();
                if (!s.contains("-") && s.length() == 32) {
                    s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                            + "-" + s.substring(16, 20) + "-" + s.substring(20);
                }
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public boolean invulnerable() {
            return invulnerable;
        }

        public boolean judgmentKill() {
            return judgmentKill;
        }

        public boolean godGear() {
            return godGear;
        }

        public boolean permanentBuffs() {
            return permanentBuffs;
        }

        public int hasteAmplifier() {
            return hasteAmplifier;
        }

        public int speedAmplifier() {
            return speedAmplifier;
        }

        public UUID avaUuid() {
            return avaUuid;
        }

        public String avaName() {
            return avaName;
        }

        public List<String> neverKillNames() {
            return neverKillNames;
        }

        public List<UUID> neverKillUuids() {
            return neverKillUuids;
        }

        public int territoryBufferBlocks() {
            return territoryBufferBlocks;
        }

        public boolean isAvaPlayer(UUID id, String name) {
            if (avaUuid != null && id != null && avaUuid.equals(id)) return true;
            if (name != null && avaName != null && avaName.equalsIgnoreCase(name.trim())) return true;
            return false;
        }

        public boolean isNeverKill(UUID id, String name) {
            if (id != null) {
                for (UUID u : neverKillUuids) {
                    if (u.equals(id)) return true;
                }
            }
            if (name != null) {
                String n = name.trim().toLowerCase(Locale.ROOT);
                return neverKillNames.contains(n);
            }
            return false;
        }
    }

    public static final class PresenceConfig {
        private final boolean enabled;
        private final boolean invulnerable;
        private final String stack;
        private final String displayName;
        private final String skinName;
        private final String skinUuid;
        private final String skinTexture;
        private final String skinTextureSignature;
        private final boolean wanderEnabled;
        private final double wanderRadius;
        private final long wanderIntervalTicks;
        private final double followDistance;
        private final double followMaxDistance;
        private final long followIntervalTicks;
        private final boolean followTeleportIfFar;
        private final boolean useWorldSpawn;
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;

        private PresenceConfig(
                boolean enabled,
                boolean invulnerable,
                String stack,
                String displayName,
                String skinName,
                String skinUuid,
                String skinTexture,
                String skinTextureSignature,
                boolean wanderEnabled,
                double wanderRadius,
                long wanderIntervalTicks,
                double followDistance,
                double followMaxDistance,
                long followIntervalTicks,
                boolean followTeleportIfFar,
                boolean useWorldSpawn,
                String world,
                double x,
                double y,
                double z,
                float yaw) {
            this.enabled = enabled;
            this.invulnerable = invulnerable;
            this.stack = stack;
            this.displayName = displayName;
            this.skinName = skinName;
            this.skinUuid = skinUuid;
            this.skinTexture = skinTexture;
            this.skinTextureSignature = skinTextureSignature;
            this.wanderEnabled = wanderEnabled;
            this.wanderRadius = wanderRadius;
            this.wanderIntervalTicks = wanderIntervalTicks;
            this.followDistance = followDistance;
            this.followMaxDistance = followMaxDistance;
            this.followIntervalTicks = followIntervalTicks;
            this.followTeleportIfFar = followTeleportIfFar;
            this.useWorldSpawn = useWorldSpawn;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        static PresenceConfig from(ConfigurationSection sec) {
            if (sec == null) {
                return defaults();
            }
            ConfigurationSection follow = sec.getConfigurationSection("follow");
            return new PresenceConfig(
                    sec.getBoolean("enabled", true),
                    sec.getBoolean("invulnerable", true),
                    sec.getString("stack", "native-mannequin"),
                    sec.getString("display-name", "&bAva Ivy"),
                    sec.getString("skin-name", "Ava_Ivy"),
                    sec.getString("skin-uuid", "78c3de61-0fd6-4800-9eda-cc178eaae34b"),
                    sec.getString("skin-texture", ""),
                    sec.getString("skin-texture-signature", ""),
                    sec.getBoolean("wander.enabled", true),
                    sec.getDouble("wander.radius", 4.0),
                    sec.getLong("wander.interval-ticks", 80L),
                    follow != null ? follow.getDouble("distance", 2.5) : 2.5,
                    follow != null ? follow.getDouble("max-distance", 48.0) : 48.0,
                    follow != null ? follow.getLong("interval-ticks", 10L) : 10L,
                    follow == null || follow.getBoolean("teleport-if-far", true),
                    sec.getBoolean("use-world-spawn", true),
                    sec.getString("world", ""),
                    sec.getDouble("x", 0.5),
                    sec.getDouble("y", 64.0),
                    sec.getDouble("z", 0.5),
                    (float) sec.getDouble("yaw", 0.0));
        }

        static PresenceConfig defaults() {
            return from(null);
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean invulnerable() {
            return invulnerable;
        }

        public String stack() {
            return stack;
        }

        public String displayName() {
            return displayName;
        }

        public String skinName() {
            return skinName;
        }

        public String skinUuid() {
            return skinUuid;
        }

        public String skinTexture() {
            return skinTexture;
        }

        public String skinTextureSignature() {
            return skinTextureSignature;
        }

        public boolean wanderEnabled() {
            return wanderEnabled;
        }

        public double wanderRadius() {
            return wanderRadius;
        }

        public long wanderIntervalTicks() {
            return wanderIntervalTicks;
        }

        public double followDistance() {
            return followDistance;
        }

        public double followMaxDistance() {
            return followMaxDistance;
        }

        public long followIntervalTicks() {
            return followIntervalTicks;
        }

        public boolean followTeleportIfFar() {
            return followTeleportIfFar;
        }

        public boolean useWorldSpawn() {
            return useWorldSpawn;
        }

        public String world() {
            return world;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public float yaw() {
            return yaw;
        }
    }
}
