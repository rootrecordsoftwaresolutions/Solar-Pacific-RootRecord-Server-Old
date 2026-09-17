package com.rootrecord.minecraft.roothelp.command;

import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import com.rootrecord.minecraft.roothelp.catalog.CommandCatalog;
import com.rootrecord.minecraft.roothelp.catalog.HelpCommandFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandsCommand implements CommandExecutor, TabCompleter {

    private final RootHelpPlugin plugin;
    private final Map<UUID, Integer> lastPage = new ConcurrentHashMap<>();

    public CommandsCommand(RootHelpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roothelp.commands")) {
            sender.sendMessage(plugin.colorize("&cYou don't have permission."));
            return true;
        }
        List<CommandCatalog.Line> all = plugin.catalog().linesFor(sender);
        if (all.isEmpty()) {
            sender.sendMessage(plugin.msg("cmds-empty"));
            return true;
        }

        int perPage = plugin.helpConfig().linesPerPage();
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        int page = resolvePage(sender, args, totalPages);

        if (page < 1 || page > totalPages) {
            sender.sendMessage(plugin.colorize(
                    plugin.rawMsg("cmds-page-invalid")
                            .replace("{page}", String.valueOf(page))
                            .replace("{pages}", String.valueOf(totalPages))));
            return true;
        }

        sender.sendMessage(plugin.colorize(
                plugin.rawMsg("cmds-header")
                        .replace("{page}", String.valueOf(page))
                        .replace("{pages}", String.valueOf(totalPages))));

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, all.size());
        String lastSection = null;
        for (int i = start; i < end; i++) {
            CommandCatalog.Line line = all.get(i);
            if (line.section() != null && !line.section().equals(lastSection)) {
                sender.sendMessage(plugin.colorize("&6" + line.section()));
                lastSection = line.section();
            }
            sender.sendMessage(HelpCommandFormat.line(line.command(), line.description()));
        }

        if (sender instanceof Player player) {
            lastPage.put(player.getUniqueId(), page);
            player.sendMessage(buildPager(page, totalPages));
        } else {
            sender.sendMessage(plugin.colorize("&7Page &f" + page + "&7/&f" + totalPages));
        }
        return true;
    }

    private int resolvePage(CommandSender sender, String[] args, int totalPages) {
        if (args.length == 0) {
            return 1;
        }
        String token = args[0].toLowerCase(Locale.ROOT);
        int current = 1;
        if (sender instanceof Player player) {
            current = lastPage.getOrDefault(player.getUniqueId(), 1);
        }
        if ("next".equals(token) || "n".equals(token)) {
            return Math.min(totalPages, current + 1);
        }
        if ("prev".equals(token) || "p".equals(token) || "back".equals(token)) {
            return Math.max(1, current - 1);
        }
        return parsePage(args[0]);
    }

    private static int parsePage(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private Component buildPager(int current, int totalPages) {
        Component pager = Component.empty();
        if (current > 1) {
            pager = pager.append(pageButton("« Prev", current - 1, NamedTextColor.YELLOW, false));
            pager = pager.append(Component.text("  ", NamedTextColor.GRAY));
        }

        int window = 7;
        int start = Math.max(1, current - window / 2);
        int end = Math.min(totalPages, start + window - 1);
        start = Math.max(1, end - window + 1);

        for (int p = start; p <= end; p++) {
            if (p > start) {
                pager = pager.append(Component.text(" ", NamedTextColor.GRAY));
            }
            NamedTextColor color = p == current ? NamedTextColor.GREEN : NamedTextColor.AQUA;
            pager = pager.append(pageButton(String.valueOf(p), p, color, p == current));
        }

        if (current < totalPages) {
            pager = pager.append(Component.text("  ", NamedTextColor.GRAY));
            pager = pager.append(pageButton("Next »", current + 1, NamedTextColor.YELLOW, false));
        }
        return pager;
    }

    private Component pageButton(String label, int page, NamedTextColor color, boolean bold) {
        Component text = bold
                ? Component.text("[" + label + "]", color, TextDecoration.BOLD)
                : Component.text("[" + label + "]", color);
        return text.clickEvent(ClickEvent.runCommand("/cmds " + page))
                .hoverEvent(HoverEvent.showText(Component.text("Go to page " + page, NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("1", "2", "3", "next", "prev").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
