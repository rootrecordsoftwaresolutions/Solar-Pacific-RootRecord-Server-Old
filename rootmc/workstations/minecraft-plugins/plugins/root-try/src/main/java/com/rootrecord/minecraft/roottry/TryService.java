package com.rootrecord.minecraft.roottry;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.common.RootMcPublicReachout;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Completions, rewards, and player-facing progress. */
public final class TryService {

    private final JavaPlugin plugin;
    private final TryConfig config;
    private final TryCatalog catalog;
    private final TryCompletionStore store;
    private final String activeHost;
    private final Map<UUID, Set<String>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> reminderIndex = new ConcurrentHashMap<>();
    /** Last pay() sweep for ChatUi (same-thread complete → pay). */
    private RootMcIncomeSweepResult lastSweep;

    public TryService(JavaPlugin plugin, TryConfig config, TryCatalog catalog, TryCompletionStore store, String activeHost) {
        this.plugin = plugin;
        this.config = config;
        this.catalog = catalog;
        this.store = store;
        this.activeHost = activeHost;
    }

    public TryCatalog catalog() {
        return catalog;
    }

    public TryConfig config() {
        return config;
    }

    /** Yellow trailing for incomplete tries, e.g. {@code Test this command for 1g}. */
    public String incompleteStatus(TryEntry entry) {
        double gold = entry != null && entry.rewardG() > 0 ? entry.rewardG() : config.rewardG();
        return config.msgIncompleteStatus().replace("{gold}", format(gold));
    }

    public String activeHost() {
        return activeHost;
    }

    public List<TryEntry> hostTries() {
        return catalog.forHost(activeHost);
    }

    public Set<String> completed(UUID uuid) {
        return cache.computeIfAbsent(uuid, store::completedIds);
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
        reminderIndex.remove(uuid);
    }

    public int doneCount(UUID uuid) {
        Set<String> done = completed(uuid);
        int n = 0;
        for (TryEntry e : hostTries()) {
            if (done.contains(e.id())) {
                n++;
            }
        }
        return n;
    }

    public TryEntry nextIncomplete(Player player) {
        Set<String> done = completed(player.getUniqueId());
        List<TryEntry> tries = hostTries();
        if (tries.isEmpty()) {
            return null;
        }
        int start = reminderIndex.getOrDefault(player.getUniqueId(), 0) % tries.size();
        for (int i = 0; i < tries.size(); i++) {
            TryEntry e = tries.get((start + i) % tries.size());
            if (!done.contains(e.id())) {
                reminderIndex.put(player.getUniqueId(), (start + i + 1) % tries.size());
                return e;
            }
        }
        return null;
    }

    public void onCommand(Player player, String commandLine) {
        if (!config.enabled() || player == null || commandLine == null) {
            return;
        }
        TryEntry entry = catalog.matchCommand(activeHost, commandLine);
        if (entry == null) {
            return;
        }
        complete(player, entry, false);
    }

    public boolean complete(Player player, TryEntry entry, boolean force) {
        if (entry == null || player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        Set<String> done = completed(uuid);
        if (done.contains(entry.id()) && !force) {
            return false;
        }
        double reward = entry.rewardG() > 0 ? entry.rewardG() : config.rewardG();
        String serverId = serverId();
        boolean inserted = store.markComplete(uuid, entry.id(), reward, serverId);
        done.add(entry.id());
        if (!inserted) {
            // Already in DB — never double-pay (even on forcecomplete).
            if (!force) {
                return false;
            }
            player.sendMessage(color(config.prefix() + config.msgAlready().replace("{title}", entry.title())));
            return true;
        }
        boolean paid = pay(player, reward, entry.id());
        int total = hostTries().size();
        int finished = doneCount(uuid);
        ChatUi.entry(player, "Try", entry.title(), "done");
        if (paid && lastSweep != null && lastSweep.toLoanRepaid() > 0.0001) {
            if (lastSweep.toWallet() > 0.0001) {
                ChatUi.gold(player, "Reward", "+" + format(lastSweep.toWallet()), "G");
            }
            ChatUi.gold(player, "Loan", format(lastSweep.toLoanRepaid()), "G");
        } else {
            ChatUi.gold(player, "Reward", "+" + format(reward), "G");
        }
        ChatUi.entry(player, "Progress", finished + "/" + total);
        if (!paid) {
            ChatUi.entry(player, "Pay", "treasury down · tell staff", "alert");
        }
        return true;
    }

    private static String shortTag(String id) {
        if (id == null || id.isBlank()) {
            return "Try";
        }
        int dot = id.lastIndexOf('.');
        String raw = dot >= 0 && dot + 1 < id.length() ? id.substring(dot + 1) : id;
        if (raw.isEmpty()) {
            return "Try";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    public boolean adminSkip(UUID uuid, String tryId) {
        TryEntry entry = catalog.get(tryId);
        if (entry == null) {
            return false;
        }
        boolean ok = store.markComplete(uuid, tryId, 0, serverId());
        invalidate(uuid);
        return ok;
    }

    private boolean pay(Player player, double amount, String tryId) {
        lastSweep = null;
        if (amount <= 0) {
            return true;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            return false;
        }
        RootMcIncomeSweepResult sweep = treasury.payTryReward(
                player.getUniqueId(),
                player.getName(),
                amount,
                tryId);
        if (sweep == null) {
            return false;
        }
        lastSweep = sweep;
        RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
        if (reachout != null) {
            // Announce wallet portion; loan sweep is closed-loop back to reserve.
            double announced = sweep.toWallet() > 0 ? sweep.toWallet() : amount;
            reachout.recordTreasuryOutflow("root_try", player.getName(), player.getUniqueId(), announced, true);
        }
        return true;
    }

    private String serverId() {
        RegisteredServiceProvider<RootCoreApi> rsp =
                Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
        if (rsp != null && rsp.getProvider() != null) {
            String id = rsp.getProvider().serverId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return activeHost;
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static String format(double g) {
        if (Math.abs(g - Math.rint(g)) < 1e-9) {
            return String.valueOf((long) Math.rint(g));
        }
        return String.format(Locale.US, "%.2f", g);
    }
}
