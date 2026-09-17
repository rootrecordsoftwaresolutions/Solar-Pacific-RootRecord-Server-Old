package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.data.EconomyStore;
import com.rootrecord.minecraft.rootessentials.towny.TownyPlayerAccess;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class EconomyCommands {

    private EconomyCommands() {}

    public static final class Baltop implements CommandExecutor, TabCompleter {

        private static final int TOP_LIMIT = 10;

        private final RootEconomyPlugin plugin;

        public Baltop(RootEconomyPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!Permissions.has(sender, "balancetop") && !Permissions.has(sender, "baltop")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            // Bare /baltop â†’ top players (wallet balances). Towns/nations need an explicit arg.
            EconomyStore.BaltopKind kind = EconomyStore.BaltopKind.PLAYERS;
            if (args.length > 0) {
                kind = parseKind(args[0].toLowerCase(Locale.ROOT));
                if (kind == null) {
                    sender.sendMessage(plugin.colorize(RootMcEconomyWeb.townyHost()
                            ? "&cUsage: &f/baltop [players|towns|nations]"
                            : "&cUsage: &f/baltop [players]"));
                    return true;
                }
            }
            if (!RootMcEconomyWeb.townyHost()
                    && (kind == EconomyStore.BaltopKind.TOWNS || kind == EconomyStore.BaltopKind.NATIONS)) {
                sender.sendMessage(plugin.colorize(
                        "&eClaims has player wallets only. Use &f/baltop&e or &f/baltop players&e."));
                return true;
            }
            try {
                sendLeaderboard(sender, kind);
                return true;
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&cBaltop failed: &f" + ex.getMessage()));
                return true;
            }
        }

        private void sendLeaderboard(CommandSender sender, EconomyStore.BaltopKind kind) throws Exception {
            var rows = plugin.topBaltop(kind, TOP_LIMIT);
            String title = switch (kind) {
                case PLAYERS -> "Top players";
                case TOWNS -> "Top towns";
                case NATIONS -> "Top nations";
            };
            ChatUi.banner(sender, title);
            if (rows.isEmpty()) {
                ChatUi.tip(sender, "No balances recorded yet.");
            } else {
                int rank = 1;
                for (var row : rows) {
                    ChatUi.rank(sender, rank++, row.username(), plugin.money(row.balance()), plugin.currency());
                }
            }
            sendSelfLine(sender, kind, rows);
            if (kind == EconomyStore.BaltopKind.PLAYERS && RootMcEconomyWeb.townyHost()) {
                ChatUi.tip(sender, "Also: /baltop towns | nations");
            }
            ChatUi.links(
                    sender,
                    "Economy", RootMcEconomyWeb.economy(),
                    "Leaderboard", RootMcEconomyWeb.leaderboard());
        }

        private void sendSelfLine(
                CommandSender sender,
                EconomyStore.BaltopKind kind,
                java.util.List<EconomyStore.BalanceRow> topRows) throws Exception {
            if (!(sender instanceof Player player)) {
                return;
            }
            Optional<EconomyStore.RankedBalance> ranked = switch (kind) {
                case PLAYERS -> plugin.baltopRankForPlayer(player.getUniqueId(), player.getName());
                case TOWNS -> TownyPlayerAccess.townName(player)
                        .flatMap(name -> {
                            try {
                                return plugin.baltopRank(kind, name);
                            } catch (Exception ex) {
                                return Optional.empty();
                            }
                        });
                case NATIONS -> TownyPlayerAccess.nationName(player)
                        .flatMap(name -> {
                            try {
                                return plugin.baltopRank(kind, name);
                            } catch (Exception ex) {
                                return Optional.empty();
                            }
                        });
            };
            if (ranked.isEmpty()) {
                String missing = switch (kind) {
                    case TOWNS -> "You are not in a town.";
                    case NATIONS -> "You are not in a nation.";
                    default -> "Your rank is unavailable.";
                };
                ChatUi.tip(sender, missing);
                return;
            }
            EconomyStore.RankedBalance self = ranked.get();
            if (kind != EconomyStore.BaltopKind.PLAYERS && alreadyListed(topRows, self.displayName())) {
                return;
            }
            String label = switch (kind) {
                case PLAYERS -> "You";
                case TOWNS, NATIONS -> self.displayName();
            };
            ChatUi.rank(sender, self.rank(), label, plugin.money(self.balance()), plugin.currency());
        }

        private static boolean alreadyListed(
                java.util.List<EconomyStore.BalanceRow> topRows, String displayName) {
            if (topRows == null || displayName == null) {
                return false;
            }
            String key = com.rootrecord.minecraft.rootessentials.data.EconomySystemAccounts
                    .bankDisplayKey(displayName);
            for (EconomyStore.BalanceRow row : topRows) {
                if (key.equals(com.rootrecord.minecraft.rootessentials.data.EconomySystemAccounts
                        .bankDisplayKey(row.username()))) {
                    return true;
                }
            }
            return false;
        }

        private static EconomyStore.BaltopKind parseKind(String raw) {
            return switch (raw) {
                case "player", "players" -> EconomyStore.BaltopKind.PLAYERS;
                case "town", "towns" -> EconomyStore.BaltopKind.TOWNS;
                case "nation", "nations" -> EconomyStore.BaltopKind.NATIONS;
                default -> null;
            };
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = RootMcEconomyWeb.townyHost()
                    ? List.of("players", "towns", "nations")
                    : List.of("players");
            List<String> out = new ArrayList<>();
            for (String option : options) {
                if (option.startsWith(prefix)) {
                    out.add(option);
                }
            }
            return out;
        }
    }

    public static final class Paytoggle implements CommandExecutor {
        private final RootEconomyPlugin plugin;

        public Paytoggle(RootEconomyPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            if (!Permissions.has(player, "paytoggle")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            try {
                boolean enabled = plugin.toggleAcceptsPay(player.getUniqueId());
                ChatUi.entry(player, "Pay", enabled ? "incoming on" : "incoming off", enabled ? "ok" : "open");
                return true;
            } catch (Exception ex) {
                ChatUi.entry(player, "Pay", "toggle failed | try again", "alert");
                return true;
            }
        }
    }
}
