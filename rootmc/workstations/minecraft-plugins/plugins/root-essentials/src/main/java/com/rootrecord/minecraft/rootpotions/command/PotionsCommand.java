package com.rootrecord.minecraft.rootpotions.command;

import com.rootrecord.minecraft.rootpotions.RootPotionsPlugin;
import com.rootrecord.minecraft.rootpotions.catalog.PotionEntry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PotionsCommand implements CommandExecutor, TabCompleter {

    private final RootPotionsPlugin plugin;

    public PotionsCommand(RootPotionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootpotions.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help", "?" -> {
                sendHelp(sender);
                yield true;
            }
            case "list" -> {
                int page = parsePage(args, 1);
                sendList(sender, page);
                yield true;
            }
            case "reload" -> handleReload(sender);
            case "search", "find" -> {
                handleSearch(sender, joinArgs(args, 1));
                yield true;
            }
            default -> {
                handleSearch(sender, joinArgs(args, 0));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rootpotions.reload")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.reloadLocalConfig();
        sender.sendMessage(plugin.colorize(
                plugin.rawMsg("reload-done").replace("{count}", String.valueOf(plugin.catalog().size()))));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.messages().help()) {
            sender.sendMessage(plugin.colorize(line));
        }
    }

    private void handleSearch(CommandSender sender, String query) {
        if (query == null || query.isBlank()) {
            sendHelp(sender);
            return;
        }
        String normalized = query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        var exact = plugin.catalog().byId(normalized);
        if (exact.isPresent()) {
            plugin.sendRecipe(sender, exact.get());
            return;
        }
        List<PotionEntry> hits = plugin.catalog().search(query);
        if (hits.isEmpty()) {
            sender.sendMessage(plugin.colorize(
                    plugin.rawMsg("not-found").replace("{query}", query)));
            return;
        }
        plugin.sendRecipe(sender, hits.getFirst());
        if (hits.size() > 1) {
            sender.sendMessage(plugin.colorize("&7Also matched:"));
            int shown = 0;
            for (int i = 1; i < hits.size(); i++) {
                if (shown >= 4) {
                    sender.sendMessage(plugin.colorize("&8… refine your search"));
                    break;
                }
                PotionEntry entry = hits.get(i);
                sender.sendMessage(plugin.colorize(
                        plugin.rawMsg("list-entry").replace("{name}", entry.name())
                                .replace("{ingredient}", entry.ingredient())
                                + " &8(&f/potions " + entry.id() + "&8)"));
                shown++;
            }
        }
    }

    private void sendList(CommandSender sender, int page) {
        List<PotionEntry> all = plugin.catalog().all();
        int pageSize = plugin.listPageSize();
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        int current = Math.min(Math.max(1, page), pages);
        int start = (current - 1) * pageSize;
        int end = Math.min(start + pageSize, all.size());

        sender.sendMessage(plugin.colorize(plugin.rawMsg("list-header")
                .replace("{page}", String.valueOf(current))
                .replace("{pages}", String.valueOf(pages))));
        for (int i = start; i < end; i++) {
            PotionEntry entry = all.get(i);
            sender.sendMessage(plugin.colorize(
                    plugin.rawMsg("list-entry")
                            .replace("{name}", entry.name())
                            .replace("{ingredient}", entry.ingredient().isBlank() ? "—" : entry.ingredient())));
        }
        if (pages > 1) {
            String footer;
            if (current > 1 && current < pages) {
                footer = plugin.rawMsg("list-prev-footer")
                        .replace("{page}", String.valueOf(current))
                        .replace("{pages}", String.valueOf(pages))
                        .replace("{prev}", String.valueOf(current - 1))
                        .replace("{next}", String.valueOf(current + 1));
            } else if (current < pages) {
                footer = plugin.rawMsg("list-footer")
                        .replace("{page}", String.valueOf(current))
                        .replace("{pages}", String.valueOf(pages))
                        .replace("{next}", String.valueOf(current + 1));
            } else {
                footer = "&7Page &f" + current + "&7/&f" + pages
                        + " &8— &f/potions list " + (current - 1);
            }
            sender.sendMessage(plugin.colorize(footer));
        }
    }

    private static int parsePage(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(args[index]));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static String joinArgs(String[] args, int from) {
        if (from >= args.length) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)).trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rootpotions.use")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list", "search", "help"));
            if (sender.hasPermission("rootpotions.reload")) {
                options.add("reload");
            }
            plugin.catalog().all().stream()
                    .map(PotionEntry::id)
                    .forEach(options::add);
            return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return StringUtil.copyPartialMatches(args[1], List.of("1", "2", "3"), new ArrayList<>());
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("search") || args[0].equalsIgnoreCase("find"))) {
            String partial = joinArgs(args, 1).toLowerCase(Locale.ROOT);
            return plugin.catalog().all().stream()
                    .map(PotionEntry::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }
}
