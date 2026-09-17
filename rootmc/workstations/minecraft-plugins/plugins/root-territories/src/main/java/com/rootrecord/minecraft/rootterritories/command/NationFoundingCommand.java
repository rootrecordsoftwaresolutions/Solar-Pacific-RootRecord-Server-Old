package com.rootrecord.minecraft.rootterritories.command;

import com.rootrecord.minecraft.rootterritories.RootTerritoriesPlugin;
import com.rootrecord.minecraft.rootterritories.nation.NationFoundingProposal;
import com.rootrecord.minecraft.rootterritories.nation.NationFoundingService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class NationFoundingCommand implements CommandExecutor, TabCompleter {

    private final RootTerritoriesPlugin plugin;

    public NationFoundingCommand(RootTerritoriesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        NationFoundingService founding = plugin.nationFounding();
        if (founding == null || !founding.config().enabled()) {
            sender.sendMessage(NationFoundingService.colorize("&cNation founding proposals are disabled."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "propose", "new" -> handlePropose(sender, args);
            case "agree", "sign" -> handleAgree(sender, args);
            case "status", "list" -> handleStatus(sender, args);
            case "cancel" -> handleCancel(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handlePropose(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NationFoundingService.colorize("&cPlayers only."));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(NationFoundingService.colorize(
                    "&cUsage: &f/nationfounding propose <NationName> <PartnerTown> [more towns...]"));
            return true;
        }
        String nation = args[1];
        List<String> partners = Arrays.asList(Arrays.copyOfRange(args, 2, args.length));
        withFounding(player, svc -> svc.propose(player, nation, partners)
                .ifPresent(msg -> player.sendMessage(NationFoundingService.colorize(msg))));
        return true;
    }

    private boolean handleAgree(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NationFoundingService.colorize("&cPlayers only."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(NationFoundingService.colorize("&cUsage: &f/nationfounding agree <proposal-id>"));
            return true;
        }
        withFounding(player, svc -> svc.agree(player, args[1])
                .ifPresent(msg -> player.sendMessage(NationFoundingService.colorize(msg))));
        return true;
    }

    private boolean handleCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(NationFoundingService.colorize("&cPlayers only."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(NationFoundingService.colorize("&cUsage: &f/nationfounding cancel <proposal-id>"));
            return true;
        }
        withFounding(player, svc -> svc.cancel(player, args[1])
                .ifPresent(msg -> player.sendMessage(NationFoundingService.colorize(msg))));
        return true;
    }

    private boolean handleStatus(CommandSender sender, String[] args) {
        NationFoundingService founding = plugin.nationFounding();
        if (founding == null) {
            return true;
        }
        if (args.length >= 2) {
            founding.store().findById(args[1]).ifPresentOrElse(
                    p -> sender.sendMessage(formatProposal(p)),
                    () -> sender.sendMessage(NationFoundingService.colorize("&cProposal not found.")));
            return true;
        }
        List<NationFoundingProposal> open = founding.store().allProposals().stream()
                .filter(p -> p.status() == NationFoundingProposal.Status.PENDING
                        || p.status() == NationFoundingProposal.Status.APPROVED)
                .collect(Collectors.toList());
        if (open.isEmpty()) {
            sender.sendMessage(NationFoundingService.colorize("&7No open nation founding proposals."));
            return true;
        }
        sender.sendMessage(NationFoundingService.colorize("&6Nation founding proposals:"));
        for (NationFoundingProposal p : open) {
            sender.sendMessage(formatProposal(p));
        }
        return true;
    }

    private String formatProposal(NationFoundingProposal p) {
        return NationFoundingService.colorize("&7[&f" + p.id() + "&7] &f" + p.nationName()
                + " &7capital &f" + p.capitalTown()
                + " &7partners &f" + String.join(", ", p.partnerTowns())
                + " &7" + p.status().name().toLowerCase(Locale.ROOT)
                + " &7(" + p.signedCount() + "/" + p.requiredTownCount() + " signed)"
                + " &7— &f/nationfounding agree " + p.id());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(NationFoundingService.colorize("&6Nation founding &7— multi-town agreement required"));
        sender.sendMessage(NationFoundingService.colorize(
                "&7Cost &f" + NationFoundingService.formatGold(
                        plugin.nationFounding().config().totalCostGold())
                        + " G &7split across signing towns when the capital runs &f/nation new"));
        sender.sendMessage(NationFoundingService.colorize("&f/nationfounding propose <Nation> <Town2> [Town3...]"));
        sender.sendMessage(NationFoundingService.colorize("&f/nationfounding agree <id> &7— partner mayor signs"));
        sender.sendMessage(NationFoundingService.colorize("&f/nationfounding status [id]"));
        sender.sendMessage(NationFoundingService.colorize("&f/nationfounding cancel <id> &7— capital mayor only"));
    }

    private void withFounding(Player player, java.util.function.Consumer<NationFoundingService> action) {
        NationFoundingService founding = plugin.nationFounding();
        if (founding == null) {
            player.sendMessage(NationFoundingService.colorize("&cNation founding unavailable."));
            return;
        }
        action.accept(founding);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("propose", "agree", "status", "cancel"), args[0]);
        }
        if (args.length == 2 && ("agree".equalsIgnoreCase(args[0]) || "cancel".equalsIgnoreCase(args[0])
                || "status".equalsIgnoreCase(args[0]))) {
            NationFoundingService founding = plugin.nationFounding();
            if (founding == null) {
                return List.of();
            }
            return filter(
                    founding.store().allProposals().stream().map(NationFoundingProposal::id).collect(Collectors.toList()),
                    args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
