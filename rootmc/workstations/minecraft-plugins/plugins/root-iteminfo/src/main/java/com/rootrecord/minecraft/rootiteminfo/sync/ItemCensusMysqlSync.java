package com.rootrecord.minecraft.rootiteminfo.sync;

import com.rootrecord.minecraft.rootiteminfo.RootItemInfoPlugin;
import com.rootrecord.minecraft.rootiteminfo.census.ItemCensusScanner;
import com.rootrecord.minecraft.rootiteminfo.census.ItemCensusStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/** Persists the latest census through RootMC's shared MySQL reporting store. */
public final class ItemCensusMysqlSync {

    private final RootItemInfoPlugin plugin;
    private final ItemCensusStore store;
    private final Object writeLock = new Object();
    private volatile long lastSavedScanMs;
    private volatile long lastWriteAtMs;
    private volatile int lastSnapshotHash;

    public ItemCensusMysqlSync(RootItemInfoPlugin plugin, ItemCensusStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void saveIfChanged() {
        synchronized (writeLock) {
            long scannedAt = store.scannedAtEpochMs();
            if (scannedAt <= 0 || scannedAt <= lastSavedScanMs) {
                return;
            }
            Plugin rootMc = Bukkit.getPluginManager().getPlugin("RootMC");
            if (rootMc == null || !rootMc.isEnabled()) {
                plugin.getLogger().warning("Item census MySQL save skipped - RootMC is unavailable.");
                return;
            }
            try {
                Map<String, Long> counts = store.snapshot();
                String scanNote = store.scanNote();
                if (counts.isEmpty() && scanNote.contains("players=0") && scanNote.contains("chunks=0")) {
                    return;
                }
                int snapshotHash = counts.hashCode();
                if (lastSavedScanMs > 0 && snapshotHash == lastSnapshotHash) {
                    lastSavedScanMs = scannedAt;
                    return;
                }
                long minWriteMs = Math.max(
                                30,
                                plugin.configFile() == null
                                        ? 120
                                        : plugin.configFile().getLong("mysql.min-write-interval-seconds", 120))
                        * 1000L;
                if (lastWriteAtMs > 0 && System.currentTimeMillis() - lastWriteAtMs < minWriteMs) {
                    return;
                }
                Map<String, Double> averages = new LinkedHashMap<>();
                Map<String, Double> mintPegTotals = new LinkedHashMap<>();
                double goldMintPegG = 0;
                for (Map.Entry<String, Long> entry : counts.entrySet()) {
                    Material material = Material.matchMaterial(entry.getKey());
                    if (material == null) {
                        continue;
                    }
                    plugin.values().averageUnitG(material).ifPresent(
                            average -> averages.put(entry.getKey(), average));
                    double peg = ItemCensusScanner.mintPegG(material);
                    if (peg > 0) {
                        double total = peg * entry.getValue();
                        mintPegTotals.put(entry.getKey(), total);
                        goldMintPegG += total;
                    }
                }
                rootMc.getClass()
                        .getMethod(
                                "persistItemCensus",
                                long.class,
                                String.class,
                                Map.class,
                                Map.class,
                                Map.class,
                                double.class)
                        .invoke(
                                rootMc,
                                scannedAt,
                                scanNote,
                                counts,
                                averages,
                                mintPegTotals,
                                goldMintPegG);
                lastSavedScanMs = scannedAt;
                lastWriteAtMs = System.currentTimeMillis();
                lastSnapshotHash = snapshotHash;
                plugin.getLogger().info("Item census saved to MySQL (" + counts.size() + " items).");
            } catch (ReflectiveOperationException ex) {
                Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null
                        ? ex.getCause()
                        : ex;
                plugin.getLogger().log(Level.WARNING, "Item census MySQL save failed: " + cause.getMessage(), cause);
            }
        }
    }
}
