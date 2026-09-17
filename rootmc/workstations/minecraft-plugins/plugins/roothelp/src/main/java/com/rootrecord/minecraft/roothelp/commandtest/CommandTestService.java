package com.rootrecord.minecraft.roothelp.commandtest;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.common.RootMcPublicReachout;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import com.rootrecord.minecraft.roothelp.catalog.CommandCatalog;
import com.rootrecord.minecraft.roothelp.cloud.HelpCloudClient;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class CommandTestService {

    public record Status(
            int completed,
            int total,
            double earned,
            double cap,
            double perCommand,
            boolean complete,
            CommandCatalog.Line nextPending) {}

    private final RootHelpPlugin plugin;
    private final CommandTestConfig config;
    private final CommandTestStore store;

    public CommandTestService(RootHelpPlugin plugin, CommandTestConfig config, CommandTestStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
    }

    public CommandTestConfig config() {
        return config;
    }

    public boolean enabled() {
        return config.enabled() && config.mysqlConfigured();
    }

    public void ensureEnrolledAsync(Player player) {
        if (!enabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ensureEnrolled(player);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Command test enroll failed for " + player.getName(), ex);
            }
        });
    }

    public void ensureEnrolled(Player player) throws SQLException {
        if (!enabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (store.findProgress(uuid).isPresent()) {
            return;
        }
        List<CommandCatalog.Line> eligible = eligibleLines(player);
        int count = eligible.size();
        if (count == 0) {
            return;
        }
        double per = roundGold(config.totalGoldCap() / count);
        store.enroll(uuid, count, per);
        Bukkit.getScheduler().runTask(plugin, () -> sendIntro(player, count, per));
    }

    public Status status(Player player) throws SQLException {
        UUID uuid = player.getUniqueId();
        ensureEnrolled(player);
        Optional<CommandTestStore.Progress> progress = store.findProgress(uuid);
        if (progress.isEmpty()) {
            return new Status(0, 0, 0, config.totalGoldCap(), 0, true, null);
        }
        Set<String> done = store.completedKeys(uuid);
        List<CommandCatalog.Line> eligible = eligibleLines(player);
        CommandCatalog.Line next = null;
        for (CommandCatalog.Line line : eligible) {
            if (!done.contains(line.key())) {
                next = line;
                break;
            }
        }
        boolean complete = progress.get().completedAt() != null || done.size() >= eligible.size();
        return new Status(
                done.size(),
                eligible.size(),
                progress.get().totalGoldPaid(),
                config.totalGoldCap(),
                progress.get().goldPerCommand(),
                complete,
                next);
    }

    public void tryCompleteCommand(Player player, String commandLine) {
        if (!enabled() || commandLine == null || !commandLine.startsWith("/")) {
            return;
        }
        String label = firstLabel(commandLine);
        if (label.isBlank() || "cmdtest".equals(label) || "testcmds".equals(label)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Optional<CommandCatalog.Line> match = findAutoLine(player, label, commandLine);
                if (match.isEmpty() || match.get().testManual()) {
                    return;
                }
                completeLine(player, match.get(), false);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Command test complete failed for " + player.getName(), ex);
            }
        });
    }

    public void tryManual(Player player, String testKey) {
        if (!enabled()) {
            player.sendMessage(plugin.msg("cmdtest-disabled"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CommandCatalog.Line line = findByKey(player, testKey);
                if (line == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(plugin.msg("cmdtest-key-unknown").replace("{key}", testKey)));
                    return;
                }
                if (!line.testManual()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(plugin.msg("cmdtest-not-manual").replace("{cmd}", line.command())));
                    return;
                }
                completeLine(player, line, true);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Command test manual failed for " + player.getName(), ex);
            }
        });
    }

    public void submitReport(Player player, String testKey, String note) {
        if (!enabled() || !plugin.cloud().hasCredentials()) {
            player.sendMessage(plugin.msg("cmdtest-no-cloud"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CommandCatalog.Line line = findByKey(player, testKey);
                if (line == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(plugin.msg("cmdtest-key-unknown").replace("{key}", testKey)));
                    return;
                }
                Status status = status(player);
                plugin.cloud().submitCommandTestReport(
                        player.getUniqueId().toString(),
                        player.getName(),
                        player.getWorld().getName(),
                        line.key(),
                        line.command(),
                        note,
                        status.completed(),
                        status.total());
                store.saveReport(player.getUniqueId(), line.key(), note);
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.msg("cmdtest-report-sent")));
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.colorize(
                                plugin.rawMsg("cmdtest-report-fail").replace("{error}", ex.getMessage()))));
            }
        });
    }

    public void sendReminder(Player player) {
        if (!enabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Status status = status(player);
                if (status.complete() || status.nextPending() == null) {
                    return;
                }
                CommandCatalog.Line next = status.nextPending();
                String line = config.message(
                        "reminder",
                        "&eCommand test &8(&f{done}&8/&f{total}&8)&7 Ã¢â‚¬â€ try &f{cmd}&7 for &f{gold} G&7 (&f/cmdtest&7 for progress)");
                String msg = line
                        .replace("{done}", String.valueOf(status.completed()))
                        .replace("{total}", String.valueOf(status.total()))
                        .replace("{cmd}", next.command().split("\\|")[0].trim())
                        .replace("{gold}", formatGold(status.perCommand()))
                        .replace("{key}", next.key());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (config.actionBarReminders()) {
                        player.sendActionBar(plugin.colorize(msg));
                    } else {
                        player.sendMessage(plugin.colorize(msg));
                    }
                });
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.FINE, "Command test reminder skipped: " + ex.getMessage());
            }
        });
    }

    private void completeLine(Player player, CommandCatalog.Line line, boolean manual) throws SQLException {
        UUID uuid = player.getUniqueId();
        ensureEnrolled(player);
        Set<String> done = store.completedKeys(uuid);
        if (done.contains(line.key())) {
            return;
        }
        Optional<CommandTestStore.Progress> progress = store.findProgress(uuid);
        if (progress.isEmpty()) {
            return;
        }
        double gold = Math.min(progress.get().goldPerCommand(), config.totalGoldCap() - progress.get().totalGoldPaid());
        if (gold <= 0) {
            gold = 0;
        }
        double feeRefund = 0;
        if (config.feeRefund() && line.testFeeGold() > 0) {
            feeRefund = line.testFeeGold();
        }
        if (!store.markDone(uuid, line.key(), gold, feeRefund)) {
            return;
        }
        payGold(player, gold, "command-test:" + line.key());
        if (feeRefund > 0) {
            payGold(player, feeRefund, "command-test-fee-refund:" + line.key());
        }
        Status after = status(player);
        final double paidTotal = gold + feeRefund;
        Bukkit.getScheduler().runTask(plugin, () -> {
            String template = config.message(
                    "completed-one",
                    "&aCommand tested: &f{cmd}&a Ã¢â‚¬â€ &f+{gold} G&7 (&f{done}&7/&f{total}&7)");
            player.sendMessage(plugin.colorize(template
                    .replace("{cmd}", line.command())
                    .replace("{gold}", formatGold(paidTotal))
                    .replace("{done}", String.valueOf(after.completed()))
                    .replace("{total}", String.valueOf(after.total()))));
            if (after.complete()) {
                player.sendMessage(plugin.msg("cmdtest-all-complete")
                        .replace("{earned}", formatGold(after.earned())));
            }
        });
        if (after.complete()) {
            store.markCompleted(uuid);
            notifyReachout(player, after.earned());
        }
    }

    private void payGold(Player player, double amount, String reason) {
        if (amount <= 0) {
            return;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            return;
        }
        treasury.grantToPlayer(
                player.getUniqueId(),
                player.getName(),
                amount,
                treasury.treasuryUuid(),
                treasury.treasuryUsername(),
                reason);
    }

    private void notifyReachout(Player player, double earned) {
        RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
        if (reachout != null) {
            reachout.recordTreasuryOutflow("command_test", player.getName(), player.getUniqueId(), earned, true);
        }
    }

    private void sendIntro(Player player, int count, double per) {
        player.sendMessage(plugin.msg("cmdtest-intro"));
        player.sendMessage(plugin.colorize(config.message(
                "intro-detail",
                "&7Try each command you have access to once. Notifications stop when you run it."
                        + " Earn up to &f1000 G&7 (&f{per} G&7 Ãƒ -  &f{count}&7 commands). Fee commands get one free test use."))
                .replace("{per}", formatGold(per))
                .replace("{count}", String.valueOf(count)));
        player.sendMessage(plugin.msg("cmdtest-intro-cmdtest"));
    }

    private List<CommandCatalog.Line> eligibleLines(Player player) {
        return plugin.catalog().linesFor(player);
    }

    private Optional<CommandCatalog.Line> findAutoLine(Player player, String label, String commandLine) {
        String lowerLine = commandLine.toLowerCase(Locale.ROOT);
        CommandCatalog.Line best = null;
        int bestLen = -1;
        for (CommandCatalog.Line line : eligibleLines(player)) {
            if (line.testManual()) {
                continue;
            }
            if (!matchesPaidUsage(line, lowerLine)) {
                continue;
            }
            String path = primaryCommandPath(line.command());
            if (path.isBlank() || !matchesCommandPath(path, lowerLine)) {
                continue;
            }
            if (path.length() > bestLen) {
                best = line;
                bestLen = path.length();
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean matchesCommandPath(String path, String lowerCommandLine) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return lowerCommandLine.equals(path) || lowerCommandLine.startsWith(path + " ");
    }

    /** Longest-prefix match for entries like `/shop create` vs bare `/shop`. */
    private static String primaryCommandPath(String cmd) {
        if (cmd == null || cmd.isBlank()) {
            return "";
        }
        String first = cmd.split("\\|")[0].trim();
        int paren = first.indexOf('(');
        if (paren > 0) {
            first = first.substring(0, paren).trim();
        }
        int dot = first.indexOf('\u00b7');
        if (dot > 0) {
            first = first.substring(0, dot).trim();
        }
        int angle = first.indexOf('<');
        if (angle > 0) {
            first = first.substring(0, angle).trim();
        }
        if (!first.startsWith("/")) {
            return "";
        }
        return first.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean matchesPaidUsage(CommandCatalog.Line line, String lowerCommandLine) {
        return switch (line.key()) {
            case "survey" -> lowerCommandLine.contains(" confirm");
            case "warp-create" -> lowerCommandLine.contains(" create");
            case "town-new" -> lowerCommandLine.contains(" new");
            case "shop-stock" -> lowerCommandLine.contains(" confirm");
            case "rank-buy" -> lowerCommandLine.contains(" buy");
            default -> true;
        };
    }

    private Optional<CommandCatalog.Line> findAutoLine(Player player, String label) {
        return findAutoLine(player, label, "/" + label);
    }

    private CommandCatalog.Line findByKey(Player player, String testKey) {
        String key = testKey == null ? "" : testKey.trim().toLowerCase(Locale.ROOT);
        for (CommandCatalog.Line line : eligibleLines(player)) {
            if (line.key().equals(key)) {
                return line;
            }
        }
        return null;
    }

    public List<CommandCatalog.Line> pendingLines(Player player) throws SQLException {
        Set<String> done = store.completedKeys(player.getUniqueId());
        List<CommandCatalog.Line> pending = new ArrayList<>();
        for (CommandCatalog.Line line : eligibleLines(player)) {
            if (!done.contains(line.key())) {
                pending.add(line);
            }
        }
        return pending;
    }

    private static String firstLabel(String commandLine) {
        String trimmed = commandLine.trim();
        if (!trimmed.startsWith("/")) {
            return "";
        }
        String withoutSlash = trimmed.substring(1);
        int space = withoutSlash.indexOf(' ');
        return (space < 0 ? withoutSlash : withoutSlash.substring(0, space)).toLowerCase(Locale.ROOT);
    }

    private static double roundGold(double gold) {
        return GoldMoney.round(gold);
    }

    private static String formatGold(double gold) {
        if (gold == Math.rint(gold)) {
            return String.valueOf((long) gold);
        }
        return String.format(Locale.US, "%.3f", gold);
    }
}
