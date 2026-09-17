package com.rootrecord.minecraft.rootmapper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class MapperCommand implements CommandExecutor, TabCompleter {

    private final RootMapperPlugin plugin;

    public MapperCommand(RootMapperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rootmapper.admin")) {
            player.sendMessage(plugin.colorize("&cNo permission."));
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> handleList(player);
            case "start" -> handleStart(player, args);
            case "refine" -> handleRefine(player, args);
            case "lava" -> handleLava(player, args);
            case "create" -> handleCreate(player, args);
            case "info" -> handleInfo(player, args);
            case "stop" -> handleStop(player);
            case "reload" -> handleReload(player);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private void sendUsage(Player player) {
        player.sendMessage(plugin.colorize("&e/mapper list"));
        player.sendMessage(plugin.colorize("&e/mapper start <area> &7— waypoint perimeter"));
        player.sendMessage(plugin.colorize("&e/mapper refine <area> &7— pull/push existing ring"));
        player.sendMessage(plugin.colorize("&e/mapper lava <area> &7— ceiling/floor pairs"));
        player.sendMessage(plugin.colorize("&e/mapper create <name> [waypoint|refine|lava] [label...]"));
        player.sendMessage(plugin.colorize("&e/mapper info <area> &7| &estop &7| &ereload"));
    }

    private boolean handleList(Player player) {
        List<MappedArea> areas = plugin.registry().areas();
        if (areas.isEmpty()) {
            player.sendMessage(plugin.colorize("&7No areas configured. Use &f/mapper create <name>&7."));
            return true;
        }
        player.sendMessage(plugin.colorize("&6Mapped areas:"));
        for (MappedArea area : areas) {
            PolygonBoundary boundary = plugin.registry().boundary(area.id());
            int points = boundary == null ? 0 : boundary.pointCount();
            player.sendMessage(plugin.colorize("&f" + area.id() + " &7— &f" + area.displayLabel()
                    + " &8(" + area.defaultMode().configKey() + ", " + points + " pts, "
                    + area.polygonFile() + ")"));
        }
        return true;
    }

    private boolean handleStart(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: /mapper start <area>"));
            return true;
        }
        MappedArea area = requireArea(player, args[1]);
        if (area == null) {
            return true;
        }
        PolygonAreaStore store = plugin.registry().polygonStore(area.id());
        try {
            store.beginSession(player.getWorld().getName(), player.getName());
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cCould not start session: &f" + ex.getMessage()));
            return true;
        }
        begin(player, area, MapperMode.WAYPOINT, plugin.registry().config().started());
        return true;
    }

    private boolean handleRefine(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: /mapper refine <area>"));
            return true;
        }
        MappedArea area = requireArea(player, args[1]);
        if (area == null) {
            return true;
        }
        PolygonBoundary boundary = plugin.registry().boundary(area.id());
        if (boundary == null || boundary.isEmpty()) {
            player.sendMessage(plugin.colorize("&cNo boundary loaded — map waypoints first or check the file."));
            return true;
        }
        plugin.registry().setBoundary(area.id(), boundary.copy());
        begin(player, area, MapperMode.REFINE, plugin.registry().config().refineStarted());
        return true;
    }

    private boolean handleLava(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: /mapper lava <area>"));
            return true;
        }
        MappedArea area = requireArea(player, args[1]);
        if (area == null) {
            return true;
        }
        LavaAreaStore store = plugin.registry().lavaStore(area.id());
        try {
            store.beginSession(player.getWorld().getName(), player.getName());
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cCould not start lava session: &f" + ex.getMessage()));
            return true;
        }
        begin(player, area, MapperMode.LAVA, plugin.registry().config().lavaStarted());
        player.sendMessage(plugin.colorize(plugin.registry().config().prefix()
                + plugin.registry().config().lavaCeilingHint()));
        return true;
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: /mapper create <name> [waypoint|refine|lava] [label...]"));
            return true;
        }
        MapperMode mode = args.length >= 3 ? MapperMode.parse(args[2]) : MapperMode.WAYPOINT;
        String label = null;
        if (args.length >= 4) {
            StringJoiner joiner = new StringJoiner(" ");
            for (int i = 3; i < args.length; i++) {
                joiner.add(args[i]);
            }
            label = joiner.toString();
        }
        String id = MapperFiles.sanitize(args[1]);
        MapperConfig cfg = plugin.registry().config();
        if (plugin.registry().area(id) != null) {
            player.sendMessage(plugin.colorize(cfg.prefix() + cfg.areaExists().replace("{area}", id)));
            return true;
        }
        if (!plugin.registry().createArea(args[1], mode, label)) {
            player.sendMessage(plugin.colorize(cfg.prefix() + cfg.areaExists().replace("{area}", id)));
            return true;
        }
        MappedArea created = plugin.registry().area(id);
        player.sendMessage(plugin.colorize(cfg.prefix() + cfg.areaCreated()
                .replace("{area}", id)
                .replace("{mode}", mode.configKey())
                .replace("{file}", created.polygonFile())));
        player.sendMessage(plugin.colorize("&7Use &f/mapper start " + id + "&7 to map the perimeter."));
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: /mapper info <area>"));
            return true;
        }
        MappedArea area = requireArea(player, args[1]);
        if (area == null) {
            return true;
        }
        PolygonBoundary boundary = plugin.registry().boundary(area.id());
        int points = boundary == null ? 0 : boundary.pointCount();
        String world = boundary == null || boundary.isEmpty() ? "—" : boundary.worldName();
        player.sendMessage(plugin.colorize("&6" + area.displayLabel() + " &8(" + area.id() + ")"));
        player.sendMessage(plugin.colorize("&7Mode: &f" + area.defaultMode().configKey()));
        player.sendMessage(plugin.colorize("&7Polygon: &fplugins/RootMC/" + area.polygonFile()
                + " &7(" + points + " points, world &f" + world + "&7)"));
        player.sendMessage(plugin.colorize("&7Lava file: &fplugins/RootMC/" + area.lavaFile()));
        return true;
    }

    private boolean handleStop(Player player) {
        plugin.clearSession(player.getUniqueId());
        player.sendMessage(plugin.colorize(plugin.registry().config().prefix()
                + plugin.registry().config().stopped()));
        return true;
    }

    private boolean handleReload(Player player) {
        plugin.reloadAll();
        player.sendMessage(plugin.colorize("&aRoot-Mapper config and boundaries reloaded."));
        return true;
    }

    private void begin(Player player, MappedArea area, MapperMode mode, String message) {
        MapperConfig cfg = plugin.registry().config();
        player.sendMessage(plugin.colorize(cfg.prefix() + message.replace("{area}", area.displayLabel())));
        player.sendMessage(plugin.colorize("&7Saves under &fplugins/RootMC/"));
        plugin.setSession(player.getUniqueId(), new MapperSession(area.id(), mode));
        MapperItem.give(player, plugin, area, mode);
    }

    private MappedArea requireArea(Player player, String rawId) {
        MappedArea area = plugin.registry().area(rawId);
        if (area == null) {
            player.sendMessage(plugin.colorize(plugin.registry().config().prefix()
                    + plugin.registry().config().areaUnknown().replace("{area}", rawId)));
        }
        return area;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("rootmapper.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String opt : List.of("list", "start", "refine", "lava", "create", "info", "stop", "reload")) {
                if (opt.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(opt);
                }
            }
        } else if (args.length == 2 && matches(args[0], "start", "refine", "lava", "info")) {
            out.addAll(plugin.registry().tabAreaIds(args[1]));
        } else if (args.length == 2 && matches(args[0], "create")) {
            if (!args[1].isBlank()) {
                out.add(MapperFiles.sanitize(args[1]));
            }
        } else if (args.length == 3 && matches(args[0], "create")) {
            for (String mode : List.of("waypoint", "refine", "lava")) {
                if (mode.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(mode);
                }
            }
        }
        return out;
    }

    private static boolean matches(String raw, String... options) {
        String key = raw.toLowerCase(Locale.ROOT);
        for (String opt : options) {
            if (opt.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
