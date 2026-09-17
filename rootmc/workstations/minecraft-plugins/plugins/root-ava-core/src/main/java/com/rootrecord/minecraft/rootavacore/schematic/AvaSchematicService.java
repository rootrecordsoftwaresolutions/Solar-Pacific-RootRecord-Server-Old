package com.rootrecord.minecraft.rootavacore.schematic;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.rootavacore.AvaConfig;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import com.rootrecord.minecraft.rootavacore.presence.AvaPresenceService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Claim → .schem + summary JSON for Ava core vision; paced rebuild for build-sim.
 */
public final class AvaSchematicService {

    public static final String SCHEMATICS_DIR = RootRecordFolders.AVA_SCHEMATICS_DIR;

    private final RootAvaCorePlugin plugin;
    private final AtomicReference<ClaimSchematicCapture.CaptureResult> lastCapture = new AtomicReference<>();
    private final AtomicReference<String> lastStem = new AtomicReference<>();
    private final AtomicInteger buildCursor = new AtomicInteger(0);
    private BukkitTask buildTask;
    private BukkitTask autoTask;
    private String lastNote = "idle";

    public AvaSchematicService(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stopAuto();
        AvaConfig.SchematicConfig cfg = plugin.config().schematics();
        if (!cfg.enabled() || cfg.autoCaptureSeconds() <= 0) {
            lastNote = cfg.enabled() ? "auto_off" : "disabled";
            return;
        }
        long period = Math.max(60L, cfg.autoCaptureSeconds()) * 20L;
        // First snap ~45s after boot (claims plugin ready), then on interval
        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoCaptureTick, 45L * 20L, period);
        plugin.getLogger().info("Ava schem auto-capture every " + cfg.autoCaptureSeconds() + "s");
    }

    public void stop() {
        stopBuild();
        stopAuto();
    }

    private void stopAuto() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    private void autoCaptureTick() {
        AvaConfig.SchematicConfig cfg = plugin.config().schematics();
        if (!cfg.enabled() || cfg.autoCaptureSeconds() <= 0) return;
        if (!plugin.config().enabled()) return;
        List<String> lines = captureAll();
        int n = 0;
        for (String line : lines) {
            if (line.startsWith("SUMMARY ")) n++;
        }
        if (n > 0) {
            plugin.getLogger().info("Ava schem auto-capture · " + n + " claim(s) · " + lastNote);
        }
    }

    public Path schematicsDir() {
        Path dir = RootRecordFolders.dir(plugin).toPath().resolve(SCHEMATICS_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    public String lastNote() {
        return lastNote;
    }

    public ClaimSchematicCapture.CaptureResult lastCapture() {
        return lastCapture.get();
    }

    public List<Object> ownedClaims() {
        List<Object> out = new ArrayList<>();
        try {
            org.bukkit.plugin.Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled()) return out;
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            UUID ava = plugin.config().powers().avaUuid();
            @SuppressWarnings("unchecked")
            List<Object> owned = (List<Object>) service.getClass()
                    .getMethod("ownedBy", UUID.class)
                    .invoke(service, ava);
            if (owned != null) out.addAll(owned);
        } catch (Throwable t) {
            plugin.getLogger().fine("schem claims: " + t.getMessage());
        }
        return out;
    }

    /** Capture every Ava-owned claim. Returns human summary lines. */
    public List<String> captureAll() {
        AvaConfig.SchematicConfig cfg = plugin.config().schematics();
        if (!cfg.enabled()) {
            lastNote = "disabled";
            return List.of("SCHEM disabled");
        }
        List<Object> claims = ownedClaims();
        if (claims.isEmpty()) {
            lastNote = "no_claims";
            return List.of("SCHEM 0");
        }
        List<String> lines = new ArrayList<>();
        lines.add("SCHEM " + claims.size());
        int ok = 0;
        for (Object claim : claims) {
            String line = captureOne(claim, cfg);
            lines.add(line);
            if (line.startsWith("SUMMARY ")) ok++;
        }
        lastNote = "captured " + ok + "/" + claims.size();
        return lines;
    }

    public String captureOne(Object claim, AvaConfig.SchematicConfig cfg) {
        int extra = cfg.includeTerritory() ? cfg.territoryExtraBlocks() : 0;
        ClaimSchematicCapture.CaptureResult cap =
                ClaimSchematicCapture.capture(claim, cfg.yBelow(), cfg.yAbove(), extra);
        if (cap == null) {
            return "SUMMARY error bad_claim";
        }
        String stem = ClaimSchematicCapture.safeFileStem(cap);
        Path dir = schematicsDir();
        try {
            byte[] schem = SpongeSchematicWriter.write(cap, stem);
            Files.write(dir.resolve(stem + ".schem"), schem);
            String summaryJson = buildSummaryJson(cap, stem);
            Files.writeString(dir.resolve(stem + ".json"), summaryJson, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(stem + ".cap.json"), buildCapJson(cap), StandardCharsets.UTF_8);
            lastCapture.set(cap);
            lastStem.set(stem);
            buildCursor.set(0);
            return summaryLine(cap, stem);
        } catch (Exception e) {
            plugin.getLogger().warning("schem capture failed: " + e.getMessage());
            return "SUMMARY error " + e.getClass().getSimpleName();
        }
    }

    /** Compact RCON-friendly summary for Ava core ingest. */
    public String summaryLine(ClaimSchematicCapture.CaptureResult cap, String stem) {
        StringBuilder top = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : ClaimSchematicCapture.topMaterials(cap, 8)) {
            if (n++ > 0) top.append(',');
            String id = e.getKey().replace("minecraft:", "");
            top.append(id).append(':').append(e.getValue());
        }
        return "SUMMARY "
                + cap.world() + " "
                + cap.claimX() + " " + cap.claimY() + " " + cap.claimZ() + " " + cap.claimRadius()
                + " " + cap.width() + "x" + cap.height() + "x" + cap.length()
                + " nonAir=" + cap.nonAir()
                + " file=" + stem
                + " top=" + top;
    }

    public List<String> dumpSummaries() {
        List<String> lines = new ArrayList<>();
        Path dir = schematicsDir();
        try {
            if (!Files.isDirectory(dir)) {
                lines.add("SCHEM 0");
                return lines;
            }
            List<Path> jsons = Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().endsWith(".cap.json"))
                    .sorted()
                    .toList();
            lines.add("SCHEM " + jsons.size());
            for (Path p : jsons) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                // Prefer live SUMMARY if we still have matching capture; else rehydrate from json fields
                String stem = p.getFileName().toString().replace(".json", "");
                ClaimSchematicCapture.CaptureResult cap = lastCapture.get();
                if (cap != null && stem.equals(lastStem.get())) {
                    lines.add(summaryLine(cap, stem));
                } else {
                    lines.add(summaryFromJsonFile(text, stem));
                }
            }
        } catch (Exception e) {
            lines.add("SCHEM error " + e.getClass().getSimpleName());
        }
        return lines;
    }

    private static String summaryFromJsonFile(String json, String stem) {
        // Minimal field scrape without a JSON lib
        String world = scrape(json, "world");
        String cx = scrape(json, "claimX");
        String cy = scrape(json, "claimY");
        String cz = scrape(json, "claimZ");
        String r = scrape(json, "claimRadius");
        String nonAir = scrape(json, "nonAir");
        String dims = scrape(json, "dims");
        return "SUMMARY "
                + nullTo(world, "?") + " "
                + nullTo(cx, "0") + " " + nullTo(cy, "0") + " " + nullTo(cz, "0") + " " + nullTo(r, "0")
                + " " + nullTo(dims, "?")
                + " nonAir=" + nullTo(nonAir, "0")
                + " file=" + stem
                + " top=";
    }

    private static String scrape(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        if (j >= json.length()) return null;
        if (json.charAt(j) == '"') {
            int end = json.indexOf('"', j + 1);
            return end > j ? json.substring(j + 1, end) : null;
        }
        int end = j;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) break;
            end++;
        }
        return json.substring(j, end);
    }

    private static String nullTo(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }

    private String buildSummaryJson(ClaimSchematicCapture.CaptureResult cap, String stem) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n");
        sb.append("  \"capturedAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"world\": \"").append(escape(cap.world())).append("\",\n");
        sb.append("  \"claimX\": ").append(cap.claimX()).append(",\n");
        sb.append("  \"claimY\": ").append(cap.claimY()).append(",\n");
        sb.append("  \"claimZ\": ").append(cap.claimZ()).append(",\n");
        sb.append("  \"claimRadius\": ").append(cap.claimRadius()).append(",\n");
        sb.append("  \"dims\": \"")
                .append(cap.width()).append('x').append(cap.height()).append('x').append(cap.length())
                .append("\",\n");
        sb.append("  \"origin\": [")
                .append(cap.originX()).append(',').append(cap.originY()).append(',').append(cap.originZ())
                .append("],\n");
        sb.append("  \"nonAir\": ").append(cap.nonAir()).append(",\n");
        sb.append("  \"blocks\": ").append(cap.blockIndices().length).append(",\n");
        sb.append("  \"file\": \"").append(escape(stem)).append("\",\n");
        sb.append("  \"schem\": \"").append(escape(stem)).append(".schem\",\n");
        sb.append("  \"topMaterials\": [\n");
        List<Map.Entry<String, Integer>> top = ClaimSchematicCapture.topMaterials(cap, 12);
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<String, Integer> e = top.get(i);
            sb.append("    {\"id\": \"").append(escape(e.getKey())).append("\", \"count\": ")
                    .append(e.getValue()).append('}');
            if (i + 1 < top.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Full voxel sidecar so rebuild survives without re-parsing .schem. */
    private String buildCapJson(ClaimSchematicCapture.CaptureResult cap) {
        StringBuilder sb = new StringBuilder(cap.blockIndices().length * 2 + 256);
        sb.append("{\n");
        sb.append("  \"world\": \"").append(escape(cap.world())).append("\",\n");
        sb.append("  \"claimX\": ").append(cap.claimX()).append(",\n");
        sb.append("  \"claimY\": ").append(cap.claimY()).append(",\n");
        sb.append("  \"claimZ\": ").append(cap.claimZ()).append(",\n");
        sb.append("  \"claimRadius\": ").append(cap.claimRadius()).append(",\n");
        sb.append("  \"width\": ").append(cap.width()).append(",\n");
        sb.append("  \"height\": ").append(cap.height()).append(",\n");
        sb.append("  \"length\": ").append(cap.length()).append(",\n");
        sb.append("  \"originX\": ").append(cap.originX()).append(",\n");
        sb.append("  \"originY\": ").append(cap.originY()).append(",\n");
        sb.append("  \"originZ\": ").append(cap.originZ()).append(",\n");
        sb.append("  \"palette\": [");
        for (int i = 0; i < cap.palette().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(cap.palette().get(i))).append('"');
        }
        sb.append("],\n");
        sb.append("  \"blocks\": [");
        int[] idx = cap.blockIndices();
        for (int i = 0; i < idx.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(idx[i]);
        }
        sb.append("]\n}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public boolean isBuilding() {
        return buildTask != null;
    }

    public void stopBuild() {
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
        lastNote = "build_stopped";
    }

    /**
     * Pace-place non-air blocks from last capture where the world is air (simulate Ava building).
     */
    public String startRebuild() {
        AvaConfig.SchematicConfig cfg = plugin.config().schematics();
        if (!cfg.enabled() || !cfg.buildEnabled()) {
            lastNote = "build_disabled";
            return "build disabled";
        }
        ClaimSchematicCapture.CaptureResult cap = lastCapture.get();
        if (cap == null) {
            lastNote = "no_capture";
            return "capture first";
        }
        stopBuild();
        buildCursor.set(0);
        int period = Math.max(1, cfg.buildPeriodTicks());
        int budget = Math.max(1, cfg.buildBlocksPerTick());
        buildTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int placed = placeBudget(cap, budget);
            if (placed == 0 && buildCursor.get() >= cap.blockIndices().length) {
                stopBuild();
                lastNote = "build_done";
                plugin.getLogger().info("Ava schem rebuild complete (" + ClaimSchematicCapture.safeFileStem(cap) + ")");
            } else if (placed > 0) {
                lastNote = "build +" + placed + " @ " + buildCursor.get();
            }
        }, 5L, period);
        lastNote = "build_started";
        return "rebuild started · " + cap.nonAir() + " non-air · " + budget + "/tick";
    }

    private int placeBudget(ClaimSchematicCapture.CaptureResult cap, int budget) {
        World world = Bukkit.getWorld(cap.world());
        if (world == null) return 0;
        int placed = 0;
        int i = buildCursor.get();
        int w = cap.width();
        int l = cap.length();
        Location last = null;
        while (i < cap.blockIndices().length && placed < budget) {
            int paletteIdx = cap.blockIndices()[i];
            String state = paletteIdx >= 0 && paletteIdx < cap.palette().size()
                    ? cap.palette().get(paletteIdx)
                    : "minecraft:air";
            int y = i / (w * l);
            int rem = i % (w * l);
            int z = rem / w;
            int x = rem % w;
            i++;
            if (state.contains("air") || state.contains("cave_air") || state.contains("void_air")) {
                continue;
            }
            int bx = cap.originX() + x;
            int by = cap.originY() + y;
            int bz = cap.originZ() + z;
            var block = world.getBlockAt(bx, by, bz);
            if (!block.getType().isAir()) {
                continue;
            }
            try {
                BlockData data = Bukkit.createBlockData(state);
                if (data.getMaterial() == Material.AIR) continue;
                block.setBlockData(data, false);
                placed++;
                last = block.getLocation();
            } catch (IllegalArgumentException ignored) {
                // unknown state — skip
            }
        }
        buildCursor.set(i);
        if (last != null) {
            AvaPresenceService presence = plugin.presence();
            if (presence != null) {
                presence.appearWorking(last);
            }
        }
        return placed;
    }
}
