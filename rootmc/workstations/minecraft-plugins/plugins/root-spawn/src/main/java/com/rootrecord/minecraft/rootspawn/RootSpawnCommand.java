package com.rootrecord.minecraft.rootspawn;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RootSpawnCommand implements CommandExecutor, TabCompleter {

    private final RootSpawnPlugin plugin;

    public RootSpawnCommand(RootSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        if (!player.hasPermission("rootspawn.build")) {
            player.sendMessage(plugin.colorize("&cNo permission."));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "map" -> handleMap(player);
            case "build" -> handleBuild(player, args);
            case "lava" -> handleLava(player);
            case "import" -> handleImport(player);
            case "stop" -> handleStop(player);
            case "reload" -> handleReload(player);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private boolean handleMap(Player player) {
        SpawnConfig cfg = plugin.config();
        try {
            plugin.waypointStore().beginSession(player.getWorld().getName(), player.getName());
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cCould not start mapping session: &f" + ex.getMessage()));
            return true;
        }
        player.sendMessage(plugin.colorize(cfg.prefix()
                + "&aSpawn mapper — &7right-click along the &fouter wall edge&7 in click order."));
        player.sendMessage(plugin.colorize("&7Inside the walls: no PvP, no hostile mobs, no griefing down to Y=0."));
        player.sendMessage(plugin.colorize("&7Outside the walls: PvP active. No dig/build within &f"
                + plugin.config().griefBufferBlocks() + "&7 blocks of the wall line."));
        player.sendMessage(plugin.colorize("&7Saves to &fplugins/RootMC/spawnarea.txt"));
        player.sendMessage(plugin.colorize("&7When done: &f/rootspawn import&7, then &f/rootspawn build&7 to refine."));
        plugin.enableBuildMode(player.getUniqueId(), BuildTarget.SPAWN_MAP);
        MapperItem.give(player, plugin, BuildTarget.SPAWN_MAP);
        return true;
    }

    private boolean handleBuild(Player player, String[] args) {
        if (args.length >= 2) {
            BuildTarget target = BuildTarget.fromArg(args[1]);
            if (target == null || target == BuildTarget.SPAWN_REFINE || target == BuildTarget.LAVA
                    || target == BuildTarget.SPAWN_MAP) {
                player.sendMessage(plugin.colorize(
                        "&eUsage: /rootspawn build &7| &e/rootspawn build chamber &7| &e/rootspawn build well"));
                return true;
            }
            return startPerimeterMap(player, target);
        }

        SpawnConfig cfg = plugin.config();
        if (plugin.boundary() == null || plugin.boundary().isEmpty()) {
            player.sendMessage(plugin.colorize(
                    "&cNo spawn walls loaded — map the perimeter first (&f/rootspawn map&c), then &f/rootspawn import&c."));
            return true;
        }
        player.sendMessage(plugin.colorize(cfg.prefix() + cfg.buildStarted()));
        player.sendMessage(plugin.colorize("&7Saves to &fplugins/RootMC/spawnarea-refined.txt"));
        plugin.enableBuildMode(player.getUniqueId(), BuildTarget.SPAWN_REFINE);
        MapperItem.give(player, plugin, BuildTarget.SPAWN_REFINE);
        return true;
    }

    private boolean startPerimeterMap(Player player, BuildTarget target) {
        SpawnConfig cfg = plugin.config();
        PolygonAreaStore store = target == BuildTarget.CHAMBER ? plugin.chamberStore() : plugin.wellStore();
        try {
            store.beginSession(player.getWorld().getName(), player.getName());
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cCould not start session: &f" + ex.getMessage()));
            return true;
        }
        String msg = target == BuildTarget.CHAMBER ? cfg.buildChamberStarted() : cfg.buildWellStarted();
        player.sendMessage(plugin.colorize(cfg.prefix() + msg));
        player.sendMessage(plugin.colorize("&7Saves to &fplugins/RootMC/" + target.fileName()));
        plugin.enableBuildMode(player.getUniqueId(), target);
        MapperItem.give(player, plugin, target);
        return true;
    }

    private boolean handleLava(Player player) {
        SpawnConfig cfg = plugin.config();
        try {
            plugin.lavaSpotStore().beginSession(player.getWorld().getName(), player.getName());
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cCould not start lava session: &f" + ex.getMessage()));
            return true;
        }
        player.sendMessage(plugin.colorize(cfg.prefix() + cfg.buildLavaStarted()));
        player.sendMessage(plugin.colorize("&7Saves to &fplugins/RootMC/lava.txt"));
        player.sendMessage(plugin.colorize(cfg.prefix() + cfg.buildLavaCeilingHint()));
        plugin.enableBuildMode(player.getUniqueId(), BuildTarget.LAVA);
        MapperItem.give(player, plugin, BuildTarget.LAVA);
        return true;
    }

    private boolean handleImport(Player player) {
        try {
            var path = plugin.waypointStore().file();
            if (!Files.isRegularFile(path)) {
                player.sendMessage(plugin.colorize("&cNo &fspawnarea.txt&c — run &f/rootspawn map&c first."));
                return true;
            }
            SpawnBoundary imported = SpawnBoundary.parse(Files.readString(path, StandardCharsets.UTF_8));
            if (imported.vertices().size() < 3) {
                player.sendMessage(plugin.colorize("&cNeed at least &f3&c perimeter waypoints before import."));
                return true;
            }
            plugin.applySpawnBoundary(imported, player.getName());
            player.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&aImported &f" + imported.vertices().size()
                    + "&a waypoint(s) into &fspawnarea-refined.txt&a."));
            player.sendMessage(plugin.colorize("&7Use &f/rootspawn build&7 to pull/push the walls, or &f/rootspawn reload&7."));
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cImport failed: &f" + ex.getMessage()));
            plugin.getLogger().warning("spawn import failed: " + ex.getMessage());
        }
        return true;
    }

    private boolean handleStop(Player player) {
        plugin.disableBuildMode(player.getUniqueId());
        plugin.lavaSpotStore().clearPending(player.getUniqueId());
        player.sendMessage(plugin.colorize(plugin.config().prefix() + "&7Mapper mode off."));
        return true;
    }

    private boolean handleReload(Player player) {
        plugin.reloadAll();
        int points = plugin.boundary() == null ? 0 : plugin.boundary().vertices().size();
        player.sendMessage(plugin.colorize("&aSpawn boundaries reloaded — wall polygon has &f" + points + "&a point(s)."));
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(plugin.colorize("&7Spawn mapping:"));
        player.sendMessage(plugin.colorize("&f/rootspawn map &8— &7click outer wall edge → spawnarea.txt"));
        player.sendMessage(plugin.colorize("&f/rootspawn import &8— &7waypoints → spawnarea-refined.txt"));
        player.sendMessage(plugin.colorize("&f/rootspawn build &8— &7refine the wall boundary"));
        player.sendMessage(plugin.colorize("&f/rootspawn build chamber &8| &fwell &8— &7basement footprints"));
        player.sendMessage(plugin.colorize("&f/rootspawn lava &8— &7chamber lava ceiling/floor pairs"));
        player.sendMessage(plugin.colorize("&f/rootspawn stop &8| &freload"));
        player.sendMessage(plugin.colorize("&7Or use &f/mapper&7 for the same tools (&f/mapper list&7)."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("rootspawn.build")) {
            return out;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String opt : List.of("map", "build", "import", "lava", "stop", "reload")) {
                if (opt.startsWith(prefix)) {
                    out.add(opt);
                }
            }
        } else if (args.length == 2 && "build".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String opt : List.of("chamber", "well")) {
                if (opt.startsWith(prefix)) {
                    out.add(opt);
                }
            }
        }
        return out;
    }
}
