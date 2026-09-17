package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.survey.ChunkSurvey;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class SurveyCommand implements CommandExecutor, TabCompleter {

    private final RootEssentialsPlugin plugin;

    public SurveyCommand(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!Permissions.has(player, "survey")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length >= 1 && "cancel".equalsIgnoreCase(args[0])) {
            player.sendMessage(plugin.msg("survey-cancelled"));
            return true;
        }
        if (args.length >= 1 && "confirm".equalsIgnoreCase(args[0])) {
            executeSurvey(player);
            return true;
        }
        offerSurvey(player);
        return true;
    }

    private void offerSurvey(Player player) {
        double cost = plugin.serviceFee("survey", 25.0);
        try {
            double balance = plugin.balance(player.getUniqueId(), player.getName());
            if (balance + 1e-9 < cost) {
                player.sendMessage(plugin.msg("survey-insufficient")
                        .replace("{amount}", plugin.money(cost))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
                return;
            }
            var chunk = player.getLocation().getChunk();
            player.sendMessage(plugin.msg("survey-confirm-prompt")
                    .replace("{amount}", plugin.money(cost))
                    .replace("{currency}", plugin.currency())
                    .replace("{chunkX}", String.valueOf(chunk.getX()))
                    .replace("{chunkZ}", String.valueOf(chunk.getZ())));
            player.sendMessage(ChatLinks.confirmCancel("/survey confirm", "/survey cancel"));
            player.sendMessage(ChatLinks.labelDashUrl("[Constitution]", "https://rootmc.net/wiki/constitution/#taxes-fees-reference"));
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cSurvey failed: &f" + ex.getMessage()));
        }
    }

    private void executeSurvey(Player player) {
        double cost = plugin.serviceFee("survey", 25.0);
        boolean charged = false;
        try {
            double balance = plugin.balance(player.getUniqueId(), player.getName());
            if (balance + 1e-9 < cost) {
                player.sendMessage(plugin.msg("survey-insufficient")
                        .replace("{amount}", plugin.money(cost))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
                return;
            }
            if (!plugin.withdraw(player.getUniqueId(), player.getName(), cost)) {
                player.sendMessage(plugin.msg("survey-insufficient")
                        .replace("{amount}", plugin.money(cost))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
                return;
            }
            charged = true;
            for (String line : ChunkSurvey.reportLines(player.getLocation())) {
                player.sendMessage(plugin.colorize(line));
            }
            player.sendMessage(plugin.msg("survey-paid")
                    .replace("{amount}", plugin.money(cost))
                    .replace("{currency}", plugin.currency()));
            player.sendMessage(ChatLinks.labelDashUrl("[View reserve]", RootMcEconomyWeb.reserve()));
            plugin.sinkServiceFee(player.getUniqueId(), player.getName(), cost, "survey");
        } catch (Exception ex) {
            if (charged) {
                try {
                    plugin.deposit(player.getUniqueId(), player.getName(), cost);
                } catch (Exception refundEx) {
                    plugin.getLogger().warning("Survey refund failed for "
                            + player.getName() + ": " + refundEx.getMessage());
                }
            }
            player.sendMessage(plugin.colorize("&cSurvey failed: &f" + ex.getMessage()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("confirm", "cancel").stream()
                .filter(option -> option.startsWith(prefix))
                .toList();
    }
}