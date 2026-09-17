package com.rootrecord.minecraft.rootloans.schedule;

import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import com.rootrecord.minecraft.rootloans.config.LoanDailyTaxConfig;
import com.rootrecord.minecraft.rootloans.data.LoanDailyTaxStateStore;
import com.rootrecord.minecraft.rootloans.service.LoanDailyTaxService;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LoanDailyTaxScheduler {

    private final RootLoansPlugin plugin;
    private final LoanDailyTaxStateStore state;
    private BukkitTask pollTask;
    private final AtomicBoolean running = new AtomicBoolean();

    public LoanDailyTaxScheduler(RootLoansPlugin plugin, LoanDailyTaxStateStore state) {
        this.plugin = plugin;
        this.state = state;
    }

    public void start() {
        stop();
        LoanDailyTaxConfig cfg = plugin.loanDailyTaxConfig();
        if (cfg == null || !cfg.enabled()) {
            return;
        }
        long periodTicks = Math.max(20L, cfg.pollSeconds() * 20L);
        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, periodTicks, periodTicks);
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    public void runNow(boolean manual) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        LoanDailyTaxService service = plugin.loanDailyTax();
        if (service == null || !service.enabled()) {
            running.set(false);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<LoanDailyTaxService.TaxResult> applied = service.accrueAllDue();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (!applied.isEmpty()) {
                            service.notifyApplied(applied);
                            if (manual) {
                                Bukkit.broadcastMessage(plugin.colorize(
                                        plugin.msg("loan-daily-tax-run-complete")
                                                .replace("{count}", String.valueOf(applied.size()))));
                            } else {
                                plugin.getLogger().info(
                                        "Loan daily tax applied to " + applied.size() + " loan(s).");
                            }
                        }
                        if (!manual) {
                            state.markRun(LocalDate.now(LoanDailyTaxConfig.HST));
                        }
                    } finally {
                        running.set(false);
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Loan daily tax failed: " + ex.getMessage());
                running.set(false);
            }
        });
    }

    private void poll() {
        LoanDailyTaxConfig cfg = plugin.loanDailyTaxConfig();
        if (cfg == null || !cfg.enabled() || plugin.loanDailyTax() == null || !plugin.loanDailyTax().enabled()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(LoanDailyTaxConfig.HST);
        if (now.getHour() != cfg.scheduleHourHst() || now.getMinute() > 5) {
            return;
        }
        LocalDate today = now.toLocalDate();
        if (today.equals(state.lastRunDate())) {
            return;
        }
        plugin.getLogger().info("Running scheduled loan daily tax (" + today + " HST).");
        runNow(false);
    }
}
