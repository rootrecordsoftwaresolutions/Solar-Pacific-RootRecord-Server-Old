package com.rootrecord.minecraft.rootroad;

import com.rootrecord.minecraft.common.RootMcClaimTerritoryService;
import com.rootrecord.minecraft.common.RootMcLoanResolver;
import com.rootrecord.minecraft.common.RootMcLoanService;
import com.rootrecord.minecraft.common.RootMcServerDisplay;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Drives welcome -> wild warning -> /rtp -> /loan take 100 -> /c claim. */
public final class RoadGuideService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final RootRoadPlugin plugin;
    private final RoadProgressStore store;
    private final Map<UUID, Long> pendingRtpUntil = new ConcurrentHashMap<>();

    public RoadGuideService(RootRoadPlugin plugin, RoadProgressStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void onJoin(Player player) {
        if (!plugin.enabledFlag() || player == null) {
            return;
        }
        if (player.hasPermission("rootroad.bypass")) {
            store.appendJoinLog(player.getUniqueId(), player.getName(), "bypass");
            return;
        }
        RoadStep current = store.step(player.getUniqueId());
        if (current.isComplete()) {
            store.appendJoinLog(player.getUniqueId(), player.getName(), "join-complete");
            return;
        }
        // Incomplete road always restarts from welcome on join.
        store.reset(player.getUniqueId());
        store.setStep(player.getUniqueId(), RoadStep.WELCOME);
        store.appendJoinLog(player.getUniqueId(), player.getName(), "join-restart-incomplete-road");
        Bukkit.getScheduler().runTaskLater(plugin.host(), () -> beginRoad(player), 40L);
    }

    public void onQuit(Player player) {
        if (!plugin.enabledFlag() || player == null || !plugin.restartIncompleteOnQuit()) {
            return;
        }
        if (player.hasPermission("rootroad.bypass")) {
            return;
        }
        RoadStep step = store.step(player.getUniqueId());
        if (step.isComplete()) {
            store.appendEventLog(player.getUniqueId(), player.getName(), "quit", "complete");
            return;
        }
        store.appendEventLog(player.getUniqueId(), player.getName(), "quit-incomplete", "reset-on-next-join");
        store.reset(player.getUniqueId());
    }

    public void beginRoad(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        sendLines(player, plugin.rawList("welcome").stream()
                .map(line -> line.replace("{player}", player.getName()))
                .toList());
        store.setStep(player.getUniqueId(), RoadStep.WILD_DANGER);
        store.appendEventLog(player.getUniqueId(), player.getName(), "welcome", "shown");
        Bukkit.getScheduler().runTaskLater(plugin.host(), () -> showWildDanger(player), 60L);
    }

    public void showWildDanger(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (store.step(player.getUniqueId()) != RoadStep.WILD_DANGER) {
            return;
        }
        sendLines(player, plugin.rawList("wild-danger"));
        store.setStep(player.getUniqueId(), RoadStep.RTP);
        store.appendEventLog(player.getUniqueId(), player.getName(), "wild-danger", "shown");
        player.sendMessage(plugin.msg("step-rtp"));
    }

    public void markRtpCommand(Player player) {
        if (!isOnStep(player, RoadStep.RTP)) {
            return;
        }
        pendingRtpUntil.put(player.getUniqueId(), System.currentTimeMillis() + 30_000L);
        store.appendEventLog(player.getUniqueId(), player.getName(), "rtp-command", "awaiting-teleport");
    }

    public void onTeleport(Player player) {
        if (!isOnStep(player, RoadStep.RTP)) {
            return;
        }
        Long until = pendingRtpUntil.get(player.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) {
            return;
        }
        pendingRtpUntil.remove(player.getUniqueId());
        advance(player, RoadStep.LOAN, "progress-rtp", "step-loan", "rtp-done");
    }

    public void onLoanCommand(Player player, String rawArgs) {
        if (!isOnStep(player, RoadStep.LOAN)) {
            return;
        }
        store.appendEventLog(player.getUniqueId(), player.getName(), "loan-command", rawArgs);
        Bukkit.getScheduler().runTaskLater(plugin.host(), () -> checkLoanProgress(player), 10L);
    }

    public void checkLoanProgress(Player player) {
        if (!isOnStep(player, RoadStep.LOAN)) {
            return;
        }
        double owed = loanOwed(player.getUniqueId());
        double need = plugin.loanAmountGold();
        if (owed + 1e-9 < need) {
            store.appendEventLog(player.getUniqueId(), player.getName(), "loan-check", "owed=" + owed);
            return;
        }
        advance(player, RoadStep.CLAIM, "progress-loan", "step-claim", "loan-done owed=" + owed);
    }

    public void onClaimCommand(Player player, String rawArgs) {
        if (!isOnStep(player, RoadStep.CLAIM)) {
            return;
        }
        store.appendEventLog(player.getUniqueId(), player.getName(), "claim-command", rawArgs);
        Bukkit.getScheduler().runTaskLater(plugin.host(), () -> checkClaimProgress(player), 15L);
    }

    public void checkClaimProgress(Player player) {
        if (!isOnStep(player, RoadStep.CLAIM)) {
            return;
        }
        int owned = ownedClaimCount(player.getUniqueId());
        if (owned < 1) {
            store.appendEventLog(player.getUniqueId(), player.getName(), "claim-check", "owned=0");
            return;
        }
        advance(player, RoadStep.COMPLETE, "progress-claim", null, "claim-done owned=" + owned);
        sendLines(player, plugin.rawList("step-done"));
    }

    public void warnIfUnsafe(Player player) {
        if (!plugin.unsafeWarnEnabled() || player == null || !player.isOnline()) {
            return;
        }
        if (player.hasPermission("rootroad.bypass")) {
            return;
        }
        RoadStep step = store.step(player.getUniqueId());
        if (!step.isActive()) {
            return;
        }
        if (isInsideClaim(player)) {
            return;
        }
        player.sendActionBar(LEGACY.deserialize(plugin.colorize(plugin.rawMsg("unsafe-warn"))));
    }

    public RoadStep status(UUID playerId) {
        return store.step(playerId);
    }

    private void advance(Player player, RoadStep next, String progressMsg, String nextStepMsg, String logDetail) {
        store.setStep(player.getUniqueId(), next);
        store.appendEventLog(player.getUniqueId(), player.getName(), "advance", logDetail + " -> " + next.name());
        if (progressMsg != null) {
            player.sendMessage(plugin.msg(progressMsg));
        }
        if (nextStepMsg != null && next.isActive()) {
            player.sendMessage(plugin.msg(nextStepMsg));
        }
    }

    private boolean isOnStep(Player player, RoadStep expected) {
        return plugin.enabledFlag()
                && player != null
                && player.isOnline()
                && !player.hasPermission("rootroad.bypass")
                && store.step(player.getUniqueId()) == expected;
    }

    private void sendLines(Player player, List<String> lines) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            String withServer = RootMcServerDisplay.apply(plugin.host(), line);
            player.sendMessage(plugin.colorize(withServer));
        }
    }

    private double loanOwed(UUID playerId) {
        RootMcLoanService loans = RootMcLoanResolver.resolve(plugin.host());
        if (loans == null) {
            return 0;
        }
        return loans.balanceSummary(playerId)
                .map(RootMcLoanService.LoanBalanceSummary::owed)
                .orElse(0.0);
    }

    private int ownedClaimCount(UUID playerId) {
        if (plugin.host() instanceof com.rootrecord.minecraft.rootclaims.RootClaimsPlugin claims) {
            return claims.ownedClaimCount(playerId);
        }
        Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
        if (claims == null || !claims.isEnabled()) {
            return 0;
        }
        try {
            Method method = claims.getClass().getMethod("ownedClaimCount", UUID.class);
            Object result = method.invoke(claims, playerId);
            return result instanceof Number n ? n.intValue() : 0;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    private boolean isInsideClaim(Player player) {
        if (player.getLocation().getWorld() == null) {
            return false;
        }
        RootMcClaimTerritoryService territory = ShadedServiceBridge.resolveClaimTerritory(plugin.host());
        if (territory != null) {
            return territory.isClaimed(
                    player.getLocation().getWorld().getName(),
                    player.getLocation().getBlockX(),
                    player.getLocation().getBlockZ());
        }
        return false;
    }
}
