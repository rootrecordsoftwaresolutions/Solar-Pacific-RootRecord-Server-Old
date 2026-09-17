package com.rootrecord.minecraft.roothelp.command;

import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import com.rootrecord.minecraft.roothelp.catalog.CommandCatalog;
import com.rootrecord.minecraft.roothelp.commandtest.CommandTestService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandTestCommand implements CommandExecutor, TabCompleter {

    private final RootHelpPlugin plugin;
    private final CommandTestService service;

    public CommandTestCommand(RootHelpPlugin plugin, CommandTestService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cPlayers only."));
            return true;
        }
        if (!sender.hasPermission("roothelp.cmdtest")) {
            sender.sendMessage(plugin.colorize("&cYou don't have permission."));
            return true;
        }
        if (!service.enabled()) {
            sender.sendMessage(plugin.msg("cmdtest-disabled"));
            return true;
        }

        if (args.length == 0) {
            showStatus(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> showList(player);
            case "try" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.msg("cmdtest-try-usage"));
                    return true;
                }
                service.tryManual(player, args[1]);
            }
            case "report" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.msg("cmdtest-report-usage"));
                    return true;
                }
                String note = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
                service.submitReport(player, args[1], note);
            }
            default -> showStatus(player);
        }
        return true;
    }

    private void showStatus(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CommandTestService.Status status = service.status(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (status.complete()) {
                        player.sendMessage(plugin.msg("cmdtest-status-complete")
                                .replace("{earned}", formatGold(status.earned()))
                                .replace("{total}", String.valueOf(status.total())));
                        return;
                    }
                    player.sendMessage(plugin.msg("cmdtest-status")
                            .replace("{done}", String.valueOf(status.completed()))
                            .replace("{total}", String.valueOf(status.total()))
                            .replace("{earned}", formatGold(status.earned()))
                            .replace("{cap}", formatGold(status.cap()))
                            .replace("{per}", formatGold(status.perCommand())));
                    if (status.nextPending() != null) {
                        player.sendMessage(plugin.msg("cmdtest-next")
                                .replace("{cmd}", status.nextPending().command())
                                .replace("{key}", status.nextPending().key()));
                    }
                });
            } catch (SQLException ex) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.colorize("&cCould not load command test progress.")));
            }
        });
    }

    private void showList(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<CommandCatalog.Line> pending = service.pendingLines(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (pending.isEmpty()) {
                        player.sendMessage(plugin.msg("cmdtest-list-empty"));
                        return;
                    }
                    player.sendMessage(plugin.msg("cmdtest-list-header")
                            .replace("{count}", String.valueOf(pending.size())));
                    int shown = 0;
                    for (CommandCatalog.Line line : pending) {
                        if (shown >= 15) {
                            player.sendMessage(plugin.colorize("&8... and " + (pending.size() - shown) + " more (&f/cmdtest list&8)"));
                            break;
                        }
                        String suffix = line.testManual() ? " &8(manual: &f/cmdtest try " + line.key() + "&8)" : "";
                        player.sendMessage(plugin.colorize("&8- &f" + line.command() + suffix));
                        shown++;
                    }
                });
            } catch (SQLException ex) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.colorize("&cCould not load pending commands.")));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player) || args.length == 0) {
            return out;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String opt : List.of("list", "try", "report")) {
                if (opt.startsWith(prefix)) {
                    out.add(opt);
                }
            }
            return out;
        }
        if (args.length == 2 && ("try".equalsIgnoreCase(args[0]) || "report".equalsIgnoreCase(args[0]))) {
            try {
                for (CommandCatalog.Line line : service.pendingLines(player)) {
                    if (line.key().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(line.key());
                    }
                }
            } catch (SQLException ignored) {
            }
        }
        return out;
    }

    private static String formatGold(double gold) {
        if (gold == Math.rint(gold)) {
            return String.valueOf((long) gold);
        }
        return String.format(Locale.US, "%.3f", gold);
    }
}
