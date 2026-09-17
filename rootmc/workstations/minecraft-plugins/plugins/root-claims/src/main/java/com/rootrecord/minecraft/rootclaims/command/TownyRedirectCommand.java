package com.rootrecord.minecraft.rootclaims.command;

import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Friendly redirect when players try Towny-style {@code /t} commands. */
public final class TownyRedirectCommand implements CommandExecutor, TabCompleter {

    private static final String DEFAULT =
            "&eThis server uses a custom Claims plugin, not Towny. Use &f/c &eor &f/claims &efor more info. "
                    + "&eReturn to Towny with &f/goto towny&e, &f/town&e, &f/towns&e, or &f/t towny&e.";

    private final RootClaimsPlugin plugin;

    public TownyRedirectCommand(RootClaimsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && shouldRedirectToTowny(label, args)) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("prefix")
                    + "&eSending you back to &fTowny&e..."));
            if (!player.performCommand("goto towny")) {
                player.sendMessage(plugin.colorize(plugin.rawMsg("prefix")
                        + "&cTowny transfer unavailable. Use &f/goto towny&c (Root-Core transfer mesh)."));
            }
            return true;
        }
        String body = plugin.rawMsg("towny-redirect");
        if (body == null || body.isBlank() || "towny-redirect".equals(body)) {
            body = DEFAULT;
        }
        sender.sendMessage(plugin.colorize(plugin.rawMsg("prefix") + body));
        return true;
    }

    private static boolean shouldRedirectToTowny(String label, String[] args) {
        String command = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (command.equals("town") || command.equals("towns") || command.equals("towny")) {
            return true;
        }
        if (command.equals("t")) {
            if (args == null || args.length == 0) {
                return false;
            }
            return "towny".equalsIgnoreCase(args[0]);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("t".equalsIgnoreCase(alias) && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("towny".startsWith(prefix)) {
                return List.of("towny");
            }
        }
        return List.of();
    }
}
