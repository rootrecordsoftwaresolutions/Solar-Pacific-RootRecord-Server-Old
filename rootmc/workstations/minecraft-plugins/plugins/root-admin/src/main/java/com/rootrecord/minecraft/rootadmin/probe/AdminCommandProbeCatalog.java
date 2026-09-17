package com.rootrecord.minecraft.rootadmin.probe;

import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Detects unauthorized admin / op command attempts from non-staff players. */
public final class AdminCommandProbeCatalog {

    private AdminCommandProbeCatalog() {}

    /** LuckPerms admin group or explicit bypass — never flagged. */
    public static boolean isStaff(Player player) {
        return player.hasPermission("group.admin")
                || player.hasPermission("rootadmin.probe.bypass");
    }

    public record ProbeMatch(String label, String reason) {}

    /** @return match when this player should count a failed admin probe attempt */
    public static ProbeMatch matchUnauthorized(Player player, String commandLine) {
        if (player == null || commandLine == null || !commandLine.startsWith("/")) {
            return null;
        }
        if (isStaff(player)) {
            return null;
        }
        String trimmed = commandLine.trim();
        if (trimmed.length() <= 1) {
            return null;
        }
        String withoutSlash = trimmed.substring(1);
        int space = withoutSlash.indexOf(' ');
        String label = (space < 0 ? withoutSlash : withoutSlash.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        String[] args = space < 0 ? new String[0] : withoutSlash.substring(space + 1).trim().split("\\s+");

        ProbeMatch rootAdmin = matchRootAdmin(player, label, args);
        if (rootAdmin != null) {
            return rootAdmin;
        }
        ProbeMatch sub = matchSubcommandAdmin(player, label, args);
        if (sub != null) {
            return sub;
        }
        ProbeMatch vanilla = matchVanillaOp(player, label);
        if (vanilla != null) {
            return vanilla;
        }
        return matchPluginPermission(player, label, args);
    }

    private static ProbeMatch matchRootAdmin(Player player, String label, String[] args) {
        String node = ROOT_ADMIN_NODES.get(label);
        if (node == null) {
            return null;
        }
        if ("gamemode".equals(node)) {
            GameMode mode = gamemodeFromLabelOrArg(label, args);
            if (mode != null && !AdminPermissions.canUseGamemode(player, mode)) {
                return new ProbeMatch(label, "gamemode:" + mode.name().toLowerCase(Locale.ROOT));
            }
            if (mode == null && !AdminPermissions.has(player, "gamemode")) {
                return new ProbeMatch(label, "rootadmin.gamemode");
            }
            return null;
        }
        if ("fly".equals(node)) {
            boolean self = args.length == 0;
            if (self && !AdminPermissions.has(player, "fly")) {
                return new ProbeMatch(label, "rootadmin.fly");
            }
            if (!self && !AdminPermissions.has(player, "fly.others")) {
                return new ProbeMatch(label, "rootadmin.fly.others");
            }
            return null;
        }
        // /ec self is public; only probe opening another player's ender chest.
        if ("echest".equals(node)) {
            if (args.length == 0) {
                return null;
            }
            if (!AdminPermissions.has(player, "enderchest.others")
                    && !AdminPermissions.has(player, "enderchest.open")) {
                return new ProbeMatch(label, "rootadmin.enderchest.others");
            }
            return null;
        }
        if (!AdminPermissions.has(player, node)) {
            return new ProbeMatch(label, "rootadmin." + node);
        }
        return null;
    }

    private static ProbeMatch matchSubcommandAdmin(Player player, String label, String[] args) {
        if (args.length == 0) {
            return null;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (label) {
            case "rootstat", "rrlink", "bn", "rootmcapp" -> switch (sub) {
                case "sync" -> deniedIf(player, "rootstat.sync", label + " " + sub);
                case "reload" -> deniedIf(player, "rootstat.reload", label + " " + sub);
                default -> null;
            };
            case "shop" -> switch (sub) {
                case "reload", "purge" -> deniedIf(player, "rootshops.admin", label + " " + sub);
                default -> null;
            };
            case "rootspawn" -> deniedIf(player, "rootspawn.build", label + " " + sub);
            case "rootrestart", "rootstop" -> deniedIf(player, "rootrestart.admin", label);
            case "rootupkeep" -> deniedIf(player, "rootupkeep.admin", label + " " + sub);
            case "mapper" -> deniedIf(player, "rootmapper.admin", label + " " + sub);
            case "potions" -> "reload".equals(sub) ? deniedIf(player, "rootpotions.reload", label + " " + sub) : null;
            case "grant" -> deniedIf(player, "rootessentials.grant", label);
            default -> matchPluginReload(label, sub, player);
        };
    }

    private static ProbeMatch matchPluginReload(String label, String sub, Player player) {
        if (!"reload".equals(sub)) {
            return null;
        }
        String perm = label + ".reload";
        if (ADMIN_RELOAD_COMMANDS.contains(label) && !player.hasPermission(perm)) {
            return new ProbeMatch(label + " reload", perm);
        }
        return null;
    }

    private static ProbeMatch deniedIf(Player player, String permission, String display) {
        if (player.hasPermission(permission)) {
            return null;
        }
        return new ProbeMatch(display, permission);
    }

    private static ProbeMatch matchVanillaOp(Player player, String label) {
        if (!VANILLA_OP_COMMANDS.contains(label)) {
            return null;
        }
        if (player.isOp()) {
            return null;
        }
        return new ProbeMatch(label, "minecraft.op");
    }

    private static ProbeMatch matchPluginPermission(Player player, String label, String[] args) {
        Command command = player.getServer().getCommandMap().getCommand(label);
        if (!(command instanceof PluginCommand pluginCommand)) {
            return null;
        }
        String perm = pluginCommand.getPermission();
        if (perm == null || perm.isBlank() || player.hasPermission(perm)) {
            return null;
        }
        if (!isAdminPermissionNode(perm, label)) {
            return null;
        }
        String display = args.length > 0 ? label + " " + args[0] : label;
        return new ProbeMatch(display, perm);
    }

    private static boolean isAdminPermissionNode(String perm, String label) {
        String p = perm.toLowerCase(Locale.ROOT);
        if (p.startsWith("rootadmin.")
                || p.equals("rootessentials.grant")
                || p.equals("rootmc.grant")
                || p.equals("rootstat.sync")
                || p.equals("rootstat.reload")
                || p.equals("rootmc.reload")
                || p.equals("rootshops.admin")
                || p.equals("rootspawn.build")
                || p.equals("rootrestart.admin")
                || p.equals("rootupkeep.admin")
                || p.equals("rootmapper.admin")
                || p.startsWith("bukkit.command.")
                || p.startsWith("minecraft.command.")) {
            return true;
        }
        if (p.endsWith(".reload") && (p.startsWith("root") || ADMIN_RELOAD_COMMANDS.contains(label))) {
            return true;
        }
        return false;
    }

    private static GameMode gamemodeFromLabelOrArg(String label, String[] args) {
        String token = switch (label) {
            case "gms" -> "survival";
            case "gmc" -> "creative";
            case "gma" -> "adventure";
            case "gmsp" -> "spectator";
            default -> args.length > 0 ? args[0] : null;
        };
        if (token == null) {
            return null;
        }
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static final Map<String, String> ROOT_ADMIN_NODES = Map.ofEntries(
            Map.entry("kick", "kick"),
            Map.entry("ban", "ban"),
            Map.entry("tempban", "tempban"),
            Map.entry("unban", "unban"),
            Map.entry("mute", "mute"),
            Map.entry("unmute", "unmute"),
            Map.entry("tpoffline", "tpoffline"),
            Map.entry("vanish", "vanish"),
            Map.entry("socialspy", "socialspy"),
            Map.entry("invsee", "invsee"),
            Map.entry("echest", "echest"),
            Map.entry("enderchest", "echest"),
            Map.entry("ec", "echest"),
            Map.entry("setspawn", "setspawn"),
            Map.entry("tppos", "tppos"),
            Map.entry("sudo", "sudo"),
            Map.entry("give", "give"),
            Map.entry("item", "give"),
            Map.entry("speed", "speed"),
            Map.entry("realname", "realname"),
            Map.entry("whois", "whois"),
            Map.entry("recipe", "recipe"),
            Map.entry("skull", "skull"),
            Map.entry("lightning", "lightning"),
            Map.entry("ext", "ext"),
            Map.entry("extinguish", "ext"),
            Map.entry("mutechat", "mutechat"),
            Map.entry("unmutechat", "unmutechat"),
            Map.entry("tpo", "tpo"),
            Map.entry("tpohere", "tpohere"),
            Map.entry("tpall", "tpall"),
            Map.entry("fly", "fly"),
            Map.entry("god", "god"),
            Map.entry("gamemode", "gamemode"),
            Map.entry("gm", "gamemode"),
            Map.entry("gms", "gamemode"),
            Map.entry("gmc", "gamemode"),
            Map.entry("gma", "gamemode"),
            Map.entry("gmsp", "gamemode"),
            Map.entry("broadcast", "broadcast"),
            Map.entry("time", "time"),
            Map.entry("day", "day"),
            Map.entry("night", "night"),
            Map.entry("weather", "weather"),
            Map.entry("remove", "remove"),
            Map.entry("burn", "burn"),
            Map.entry("world", "world"),
            Map.entry("setwarp", "setwarp"),
            Map.entry("delwarp", "delwarp"),
            Map.entry("worldborder", "worldborder"),
            Map.entry("wb", "worldborder")
    );

    private static final Set<String> ADMIN_RELOAD_COMMANDS = Set.of(
            "rootrewards", "rootactivity", "rootannouncer", "rootblueprints", "rootcontracts",
            "rootloans", "rootquestionnaire", "rootranks", "rootterritories", "rootexplore"
    );

    /** Vanilla / Paper server commands that require operator. */
    private static final Set<String> VANILLA_OP_COMMANDS = Set.of(
            "op", "deop",
            "ban-ip", "pardon-ip", "banlist",
            "whitelist",
            "stop", "restart",
            "save-all", "save-on", "save-off",
            "fill", "clone", "setblock", "summon",
            "effect", "enchant", "experience", "xp",
            "spreadplayers", "playsound", "stopsound",
            "title", "tellraw", "data", "datapack",
            "debug", "function", "forceload", "jfr",
            "locate", "loot", "particle", "perf", "place",
            "publish", "reload", "ride", "say", "schedule", "scoreboard",
            "seed", "setworldspawn", "spawnpoint", "spectate",
            "tag", "team", "tick", "trigger",
            "attribute", "bossbar", "difficulty", "return", "send", "random"
    );
}
