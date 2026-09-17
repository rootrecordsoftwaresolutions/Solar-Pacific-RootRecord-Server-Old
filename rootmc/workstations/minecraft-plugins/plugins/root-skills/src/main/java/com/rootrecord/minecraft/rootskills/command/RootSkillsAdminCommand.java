package com.rootrecord.minecraft.rootskills.command;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class RootSkillsAdminCommand implements CommandExecutor, TabCompleter {

    private final RootSkillsPlugin plugin;

    public RootSkillsAdminCommand(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootskills.admin") && !sender.hasPermission("rootskills.reload")) {
            sender.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.msg("admin.usage"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("rootskills.reload") && !sender.hasPermission("rootskills.admin")) {
                    sender.sendMessage(plugin.msg("general.no-permission"));
                    return true;
                }
                plugin.reloadLocal();
                sender.sendMessage(plugin.msg("general.reload-ok"));
            }
            case "setlevel", "level" -> {
                if (!sender.hasPermission("rootskills.admin")) {
                    sender.sendMessage(plugin.msg("general.no-permission"));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(plugin.colorize("&e/rootskills setlevel <player> <skill> <level>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                Optional<SkillId> skill = SkillId.fromString(args[2]);
                if (skill.isEmpty()) {
                    sender.sendMessage(plugin.msg("skills.unknown").replace("{skill}", args[2]));
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(plugin.msg("admin.usage"));
                    return true;
                }
                plugin.setLevel(target.getUniqueId(), skill.get(), level);
                sender.sendMessage(plugin.msg("admin.level-set")
                        .replace("{player}", args[1])
                        .replace("{skill}", skill.get().key())
                        .replace("{level}", Integer.toString(level)));
            }
            case "xp" -> {
                if (!sender.hasPermission("rootskills.admin")) {
                    sender.sendMessage(plugin.msg("general.no-permission"));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(plugin.colorize("&e/rootskills xp <player> <skill> <amount>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                Optional<SkillId> skill = SkillId.fromString(args[2]);
                if (skill.isEmpty()) {
                    sender.sendMessage(plugin.msg("skills.unknown").replace("{skill}", args[2]));
                    return true;
                }
                long xp;
                try {
                    xp = Long.parseLong(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(plugin.msg("admin.usage"));
                    return true;
                }
                plugin.repository().setXp(target.getUniqueId(), skill.get(), xp);
                sender.sendMessage(plugin.msg("admin.xp-set")
                        .replace("{player}", args[1])
                        .replace("{skill}", skill.get().key())
                        .replace("{xp}", Long.toString(xp)));
            }
            case "migrate" -> {
                if (!sender.hasPermission("rootskills.admin")) {
                    sender.sendMessage(plugin.msg("general.no-permission"));
                    return true;
                }
                boolean dryRun = false;
                UUID only = null;
                for (int i = 1; i < args.length; i++) {
                    String a = args[i];
                    if ("mcmmo".equalsIgnoreCase(a)) {
                        continue;
                    }
                    if ("--dry-run".equalsIgnoreCase(a)) {
                        dryRun = true;
                        continue;
                    }
                    if ("--player".equalsIgnoreCase(a) && i + 1 < args.length) {
                        String who = args[++i];
                        try {
                            only = UUID.fromString(who);
                        } catch (IllegalArgumentException ex) {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(who);
                            only = op.getUniqueId();
                        }
                    }
                }
                sender.sendMessage(plugin.msg("admin.migrate-started"));
                plugin.mcMmoMigrator().migrate(sender, dryRun, only);
            }
            case "booster" -> {
                if (args.length >= 3 && "buy".equalsIgnoreCase(args[1])) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(plugin.msg("general.players-only"));
                        return true;
                    }
                    plugin.boosterManager().buy(player, args[2]);
                } else {
                    sender.sendMessage(plugin.colorize("&e/rootskills booster buy <xp2|xp15>"));
                }
            }
            case "party" -> handleParty(sender, args);
            case "prestige" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage(plugin.colorize("&e/rootskills prestige <skill>"));
                    return true;
                }
                Optional<SkillId> skill = SkillId.fromString(args[1]);
                if (skill.isEmpty()) {
                    sender.sendMessage(plugin.msg("skills.unknown").replace("{skill}", args[1]));
                    return true;
                }
                var profile = plugin.repository().getOrCreate(player.getUniqueId());
                if (!plugin.prestigeManager().prestige(profile, skill.get())) {
                    player.sendMessage(plugin.msg("prestige.not-ready")
                            .replace("{threshold}", Integer.toString(plugin.prestigeManager().threshold())));
                }
            }
            default -> sender.sendMessage(plugin.msg("admin.usage"));
        }
        return true;
    }

    private void handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("general.players-only"));
            return;
        }
        if (!plugin.partyManager().enabled()) {
            sender.sendMessage(plugin.msg("general.feature-disabled"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&e/rootskills party <create|invite|accept|leave>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                plugin.partyManager().create(player.getUniqueId(),
                        args.length >= 3 ? args[2] : player.getName() + "'s party");
                player.sendMessage(plugin.msg("party.created"));
            }
            case "invite" -> {
                if (args.length < 3) {
                    player.sendMessage(plugin.colorize("&e/rootskills party invite <player>"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    player.sendMessage(plugin.msg("general.no-permission"));
                    return;
                }
                if (plugin.partyManager().invite(player.getUniqueId(), target.getUniqueId())) {
                    target.sendMessage(plugin.colorize("&eParty invite from &6" + player.getName()
                            + "&e — /rootskills party accept"));
                    player.sendMessage(plugin.colorize("&aInvite sent."));
                } else {
                    player.sendMessage(plugin.msg("party.not-in"));
                }
            }
            case "accept" -> {
                if (plugin.partyManager().accept(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("party.joined"));
                } else {
                    player.sendMessage(plugin.msg("party.full"));
                }
            }
            case "leave" -> {
                plugin.partyManager().leave(player.getUniqueId());
                player.sendMessage(plugin.msg("party.left"));
            }
            default -> player.sendMessage(plugin.colorize("&e/rootskills party <create|invite|accept|leave>"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "setlevel", "level", "xp", "migrate", "booster", "party", "prestige"),
                    args[0]);
        }
        if (args.length == 2 && "migrate".equalsIgnoreCase(args[0])) {
            return filter(List.of("mcmmo", "--dry-run", "--player"), args[1]);
        }
        if (args.length == 2 && "booster".equalsIgnoreCase(args[0])) {
            return filter(List.of("buy"), args[1]);
        }
        if (args.length == 3 && "booster".equalsIgnoreCase(args[0])) {
            return filter(List.of("xp2", "xp15"), args[2]);
        }
        if (args.length == 2 && "party".equalsIgnoreCase(args[0])) {
            return filter(List.of("create", "invite", "accept", "leave"), args[1]);
        }
        if (args.length == 3 && ("setlevel".equalsIgnoreCase(args[0]) || "level".equalsIgnoreCase(args[0])
                || "xp".equalsIgnoreCase(args[0]) || "prestige".equalsIgnoreCase(args[0]))) {
            List<String> skills = new ArrayList<>();
            for (SkillId id : SkillId.values()) {
                skills.add(id.key());
            }
            return filter(skills, args[2]);
        }
        if (args.length == 2 && "prestige".equalsIgnoreCase(args[0])) {
            List<String> skills = new ArrayList<>();
            for (SkillId id : SkillId.values()) {
                skills.add(id.key());
            }
            return filter(skills, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
