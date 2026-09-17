package com.rootrecord.minecraft.rootavacore.economy;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.rootavacore.AvaConfig;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ava wallet IS the Server Reserve. Hire/fees sink there; /bal Ava_Ivy = /reserve.
 * Credits playtime while she works, pays milestones from reserve, tops up
 * claim bank from the reserve vault, expands when she can.
 */
public final class AvaEconomyService {

    private final RootAvaCorePlugin plugin;
    private BukkitTask task;
    private String lastNote = "boot";
    private final AtomicInteger expandsThisHour = new AtomicInteger(0);
    private String expandHour = "";
    private long lastPlaytimeCreditAt;

    public AvaEconomyService(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        AvaConfig.EconomyConfig cfg = plugin.config().economy();
        if (!cfg.enabled()) {
            lastNote = "disabled";
            return;
        }
        long period = Math.max(20L, cfg.tickSeconds() * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 120L, period);
        plugin.getLogger().info("Ava economy · wallet/rewards tick every " + cfg.tickSeconds() + "s");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public String lastNote() {
        return lastNote;
    }

    public String statusLine() {
        UUID ava = plugin.config().powers().avaUuid();
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        double bal = eco == null ? 0 : GoldMoney.round(eco.balance(ava));
        return String.format(Locale.US, "reserve %.3fG · %s", bal, lastNote);
    }

    public double walletBalance() {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null) return 0;
        return GoldMoney.round(eco.balance(plugin.config().powers().avaUuid()));
    }

    private void tick() {
        AvaConfig.EconomyConfig cfg = plugin.config().economy();
        if (!cfg.enabled() || !plugin.config().enabled()) {
            lastNote = "disabled";
            return;
        }
        if (!plugin.powers().playEnabled()) {
            lastNote = "play_off";
            return;
        }
        boolean working = isWorking();
        Player online = findAvaOnline();

        if (working && cfg.creditPlaytimeWhileWorking()) {
            creditWorkPlaytime(cfg);
        }
        if (cfg.payPlaytimeMilestones()) {
            payMilestones();
        }
        if (online != null && cfg.claimBonusWhenOnline()) {
            claimDailyBonus(online);
        }
        if (cfg.autoClaimBankTopup()) {
            topUpClaimBank(cfg);
        }
        if (cfg.autoExpand() && online != null) {
            tryExpand(online, cfg);
        }
    }

    private boolean isWorking() {
        if (plugin.autonomy() != null
                && plugin.autonomy().phase() != null
                && plugin.config().autonomy().enabled()
                && !plugin.autonomy().paused()
                && plugin.autonomy().phase().name().indexOf("PAUSED") < 0
                && plugin.autonomy().phase().name().indexOf("IDLE") < 0) {
            String note = plugin.autonomy().statusLine();
            if (note != null && note.contains("no_claims")) return false;
            return true;
        }
        return plugin.presence() != null && plugin.presence().isSpawned();
    }

    private void creditWorkPlaytime(AvaConfig.EconomyConfig cfg) {
        long now = System.currentTimeMillis();
        long intervalMs = Math.max(10L, cfg.tickSeconds()) * 1000L;
        if (now - lastPlaytimeCreditAt < intervalMs * 0.8) return;
        lastPlaytimeCreditAt = now;
        long seconds = Math.max(1L, cfg.tickSeconds());
        UUID ava = plugin.config().powers().avaUuid();
        String name = plugin.config().powers().avaName();
        Plugin times = Bukkit.getPluginManager().getPlugin("Root-Times");
        if (times != null && times.isEnabled()) {
            try {
                times.getClass()
                        .getMethod("creditPlaytime", UUID.class, String.class, long.class)
                        .invoke(times, ava, name, seconds);
                lastNote = "playtime +" + seconds + "s";
                return;
            } catch (Throwable t) {
                plugin.getLogger().fine("times credit: " + t.getMessage());
            }
        }
        // Fallback: Root-Play RewardsStore.addFallbackPlaytime
        try {
            Plugin play = Bukkit.getPluginManager().getPlugin("Root-Play");
            if (play == null) return;
            Object rewards = play.getClass().getMethod("rewards").invoke(play);
            Object store = rewards.getClass().getMethod("store").invoke(rewards);
            store.getClass()
                    .getMethod("addFallbackPlaytime", UUID.class, long.class)
                    .invoke(store, ava, seconds);
            lastNote = "playtime fallback +" + seconds + "s";
        } catch (Throwable t) {
            plugin.getLogger().fine("fallback playtime: " + t.getMessage());
        }
    }

    private void payMilestones() {
        try {
            Plugin play = Bukkit.getPluginManager().getPlugin("Root-Play");
            if (play == null || !play.isEnabled()) return;
            Object rewards = play.getClass().getMethod("rewards").invoke(play);
            Object svc = rewards.getClass().getMethod("playtimeRewards").invoke(rewards);
            UUID ava = plugin.config().powers().avaUuid();
            String name = plugin.config().powers().avaName();
            Player online = findAvaOnline();
            if (online != null) {
                svc.getClass().getMethod("checkPlayerAsync", Player.class).invoke(svc, online);
            } else {
                svc.getClass().getMethod("checkUuid", UUID.class, String.class).invoke(svc, ava, name);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("milestone pay: " + t.getMessage());
        }
    }

    private void claimDailyBonus(Player ava) {
        try {
            Plugin appr = Bukkit.getPluginManager().getPlugin("Root-Appreciation");
            if (appr == null || !appr.isEnabled()) {
                // command path still works if registered
                ava.performCommand("bonus");
                lastNote = "bonus attempted";
                return;
            }
            Object service = appr.getClass().getMethod("service").invoke(appr);
            Boolean available = (Boolean) service.getClass()
                    .getMethod("bonusAvailable", UUID.class)
                    .invoke(service, ava.getUniqueId());
            if (Boolean.TRUE.equals(available)) {
                ava.performCommand("bonus");
                lastNote = "bonus claimed";
            }
        } catch (Throwable t) {
            ava.performCommand("bonus");
            lastNote = "bonus command";
        }
    }

    private void topUpClaimBank(AvaConfig.EconomyConfig cfg) {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null) {
            lastNote = "no_economy";
            return;
        }
        UUID ava = plugin.config().powers().avaUuid();
        Object areaRoot = firstAreaRoot();
        if (areaRoot == null) {
            lastNote = "no_area_bank";
            return;
        }
        double bankBal = claimBankBalance(areaRoot);
        double want = cfg.bankReserveGold();
        if (bankBal + 1e-9 >= want) {
            lastNote = String.format(Locale.US, "bank ok %.3fG", bankBal);
            return;
        }
        double need = GoldMoney.round(want - bankBal);
        double wallet = GoldMoney.round(eco.balance(ava));
        // Keep a small personal float
        double spendable = GoldMoney.round(Math.max(0, wallet - cfg.walletFloatGold()));
        double move = Math.min(need, spendable);
        if (move < GoldMoney.MIN_AMOUNT) {
            lastNote = String.format(Locale.US, "bank low %.3fG wallet %.3fG", bankBal, wallet);
            return;
        }
        if (!eco.withdraw(ava, move)) {
            lastNote = "withdraw failed";
            return;
        }
        if (!depositClaimBank(areaRoot, move)) {
            eco.deposit(ava, move);
            lastNote = "bank deposit failed (refunded)";
            return;
        }
        lastNote = String.format(Locale.US, "bank +%.3fG → %.3fG", move, bankBal + move);
    }

    private void tryExpand(Player ava, AvaConfig.EconomyConfig cfg) {
        String hour = java.time.Instant.now().toString().substring(0, 13);
        if (!hour.equals(expandHour)) {
            expandHour = hour;
            expandsThisHour.set(0);
        }
        if (expandsThisHour.get() >= cfg.expandMaxPerHour()) {
            return;
        }
        Object areaRoot = firstAreaRoot();
        if (areaRoot == null) return;
        double price = expansionPrice(areaRoot);
        double bank = claimBankBalance(areaRoot);
        if (price > 0 && bank + 1e-9 < price) {
            lastNote = String.format(Locale.US, "expand needs %.3fG bank (have %.3fG)", price, bank);
            return;
        }
        Location edge = edgeStand(areaRoot);
        if (edge == null) return;
        Location back = ava.getLocation().clone();
        try {
            ava.teleport(edge);
            ava.performCommand("c deposit all");
            boolean ok = ava.performCommand("c claim");
            if (ok) {
                expandsThisHour.incrementAndGet();
                lastNote = "expand attempted @ edge";
            }
        } finally {
            // Don't strand Ava far from claim chests if expand failed — soft return
            if (back.getWorld() != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (ava.isOnline()) ava.teleport(back);
                }, 40L);
            }
        }
    }

    private Player findAvaOnline() {
        AvaConfig.PowersConfig p = plugin.config().powers();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (p.isAvaPlayer(pl.getUniqueId(), pl.getName())) return pl;
        }
        return null;
    }

    private Object firstAreaRoot() {
        try {
            Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled()) return null;
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            UUID ava = plugin.config().powers().avaUuid();
            @SuppressWarnings("unchecked")
            List<Object> owned = (List<Object>) service.getClass()
                    .getMethod("ownedBy", UUID.class)
                    .invoke(service, ava);
            if (owned == null || owned.isEmpty()) return null;
            Object first = owned.get(0);
            try {
                return service.getClass().getMethod("areaRoot", first.getClass()).invoke(service, first);
            } catch (NoSuchMethodException e) {
                // ClaimService.areaRoot(ClaimRecord) — try interface Object
                return service.getClass()
                        .getMethod("areaRoot", Class.forName("com.rootrecord.minecraft.rootclaims.ClaimRecord"))
                        .invoke(service, first);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("area root: " + t.getMessage());
            return null;
        }
    }

    private double claimBankBalance(Object claim) {
        try {
            Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            Object banks = claims.getClass().getMethod("claimBanks").invoke(claims);
            Object bal = banks.getClass().getMethod("balance", claim.getClass()).invoke(banks, claim);
            return ((Number) bal).doubleValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    private boolean depositClaimBank(Object claim, double amount) {
        try {
            Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            Object banks = claims.getClass().getMethod("claimBanks").invoke(claims);
            Object ok = banks.getClass()
                    .getMethod("depositToClaimBank", claim.getClass(), double.class)
                    .invoke(banks, claim, amount);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            plugin.getLogger().fine("bank deposit: " + t.getMessage());
            return false;
        }
    }

    private double expansionPrice(Object areaRoot) {
        try {
            Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            Object store = claims.getClass().getMethod("claims").invoke(claims);
            // areaLevel
            int level = ((Number) store.getClass()
                    .getMethod("areaLevel", areaRoot.getClass())
                    .invoke(store, areaRoot)).intValue();
            return ((Number) claims.getClass()
                    .getMethod("expansionPriceForLevel", int.class)
                    .invoke(claims, level)).doubleValue();
        } catch (Throwable t) {
            return 75;
        }
    }

    private Location edgeStand(Object claim) {
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            String world = String.valueOf(key.getClass().getMethod("world").invoke(key));
            int x = ((Number) key.getClass().getMethod("x").invoke(key)).intValue();
            int y = ((Number) key.getClass().getMethod("y").invoke(key)).intValue();
            int z = ((Number) key.getClass().getMethod("z").invoke(key)).intValue();
            int r = ((Number) claim.getClass().getMethod("radiusBlocks").invoke(claim)).intValue();
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            // Stand just outside east edge for /c claim snap
            return new Location(w, x + r + 1 + 0.5, y, z + 0.5);
        } catch (Throwable t) {
            return null;
        }
    }
}
