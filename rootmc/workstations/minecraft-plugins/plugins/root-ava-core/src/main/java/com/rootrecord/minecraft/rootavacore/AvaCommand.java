package com.rootrecord.minecraft.rootavacore;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootavacore.AvaConfig.HireConfig;
import com.rootrecord.minecraft.rootavacore.fill.AvaFillService;
import com.rootrecord.minecraft.rootavacore.hire.AvaHireService;
import com.rootrecord.minecraft.rootavacore.presence.AvaPresenceService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AvaCommand implements CommandExecutor, TabCompleter {

    private final RootAvaCorePlugin plugin;

    public AvaCommand(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return status(sender);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help", "?", "commands", "cmds", "usage" -> help(sender);
            case "reload" -> reload(sender);
            case "presence", "p" -> presence(sender, Arrays.copyOfRange(args, 1, args.length));
            case "hire", "job" -> hire(sender, Arrays.copyOfRange(args, 1, args.length));
            case "chest" -> chest(sender);
            case "speed" -> speed(sender);
            case "speak" -> speak(sender, Arrays.copyOfRange(args, 1, args.length));
            case "hour", "sidekick", "follow", "buddy", "mine", "excavate", "dig",
                    "teardown", "flatten", "chunk", "quarry", "claim", "clear", "circle",
                    "minedown", "shaft", "down",
                    "confirm", "yes", "accept", "sign", "cancel", "stopall", "cancelall" ->
                    hire(sender, prepend(sub, Arrays.copyOfRange(args, 1, args.length)));
            case "work", "autonomy" -> work(sender, Arrays.copyOfRange(args, 1, args.length));
            case "gift", "give" -> gift(sender);
            case "gear", "loadout" -> gear(sender);
            case "judgment", "judge", "kill" -> judgment(sender, Arrays.copyOfRange(args, 1, args.length));
            case "claims" -> claimsDump(sender);
            case "schem", "schematic", "blueprint" -> schem(sender, Arrays.copyOfRange(args, 1, args.length));
            case "eco", "economy", "wallet", "gold" -> eco(sender);
            case "plan" -> plan(sender, Arrays.copyOfRange(args, 1, args.length));
            case "trust", "invite" -> trust(sender, Arrays.copyOfRange(args, 1, args.length));
            case "stop" -> stopWork(sender, Arrays.copyOfRange(args, 1, args.length));
            case "play" -> startPlay(sender);
            case "world", "testworld", "test" -> avaWorld(sender);
            case "fill" -> fill(sender, Arrays.copyOfRange(args, 1, args.length));
            case "layer" -> layer(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> help(sender);
        };
    }

    private boolean help(CommandSender sender) {
        if (!sender.hasPermission("rootavacore.use") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        hireHowTo(sender);
        ChatUi.section(sender, "Other");
        ChatUi.entry(sender, "/ava", "status");
        ChatUi.entry(sender, "/ava world", "Ava's test world (from play.rootmc.net)");
        ChatUi.entry(sender, "/ava gift", "hand items into Ava's claim chests");
        ChatUi.entry(sender, "/ava fill here", "holes under the block you look at (held block)");
        ChatUi.entry(sender, "/ava fill 10", "11×11 square · holes from 10 below · held block");
        ChatUi.entry(sender, "/ava fill circle 5", "circle radius 5 · from 10 below");
        ChatUi.entry(sender, "/ava layer 8", "column · 8 deep from standing Y");
        ChatUi.entry(sender, "/ava layer circle 5 10", "circle r5 · 10 deep from standing Y");
        ChatUi.entry(sender, "/ava chest", "set the hire drop chest you are looking at");
        ChatUi.entry(sender, "/ava speed", "1000 G · faster hire/farm/mine ticks");
        ChatUi.entry(sender, "/ava work", "Ava's own-claim work");
        ChatUi.entry(sender, "/ava stop", "work orders · red Stop");
        ChatUi.entry(sender, "/ava eco", "Server Reserve (Ava's wallet)");
        if (canSeeStaffHelp(sender)) {
            ChatUi.section(sender, "Staff");
            ChatUi.entry(sender, "/ava presence", "spawn · despawn · claim · here · follow · hang · stop");
            ChatUi.entry(sender, "/ava play", "arm judgment / play");
            ChatUi.entry(sender, "/ava stop", "work-order list + red Stop (hire · homestead · play)");
            ChatUi.entry(sender, "/ava gear", "god loadout on Ava_Ivy");
            ChatUi.entry(sender, "/ava judgment", "<player> [reason]");
            ChatUi.entry(sender, "/ava plan", "status · set · theme · clear");
            ChatUi.entry(sender, "/ava trust", "<player> on Ava_Ivy's claim");
            ChatUi.entry(sender, "/ava schem", "capture · dump · rebuild · stop");
            ChatUi.entry(sender, "/ava claims", "RCON dump of Ava-owned claims");
            ChatUi.entry(sender, "/ava speak", "<message> · broadcast as Ava");
        }
        ChatUi.links(sender, "Economy", "https://rootmc.net/economy", "Plugins", "https://rootmc.net/plugins/root-ava-core/");
        return true;
    }

    private boolean avaWorld(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!sender.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (plugin.config().meshWorldTransfers()) {
            String peer = plugin.config().meshWorldPeer();
            player.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&eOpening Ava's world…"));
            if (!player.performCommand("goto " + peer)
                    && !player.performCommand("goto test")) {
                player.sendMessage(plugin.colorize(plugin.config().prefix()
                        + "&cTransfer unavailable. Use &f/goto ava &cor &f/totest&c."));
            }
            return true;
        }
        if (!player.performCommand("c spawn Ava_Ivy")) {
            player.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&7You're on Ava's world. &f/c spawn Ava_Ivy &7if you need her claim."));
        }
        return true;
    }

    private boolean fill(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!sender.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (args.length == 0) {
            ChatUi.section(sender, "Fill");
            ChatUi.entry(sender, "Hand", "hold the block to place");
            ChatUi.entry(sender, "/ava fill here", "one column under the block you look at");
            ChatUi.entry(sender, "/ava fill 2", "3×3 square · holes from 10 below");
            ChatUi.entry(sender, "/ava fill 10", "11×11 square · holes from 10 below");
            ChatUi.entry(sender, "/ava fill 16", "17×17 square (max)");
            ChatUi.entry(sender, "/ava fill circle 5", "circle radius 5 · from 10 below");
            ChatUi.entry(sender, "/ava fill circle 5 20", "circle r5 · 20 deep");
            ChatUi.tip(sender, "Places from the bottom up. 0.01 G + 1 matching item per hole. Own land only.");
            return true;
        }
        return replyFill(sender, plugin.fill().startFill(player, args));
    }

    private boolean layer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!sender.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (args.length == 0) {
            ChatUi.section(sender, "Layer");
            ChatUi.entry(sender, "/ava layer 8", "column · 8 layers down from standing Y");
            ChatUi.entry(sender, "/ava layer here 12", "looked-at column · 12 deep");
            ChatUi.entry(sender, "/ava layer 10 6", "11×11 · 6 deep from standing Y");
            ChatUi.entry(sender, "/ava layer circle 5 10", "circle r5 · 10 deep from standing Y");
            ChatUi.tip(sender, "Same as fill, from standing Y. 0.01 G + 1 item per hole.");
            return true;
        }
        return replyFill(sender, plugin.fill().startLayer(player, args));
    }

    private boolean replyFill(CommandSender sender, String code) {
        if (code != null && code.startsWith("ok:")) {
            String[] parts = code.split(":", 4);
            sender.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&aFilling &f" + (parts.length > 1 ? parts[1] : "?")
                    + " &aholes with &f" + (parts.length > 2 ? parts[2].replace('_', ' ') : "block")
                    + " &8· &f" + (parts.length > 3 ? parts[3] : "?") + " G &7max · /ava stop fill"));
            return true;
        }
        String msg = switch (code == null ? "" : code) {
            case "players_only" -> "&cPlayers only.";
            case "already_running" -> "&eAlready filling. &f/ava stop fill";
            case "no_block" -> "&cHold a placeable block in your hand.";
            case "no_economy" -> "&cEconomy offline — can't charge Gold.";
            case "broke" -> "&cNeed &f0.01 G &cper block. Not enough Gold.";
            case "no_target" -> "&cLook at a block (or stand on one).";
            case "no_holes" -> "&eNo holes in that area.";
            case "not_in_claim", "not_your_claim" -> "&cOnly on land you own.";
            case "claims_offline" -> "&cClaims are offline.";
            case "bad_area" -> "&cArea 2–16 (2 = 3×3, 10 = 11×11, 16 = 17×17).";
            case "bad_radius" -> "&cCircle radius 1–16.";
            case "bad_depth" -> "&cDepth 1–64.";
            case "usage_circle" -> "&7/ava fill circle <1-16> [depth]";
            case "usage_area" -> "&7/ava fill area <2-16> [depth]";
            case "usage_layer_depth" -> "&7/ava layer circle|area <n> <depth>";
            case "usage_layer" -> "&7/ava layer <depth> · /ava layer 10 6 · /ava layer circle 5 10";
            default -> "&7/ava fill here|2-16|circle <r>  ·  /ava layer …";
        };
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + msg));
        return true;
    }

    private boolean hireHowTo(CommandSender sender) {
        HireConfig h = plugin.config().hire();
        String sidekick = h.sidekickGold() > 0
                ? goldFmt(h.sidekickGold()) + "G"
                : goldFmt(h.sidekickPerMinute()) + "G/min";
        ChatUi.banner(sender, "Hire Ava");
        ChatUi.tip(sender, "Only on land you own. Claim clear: stand within 8 of that ring (center or rim, Y 0+) — full circle including your expansions. Natural blocks only; builds stay.");
        ChatUi.section(sender, "How");
        ChatUi.entry(sender, "1", "Click a job below, or type the command");
        ChatUi.entry(sender, "2", "Claim / minedown: read the quote, then click Confirm");
        ChatUi.entry(sender, "3", "/ava stop lists work orders · red [Stop] · contracts: no refund");
        ChatUi.section(sender, "Jobs");
        ChatUi.entry(sender, "/ava hire hour", goldFmt(h.hourGold()) + "G", h.hourMinutes() + " min claim work");
        ChatUi.entry(sender, "/ava hire sidekick", sidekick, "follows you");
        ChatUi.entry(sender, "/ava hire mine", goldFmt(h.mineBase()) + "G", "dig around you");
        ChatUi.entry(sender, "/ava hire teardown", goldFmt(h.teardownBase()) + "G", "clear surface junk");
        String claimHint = plugin.hire().nextContractHint(null, AvaHireService.JobType.CHUNK);
        ChatUi.entry(sender, "/ava hire claim", claimHint, "full claim circle · expansions included · builds left");
        ChatUi.entry(
                sender,
                "/ava hire minedown",
                claimHint,
                h.minedownSize() + "×" + h.minedownSize() + " shaft down from your feet");
        ChatUi.tip(sender, "Paid jobs keep going if you disconnect or the server restarts.");
        sender.sendMessage(
                Component.text()
                        .append(ChatLinks.action("[Hour]", "/ava hire hour"))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(ChatLinks.action("[Sidekick]", "/ava hire sidekick"))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(ChatLinks.action("[Mine]", "/ava hire mine"))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(ChatLinks.action("[Teardown]", "/ava hire teardown"))
                        .build());
        sender.sendMessage(
                Component.text()
                        .append(ChatLinks.action("[Claim]", "/ava hire claim"))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(ChatLinks.action("[Minedown]", "/ava hire minedown"))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(ChatLinks.action("[Confirm]", "/ava hire confirm"))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(ChatLinks.action("[Status]", "/ava hire status"))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(ChatLinks.danger("[Stop]", "/ava stop"))
                        .build());
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!sender.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (!plugin.config().enabled()) {
            sender.sendMessage(plugin.colorize(plugin.config().disabled()));
            return true;
        }
        int online = Bukkit.getOnlinePlayers().size();
        String line = plugin.config().statusLine()
                .replace("{version}", plugin.getDescription().getVersion())
                .replace("{online}", String.valueOf(online))
                .replace("{tps}", formatTps());
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + line));
        AvaPresenceService presence = plugin.presence();
        if (presence != null) {
            String mode = presence.isSpawned()
                    ? presence.mode().name().toLowerCase(Locale.ROOT)
                    : "despawned";
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&7presence &f" + mode
                            + " &8· &7fleet &f" + presence.spawnedCount()
                            + " &8· &7play &f" + (plugin.powers().playEnabled() ? "on" : "off")
                            + " &8· &7hire &f" + (plugin.config().hire().enabled() ? "open" : "off")
                            + " &8· &7jobs &f" + plugin.hire().activeCount()
                            + " &8· &7work &f" + plugin.autonomy().phase().name().toLowerCase(Locale.ROOT)));
        }
        if (plugin.economy() != null) {
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&7eco &f" + plugin.economy().statusLine()));
        }
        if (sender instanceof Player player) {
            AvaHireService.Job job = plugin.hire().jobFor(player.getUniqueId());
            if (job != null) {
                sender.sendMessage(plugin.colorize(
                        plugin.config().prefix() + "&7your job &f" + AvaHireService.jobLabel(job.type)
                                + hireJobDetail(job)));
            }
        }
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&8/ava help &7for commands"));
        return true;
    }

    private boolean eco(CommandSender sender) {
        if (!sender.hasPermission("rootavacore.use") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (plugin.economy() == null) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cEconomy unavailable."));
            return true;
        }
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7" + plugin.economy().statusLine()));
        return true;
    }

    private boolean trust(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootavacore.admin") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7/ava trust <player>"));
            return true;
        }
        String name = args[0].trim();
        Player online = Bukkit.getPlayerExact(name);
        if (online == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(name)) {
                    online = p;
                    break;
                }
            }
        }
        UUID memberId = online != null ? online.getUniqueId() : Bukkit.getOfflinePlayer(name).getUniqueId();
        String memberName = online != null ? online.getName() : name;
        try {
            org.bukkit.plugin.Plugin rc = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (rc == null || !rc.isEnabled()) {
                sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cRoot-Claims offline."));
                return true;
            }
            Object service = rc.getClass().getMethod("claims").invoke(rc);
            UUID ava = plugin.config().powers().avaUuid();
            service.getClass()
                    .getMethod("adminTrust", UUID.class, UUID.class, String.class)
                    .invoke(service, ava, memberId, memberName);
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&aTrusted &f" + memberName + " &7on Ava_Ivy's claim."));
            return true;
        } catch (NoSuchMethodException ex) {
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&eUpdate Root-Claims (needs 1.8.112+) for /ava trust."));
            return true;
        } catch (Throwable t) {
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&cTrust failed · &f" + t.getMessage()));
            return true;
        }
    }

    private boolean plan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootavacore.admin") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status", "show" -> {
                List<String> pri = plugin.autonomy().planPriorities();
                String theme = plugin.autonomy().planTheme();
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + "&7work cycle &fgather→chests→deposit→build→farm→vacuum"
                        + (pri.isEmpty()
                                ? ""
                                : " &8· &7stored &f" + String.join("→", pri)
                                        + (theme.isBlank() ? "" : " · " + theme)
                                        + " &8(not used)")));
            }
            case "clear", "reset" -> {
                plugin.autonomy().setPlanPriorities(List.of(), "");
                sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eHour plan cleared."));
            }
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.colorize(plugin.config().prefix()
                            + "&7/ava plan set gather,build,deposit"));
                    return true;
                }
                String raw = String.join(",", Arrays.copyOfRange(args, 1, args.length));
                List<String> pri = new ArrayList<>();
                for (String part : raw.split("[,\\s]+")) {
                    if (part.isBlank()) continue;
                    pri.add(part.toLowerCase(Locale.ROOT).trim());
                }
                plugin.autonomy().setPlanPriorities(pri, plugin.autonomy().planTheme());
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + "&eStored &f" + String.join("→", plugin.autonomy().planPriorities())
                        + " &8· &7work still uses default cycle"));
            }
            case "theme" -> {
                String theme = args.length < 2
                        ? ""
                        : String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.autonomy().setPlanPriorities(plugin.autonomy().planPriorities(), theme);
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + "&aTheme &f" + (theme.isBlank() ? "(cleared)" : theme)));
            }
            default -> sender.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&7/ava plan &fstatus|set|theme|clear"));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("rootavacore.admin")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(plugin.colorize(plugin.config().reloaded()));
        return true;
    }

    private boolean stopWork(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootavacore.use") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (args.length == 0) {
            return showWorkOrders(sender);
        }
        String act = args[0].toLowerCase(Locale.ROOT);
        return switch (act) {
            case "all", "stopall", "cancelall" -> stopAllOrders(sender);
            case "hire", "job" -> stopHireOrder(sender, Arrays.copyOfRange(args, 1, args.length));
            case "fill", "layer" -> stopFillOrder(sender);
            case "quote" -> stopQuoteOrder(sender, Arrays.copyOfRange(args, 1, args.length));
            case "work", "homestead", "autonomy" -> stopHomestead(sender);
            case "play", "presence" -> stopPlayPresence(sender);
            default -> showWorkOrders(sender);
        };
    }

    private boolean showWorkOrders(CommandSender sender) {
        ChatUi.banner(sender, "Ava work orders");
        boolean ops = isOps(sender);
        UUID self = sender instanceof Player p ? p.getUniqueId() : null;
        int shown = 0;
        for (AvaHireService.Job job : plugin.hire().activeJobs()) {
            if (job == null || job.done) continue;
            if (!ops && (self == null || !self.equals(job.hirer))) continue;
            shown++;
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("Hire ", NamedTextColor.GRAY))
                            .append(Component.text(job.hirerName != null ? job.hirerName : "?", NamedTextColor.WHITE))
                            .append(Component.text(" · " + AvaHireService.jobLabel(job.type), NamedTextColor.AQUA))
                            .append(Component.text(plainHireDetail(job), NamedTextColor.DARK_GRAY))
                            .append(Component.text("  "))
                            .append(ChatLinks.danger("[Stop]", "/ava stop hire " + job.id))
                            .build());
        }
        if (plugin.fill() != null && sender instanceof Player fp) {
            AvaFillService.Job fillJob = plugin.fill().jobFor(fp.getUniqueId());
            if (fillJob != null && (ops || self != null && self.equals(fillJob.playerId))) {
                shown++;
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("Fill ", NamedTextColor.GRAY))
                                .append(Component.text(fillJob.mode + " · " + fillJob.placed + " placed · "
                                        + fillJob.remaining + " left · "
                                        + String.format(java.util.Locale.US, "%.2f", fillJob.goldSpent) + " G", NamedTextColor.AQUA))
                                .append(Component.text("  "))
                                .append(ChatLinks.danger("[Stop]", "/ava stop fill"))
                                .build());
            }
        }
        for (var entry : plugin.hire().pendingQuotes().entrySet()) {
            UUID hirer = entry.getKey();
            AvaHireService.PendingContract quote = entry.getValue();
            if (quote == null || System.currentTimeMillis() > quote.expiresAt) continue;
            if (!ops && (self == null || !self.equals(hirer))) continue;
            shown++;
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("Quote ", NamedTextColor.GRAY))
                            .append(Component.text(offlineName(hirer), NamedTextColor.WHITE))
                            .append(Component.text(
                                    " · " + AvaHireService.jobLabel(quote.type)
                                            + " · " + quote.blocks.size() + " blocks",
                                    NamedTextColor.AQUA))
                            .append(Component.text("  "))
                            .append(ChatLinks.danger("[Stop]", "/ava stop quote " + hirer))
                            .build());
        }
        if (ops) {
            shown++;
            boolean paused = plugin.autonomy() != null && plugin.autonomy().paused();
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("Homestead ", NamedTextColor.GRAY))
                            .append(Component.text(
                                    plugin.autonomy() != null ? plugin.autonomy().statusLine() : "offline",
                                    NamedTextColor.WHITE))
                            .append(Component.text("  "))
                            .append(paused
                                    ? Component.text("(paused)", NamedTextColor.DARK_GRAY)
                                    : ChatLinks.danger("[Stop]", "/ava stop work"))
                            .build());
            shown++;
            boolean playOn = plugin.powers() != null && plugin.powers().playEnabled();
            String presence = plugin.presence() != null && plugin.presence().isSpawned()
                    ? plugin.presence().mode().name().toLowerCase(Locale.ROOT)
                    : "despawned";
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("Play ", NamedTextColor.GRAY))
                            .append(Component.text((playOn ? "on" : "off") + " · presence " + presence, NamedTextColor.WHITE))
                            .append(Component.text("  "))
                            .append(ChatLinks.danger("[Stop]", "/ava stop play"))
                            .build());
        }
        if (shown == 0) {
            ChatUi.tip(sender, "No active work orders.");
        } else if (ops || shown > 1) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("  "))
                            .append(ChatLinks.danger("[Stop all]", "/ava stop all"))
                            .build());
        }
        ChatUi.tip(sender, "Hire contracts: stop anytime, no refund. Builds are never dug.");
        return true;
    }

    private boolean stopAllOrders(CommandSender sender) {
        if (isOps(sender)) {
            int n = plugin.hire().cancelAll();
            if (plugin.fill() != null) {
                n += plugin.fill().cancelAll();
            }
            if (plugin.autonomy() != null) {
                plugin.autonomy().setPaused(true);
            }
            if (plugin.powers() != null) {
                plugin.powers().setPlayEnabled(false);
            }
            if (plugin.presence() != null) {
                plugin.presence().despawn();
            }
            ChatUi.entry(sender, "Stop all", n + " hire jobs/quotes · homestead paused · play off", "ok");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7/ava stop all"));
            return true;
        }
        boolean fillStopped = plugin.fill() != null && plugin.fill().cancel(player);
        if (fillStopped) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eFill stopped."));
        }
        String hire = plugin.hire().cancel(player);
        if (fillStopped && (hire == null || "none".equals(hire))) {
            return true;
        }
        return replyStop(sender, hire);
    }

    private boolean stopFillOrder(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cPlayers only."));
            return true;
        }
        if (plugin.fill() != null && plugin.fill().cancel(player)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eFill stopped."));
        } else {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7No fill in progress."));
        }
        return true;
    }

    private boolean stopHireOrder(CommandSender sender, String[] rest) {
        if (rest.length == 0) {
            if (sender instanceof Player player) {
                return replyStop(sender, plugin.hire().cancel(player));
            }
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7/ava stop hire <id|name>"));
            return true;
        }
        String key = rest[0];
        UUID id = parseUuid(key);
        if (id != null) {
            AvaHireService.Job byId = findActiveJob(id);
            if (byId != null) {
                if (!canManageHirer(sender, byId.hirer)) {
                    sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
                    return true;
                }
                return replyStop(sender, plugin.hire().cancelJob(byId.id));
            }
            AvaHireService.PendingContract quote = plugin.hire().pendingContract(id);
            if (quote != null) {
                if (!canManageHirer(sender, id)) {
                    sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
                    return true;
                }
                return replyStop(sender, plugin.hire().cancelQuote(id));
            }
        }
        for (AvaHireService.Job job : plugin.hire().activeJobs()) {
            if (job == null || job.hirerName == null || !job.hirerName.equalsIgnoreCase(key)) continue;
            if (!canManageHirer(sender, job.hirer)) {
                sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
                return true;
            }
            return replyStop(sender, plugin.hire().cancelJob(job.id));
        }
        Player online = Bukkit.getPlayerExact(key);
        if (online == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(key)) {
                    online = p;
                    break;
                }
            }
        }
        if (online != null) {
            if (!canManageHirer(sender, online.getUniqueId())) {
                sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
                return true;
            }
            return replyStop(sender, plugin.hire().cancelHirer(online.getUniqueId()));
        }
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eNo matching work order."));
        return true;
    }

    private boolean stopQuoteOrder(CommandSender sender, String[] rest) {
        UUID target = null;
        if (rest.length == 0) {
            if (sender instanceof Player player) {
                target = player.getUniqueId();
            }
        } else {
            UUID parsed = parseUuid(rest[0]);
            if (parsed != null) {
                target = parsed;
            } else {
                Player online = Bukkit.getPlayerExact(rest[0]);
                if (online == null) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().equalsIgnoreCase(rest[0])) {
                            online = p;
                            break;
                        }
                    }
                }
                if (online != null) target = online.getUniqueId();
            }
        }
        if (target == null) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7/ava stop quote [player]"));
            return true;
        }
        if (!canManageHirer(sender, target)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        return replyStop(sender, plugin.hire().cancelQuote(target));
    }

    private boolean stopHomestead(CommandSender sender) {
        if (!isOps(sender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        plugin.autonomy().setPaused(true);
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eOwn-claim work paused."));
        return true;
    }

    private boolean stopPlayPresence(CommandSender sender) {
        if (!isOps(sender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        plugin.powers().setPlayEnabled(false);
        plugin.presence().despawn();
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eAva play+presence stopped."));
        return true;
    }

    private boolean replyStop(CommandSender sender, String r) {
        if (r == null || "none".equals(r)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eNo active work order."));
        } else if (r.startsWith("cancelled_refund:")) {
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&eStopped · refund &f" + r.substring("cancelled_refund:".length()) + "G"));
        } else if ("cancelled_contract".equals(r)) {
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&eDig stopped · &cno refund &8· mining contract already paid."));
        } else if ("quote_cancelled".equals(r)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eQuote cancelled · nothing charged."));
        } else {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eWork order stopped."));
        }
        return true;
    }

    private boolean canManageHirer(CommandSender sender, UUID hirer) {
        if (hirer == null) return false;
        if (isOps(sender)) return true;
        return sender instanceof Player player && hirer.equals(player.getUniqueId());
    }

    private AvaHireService.Job findActiveJob(UUID id) {
        if (id == null) return null;
        for (AvaHireService.Job job : plugin.hire().activeJobs()) {
            if (job != null && id.equals(job.id)) return job;
        }
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String offlineName(UUID id) {
        if (id == null) return "?";
        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null && !name.isBlank() ? name : id.toString().substring(0, 8);
    }

    private static String plainHireDetail(AvaHireService.Job job) {
        if (job.endsAt > 0) {
            long left = Math.max(0L, job.endsAt - System.currentTimeMillis());
            long mins = (left + 59_999L) / 60_000L;
            return " · " + mins + "m left";
        }
        return " · " + job.broken + " cleared · " + job.blocks.size() + " left · "
                + AvaHireService.etaPhrase(job.blocks.size());
    }

    private boolean startPlay(CommandSender sender) {
        if (!isOps(sender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        plugin.powers().setPlayEnabled(true);
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&aAva play armed."));
        return true;
    }

    private boolean presence(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootavacore.presence") && !sender.hasPermission("rootavacore.admin")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        AvaPresenceService presence = plugin.presence();
        if (args.length == 0) {
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix()
                            + "&7/ava presence <spawn|despawn|here|follow|hang|stop> [player]"));
            return true;
        }
        String act = args[0].toLowerCase(Locale.ROOT);
        switch (act) {
            case "spawn" -> {
                boolean ok = spawnPresencePreferred(presence);
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + (ok ? "&aPresence spawned." : "&cPresence failed (check config / Mannequin).")));
            }
            case "claim" -> {
                Location at = firstOwnedClaimAnchor();
                if (at == null) {
                    sender.sendMessage(plugin.colorize(plugin.config().prefix()
                            + "&cNo Ava_Ivy claim yet — restart with Root-Claims 1.8.107+ or /c grant Ava_Ivy public."));
                    return true;
                }
                boolean ok = presence.parkAndWander(at);
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + (ok
                                ? "&aPresence working claim &f"
                                        + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ()
                                : "&cPresence spawn at claim failed.")));
            }
            case "despawn", "remove" -> {
                presence.despawn();
                sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&ePresence despawned."));
            }
            case "here" -> {
                if (!(sender instanceof Player player)) {
                    // Console / RCON: treat "here" as claim park when possible
                    Location at = firstOwnedClaimAnchor();
                    if (at == null) {
                        sender.sendMessage(plugin.colorize(plugin.config().prefix()
                                + "&cPlayers only for here (or found a claim first)."));
                        return true;
                    }
                    boolean ok = presence.parkAndWander(at);
                    sender.sendMessage(plugin.colorize(plugin.config().prefix()
                            + (ok ? "&aPresence at claim — wandering/working." : "&cFailed.")));
                    return true;
                }
                String land = AvaLandAccess.requireOwnLand(player);
                if (land != null) {
                    sender.sendMessage(plugin.colorize(plugin.config().prefix() + landMessage(land)));
                    return true;
                }
                boolean ok = presence.parkAndWander(player.getLocation());
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + (ok ? "&aPresence here — wandering/working." : "&cFailed.")));
            }
            case "hang" -> {
                boolean ok = presence.hang();
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + (ok ? "&aPresence hanging." : "&cSpawn presence first.")));
            }
            case "follow" -> {
                Player target = null;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().equalsIgnoreCase(args[1])) {
                                target = p;
                                break;
                            }
                        }
                    }
                } else if (sender instanceof Player self) {
                    target = self;
                }
                if (target == null) {
                    sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cNo follow target."));
                    return true;
                }
                boolean ok = presence.follow(target);
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + (ok ? "&aFollowing &f" + target.getName() : "&cFailed.")));
            }
            case "stop" -> {
                presence.stopFollow();
                sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eFollow stopped (wander)."));
            }
            default -> sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&7spawn|despawn|claim|here|follow|hang|stop"));
        }
        return true;
    }

    private boolean gear(CommandSender sender) {
        if (!isOps(sender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        plugin.godLoadout().applyNow();
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&aGod loadout applied to Ava_Ivy (if online)."));
        return true;
    }

    private boolean work(CommandSender sender, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&7own-claim work &f" + plugin.autonomy().statusLine()));
            sender.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&7/ava work pause|resume &8· &7/ava gift &8(hand items)"));
            return true;
        }
        if (!isOps(sender) && !sender.hasPermission("rootavacore.admin")) {
            // players can view status only
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        String act = args[0].toLowerCase(Locale.ROOT);
        if ("pause".equals(act) || "stop".equals(act)) {
            plugin.autonomy().setPaused(true);
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eOwn-claim work paused."));
            return true;
        }
        if ("resume".equals(act) || "start".equals(act)) {
            plugin.autonomy().setPaused(false);
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&aOwn-claim work resumed."));
            return true;
        }
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7/ava work status|pause|resume"));
        return true;
    }

    private boolean gift(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cHold an item and /ava gift in-game."));
            return true;
        }
        if (!player.hasPermission("rootavacore.hire") && !player.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        String land = AvaLandAccess.requireOwnLand(player);
        if (land != null) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + landMessage(land)));
            return true;
        }
        String err = plugin.gifts().giftHeld(player);
        if (err == null) {
            player.sendMessage(plugin.colorize(plugin.config().prefix() + "&aThanks — stashed in my claim chests."));
            return true;
        }
        String msg = switch (err) {
            case "empty_hand" -> "&eHold something to gift me.";
            case "no_claims" -> "&cI don't have a claim mirrored yet.";
            case "too_far" -> "&eCome to my claim (or stand near me) to gift.";
            case "not_in_claim" -> "&cStand in &fyour claim or territory&c to gift Ava.";
            case "not_your_claim" -> "&cOnly claim owners/trusted can gift Ava here.";
            case "claims_offline" -> "&cClaims are offline — gifts are paused.";
            case "chests_full" -> "&eMy chests are full — I'll place more when I can.";
            case "partial" -> "&aTook what I could — leftover stays in your hand.";
            default -> "&eGift refused · &f" + err;
        };
        player.sendMessage(plugin.colorize(plugin.config().prefix() + msg));
        return true;
    }

    private boolean chest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cLook at a chest in-game."));
            return true;
        }
        if (!player.hasPermission("rootavacore.hire") && !player.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (plugin.playerChests() == null) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cChest storage unavailable."));
            return true;
        }
        String err = plugin.playerChests().setChestFromLook(player);
        if (err == null) {
            Location loc = plugin.playerChests().chestLocation(player.getUniqueId());
            sender.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&aHire chest set &8· &f"
                    + (loc == null
                            ? "ok"
                            : loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ())));
            return true;
        }
        sender.sendMessage(plugin.colorize(plugin.config().prefix()
                + "&eLook at a chest (or stand next to one), then &f/ava chest&e."));
        return true;
    }

    private boolean speed(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cPlayers only."));
            return true;
        }
        if (!player.hasPermission("rootavacore.hire") && !player.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (plugin.playerChests() == null) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cSpeed upgrades unavailable."));
            return true;
        }
        int current = plugin.playerChests().getSpeedLevel(player.getUniqueId());
        String result = plugin.playerChests().buySpeed(player);
        if ("no_economy".equals(result)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cEconomy offline."));
            return true;
        }
        if ("broke".equals(result)) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&cNeed &f1000 G&c to buy speed (level &f" + current + "&c)."));
            return true;
        }
        if (result != null && result.startsWith("ok:")) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&aSpeed &f" + result.substring(3)
                    + " &8· &f1000 G&7 · faster hire/farm/mine ticks"));
            return true;
        }
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eSpeed upgrade failed."));
        return true;
    }

    private boolean speak(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootavacore.admin") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7/ava speak <message>"));
            return true;
        }
        String message = String.join(" ", args).trim();
        Bukkit.broadcastMessage(plugin.colorize(plugin.config().prefix() + message));
        return true;
    }

    private boolean hire(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootavacore.hire") && !sender.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0]) || "prices".equalsIgnoreCase(args[0])) {
            return hireHowTo(sender);
        }
        if (!(sender instanceof Player player)) {
            // Console / RCON: /ava hire stopall  or  /ava hire <job> <player>
            if (args.length >= 1) {
                String act = args[0].toLowerCase(Locale.ROOT);
                if ("stopall".equals(act) || "cancelall".equals(act)) {
                    if (!isOps(sender)) {
                        sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
                        return true;
                    }
                    int n = plugin.hire().cancelAll();
                    ChatUi.entry(sender, "Hire", "stopped " + n + " jobs/quotes", "ok");
                    return true;
                }
            }
            if (args.length < 2) {
                sender.sendMessage(plugin.colorize(
                        plugin.config().prefix() + "&7Console: /ava hire <hour|sidekick|mine|teardown|claim|minedown|confirm|cancel|stopall> <player>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().equalsIgnoreCase(args[1])) {
                        target = p;
                        break;
                    }
                }
            }
            if (target == null) {
                sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cPlayer offline."));
                return true;
            }
            String act = args[0].toLowerCase(Locale.ROOT);
            String[] rest = Arrays.copyOfRange(args, 2, args.length);
            return runHireFor(sender, target, act, rest);
        }
        return runHireFor(sender, player, args[0].toLowerCase(Locale.ROOT), Arrays.copyOfRange(args, 1, args.length));
    }

    private boolean runHireFor(CommandSender notify, Player player, String act, String[] args) {
        return switch (act) {
            case "hour", "work", "shift" ->
                    replyHire(notify, player, plugin.hire().hireHour(player), "hour");
            case "sidekick", "follow", "buddy" -> {
                int mins = 10;
                if (args.length >= 1) {
                    try {
                        mins = Integer.parseInt(args[0]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                yield replyHire(notify, player, plugin.hire().hireSidekick(player, mins), "sidekick");
            }
            case "mine", "excavate", "dig" -> {
                int radius = plugin.config().hire().defaultRadius();
                if (args.length >= 1) {
                    try {
                        radius = Integer.parseInt(args[0]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                yield replyHire(notify, player, plugin.hire().hireMine(player, radius), "mine");
            }
            case "teardown", "flatten" -> {
                int radius = plugin.config().hire().defaultRadius();
                if (args.length >= 1) {
                    try {
                        radius = Integer.parseInt(args[0]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                yield replyHire(notify, player, plugin.hire().hireTeardown(player, radius), "teardown");
            }
            case "chunk", "quarry", "claim", "clear", "circle" ->
                    replyHire(notify, player, plugin.hire().hireChunk(player), "claim");
            case "minedown", "shaft", "down" ->
                    replyHire(notify, player, plugin.hire().hireMinedown(player), "minedown");
            case "stopall", "cancelall" -> {
                if (!isOps(notify)) {
                    notify.sendMessage(plugin.colorize(plugin.config().noPermission()));
                    yield true;
                }
                int n = plugin.hire().cancelAll();
                ChatUi.entry(notify, "Hire", "stopped " + n + " jobs/quotes", "ok");
                yield true;
            }
            case "confirm", "yes", "accept", "sign" ->
                    replyHire(notify, player, plugin.hire().confirmContract(player), "contract");
            case "cancel", "stop" -> {
                String r = plugin.hire().cancel(player);
                if ("none".equals(r)) {
                    notify.sendMessage(plugin.colorize(plugin.config().prefix() + "&eNo active hire."));
                } else if (r.startsWith("cancelled_refund:")) {
                    notify.sendMessage(plugin.colorize(
                            plugin.config().prefix() + "&eCancelled · refund &f" + r.substring("cancelled_refund:".length()) + "G"));
                } else if ("cancelled_contract".equals(r)) {
                    notify.sendMessage(plugin.colorize(
                            plugin.config().prefix() + "&eDig stopped · &cno refund &8· mining contract already paid."));
                } else if ("quote_cancelled".equals(r)) {
                    notify.sendMessage(plugin.colorize(plugin.config().prefix() + "&eQuote cancelled · nothing charged."));
                } else {
                    notify.sendMessage(plugin.colorize(plugin.config().prefix() + "&eHire cancelled."));
                }
                yield true;
            }
            case "status" -> {
                AvaHireService.Job job = plugin.hire().jobFor(player.getUniqueId());
                int crews = plugin.presence() != null ? plugin.presence().spawnedCount() : 0;
                AvaHireService.PendingContract quote = plugin.hire().pendingContract(player.getUniqueId());
                if (job == null && quote != null && System.currentTimeMillis() <= quote.expiresAt) {
                    sendQuotePanel(
                            notify,
                            AvaHireService.jobLabel(quote.type),
                            quote.blocks.size(),
                            quote.cost,
                            quote.standY,
                            plugin.hire().walletGold(player),
                            quote.perBlock,
                            quote.overlapSkipped,
                            quote.oresAtNeg59,
                            quote.buildsSkipped);
                } else if (job == null) {
                    notify.sendMessage(plugin.colorize(
                            plugin.config().prefix()
                                    + "&7No active hire. &f/ava hire"
                                    + " &8· &7fleet &f" + crews
                                    + " &8· &7jobs &f" + plugin.hire().activeCount()));
                } else {
                    sendJobStatusPanel(notify, job, crews);
                }
                yield true;
            }
            default -> {
                hireHowTo(notify);
                yield true;
            }
        };
    }

    private boolean replyHire(CommandSender notify, Player player, String code, String label) {
        if (code == null) {
            notify.sendMessage(plugin.colorize(plugin.config().prefix() + "&aHired &f" + player.getName() + " &afor &f" + label + "&a."));
            if (notify != player) {
                player.sendMessage(plugin.colorize(plugin.config().prefix() + "&aYou're hired for &f" + label + "&a."));
            }
            return true;
        }
        if (code.startsWith("hour:")) {
            String[] p = code.split(":");
            String msg = "&aHour hire &8· &f" + p[1] + "m &8· &f" + p[2] + "G &7on your claim";
            notify.sendMessage(plugin.colorize(plugin.config().prefix() + msg));
            if (notify != player) {
                player.sendMessage(plugin.colorize(plugin.config().prefix() + msg));
            }
            return true;
        }
        if (code.startsWith("queued:")) {
            String[] p = code.split(":");
            String msg = "&a" + label + " queued &8· &f" + p[1] + " blocks &8· &f" + p[2] + "G";
            notify.sendMessage(plugin.colorize(plugin.config().prefix() + msg));
            if (notify != player) {
                player.sendMessage(plugin.colorize(plugin.config().prefix() + msg));
            }
            return true;
        }
        if (code.startsWith("quote:")) {
            String[] p = code.split(":");
            int blocks = parseIntSafe(p, 2, 0);
            double cost = parseDoubleSafe(p, 3, 0);
            int standY = parseIntSafe(p, 4, 0);
            double wallet = parseDoubleSafe(p, 5, 0);
            double rate = p.length >= 7 ? parseDoubleSafe(p, 6, 0.01) : 0.01;
            int overlap = p.length >= 8 ? parseIntSafe(p, 7, 0) : 0;
            int oresNeg59 = p.length >= 9 ? parseIntSafe(p, 8, 0) : 0;
            int buildsSkipped = p.length >= 10 ? parseIntSafe(p, 9, 0) : 0;
            sendQuotePanel(notify, p[1], blocks, cost, standY, wallet, rate, overlap, oresNeg59, buildsSkipped);
            if (notify != player) {
                sendQuotePanel(player, p[1], blocks, cost, standY, wallet, rate, overlap, oresNeg59, buildsSkipped);
            }
            return true;
        }
        if (code.startsWith("contract:")) {
            String[] p = code.split(":");
            ChatUi.entry(
                    notify,
                    "Signed",
                    p[1] + " · " + p[2] + " blocks · " + goldFmt(parseDoubleSafe(p, 3, 0)) + " G paid",
                    "ok");
            if (notify != player) {
                ChatUi.entry(
                        player,
                        "Signed",
                        p[1] + " · " + p[2] + " blocks · " + goldFmt(parseDoubleSafe(p, 3, 0)) + " G paid",
                        "ok");
            }
            return true;
        }
        String msg = switch (code) {
            case "hire_disabled" -> "&cHiring is disabled.";
            case "already_hired" -> "&eAlready have an active hire. &f/ava stop";
            case "busy" -> "&eAva is busy — try again shortly.";
            case "not_in_claim" -> "&cStand inside &fyour own claim or territory&c to hire Ava. Not wilderness.";
            case "not_your_claim" -> "&cHire Ava only on &fland you own&c. Trusted / others / wilderness are blocked.";
            case "below_mining_floor" -> "&cStart dig jobs at &fY 0 or above&c, inside your claim or territory. Below Y 0 is free-reign mining (no claims).";
            case "plot_not_fully_in_claim" -> "&cThe entire plot must sit inside &fyour own&c claim or territory. Move so no edge hangs into wilderness or someone else's land.";
            case "stand_near_claim_ring" -> "&cStand within &f8 blocks&c of this claim ring (center or rim, including expansions) to clear it.";
            case "stand_near_expansion_ring" -> "&cStand within &f8 blocks&c of this claim ring (center or rim). Expansion circles are included.";
            case "overlap_only" -> "&eNothing left to clear — this circle only overlaps another player's claim.";
            case "builds_only" -> "&eNothing natural left — player builds in this circle are left standing.";
            case "claims_offline" -> "&cClaims are offline — Ava hires are paused.";
            case "nothing_to_clear" -> "&eNothing matching to clear here.";
            case "no_quote" -> "&eNo mining quote. Click &f[Claim]&e or &f[Minedown]&e first.";
            case "quote_expired" -> "&eQuote expired. Run &f/ava hire claim &7or &fminedown &7again.";
            case "no_economy" -> "&cEconomy offline — can't charge Gold.";
            case "no_chest" -> "&eSet a drop chest first: &f/ava chest";
            case "withdraw_failed" -> "&cCouldn't withdraw Gold.";
            default -> {
                if (code.startsWith("broke:")) {
                    String[] p = code.split(":");
                    yield "&cNeed &f" + p[2] + "G&c (balance &f" + p[1] + "G&c).";
                }
                if (code.startsWith("too_large:")) {
                    String[] p = code.split(":");
                    yield "&cToo many blocks (&f" + p[1] + "&c / max &f" + p[2] + "&c). Shrink radius.";
                }
                yield "&eHire refused · &f" + code;
            }
        };
        notify.sendMessage(plugin.colorize(plugin.config().prefix() + msg));
        return true;
    }

    private boolean judgment(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootavacore.judgment") && !sender.hasPermission("rootavacore.admin")
                && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7/ava judgment <player> [reason]"));
            return true;
        }
        String reason = args.length >= 2
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "operator";
        String err = plugin.powers().judgmentKillByName(args[0], reason);
        if (err == null) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cJudgment · &f" + args[0]));
        } else {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eJudgment refused · &f" + err));
        }
        return true;
    }

    /**
     * Claim schematic capture / dump / rebuild for Ava core vision + build-sim.
     * RCON lines: SCHEM n · SUMMARY world x y z r dims nonAir=… file=… top=…
     */
    private boolean schem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootavacore.admin") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        if (plugin.schematics() == null) {
            sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&cSchematics unavailable."));
            return true;
        }
        String sub = args.length == 0 ? "dump" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "capture", "save", "snap" -> {
                for (String line : plugin.schematics().captureAll()) {
                    sender.sendMessage(line);
                }
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + "&7schem &f" + plugin.schematics().lastNote()));
            }
            case "dump", "list", "status" -> {
                for (String line : plugin.schematics().dumpSummaries()) {
                    sender.sendMessage(line);
                }
                sender.sendMessage(plugin.colorize(plugin.config().prefix()
                        + "&7schem &f" + plugin.schematics().lastNote()
                        + (plugin.schematics().isBuilding() ? " &8· &abuilding" : "")));
            }
            case "rebuild", "build", "place" -> {
                String msg = plugin.schematics().startRebuild();
                sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7" + msg));
            }
            case "stop" -> {
                plugin.schematics().stopBuild();
                sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&eSchem rebuild stopped."));
            }
            default -> sender.sendMessage(plugin.colorize(plugin.config().prefix()
                    + "&7/ava schem &fcapture|dump|rebuild|stop"));
        }
        return true;
    }

    /**
     * Dump Ava_Ivy owned claims for RCON → ava-minecraft.json mirror.
     * Soft-depends Root-Claims via reflection (no hard compile dep).
     */
    private boolean claimsDump(CommandSender sender) {
        if (!sender.hasPermission("rootavacore.admin") && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        UUID avaUuid = plugin.config().powers().avaUuid();
        int buffer = plugin.config().powers().territoryBufferBlocks();
        sender.sendMessage("BUFFER " + buffer);
        org.bukkit.plugin.Plugin claimsPlug = Bukkit.getPluginManager().getPlugin("Root-Claims");
        if (claimsPlug == null || !claimsPlug.isEnabled()) {
            sender.sendMessage("CLAIMS none (Root-Claims missing)");
            return true;
        }
        try {
            Object service = claimsPlug.getClass().getMethod("claims").invoke(claimsPlug);
            @SuppressWarnings("unchecked")
            java.util.List<?> owned = (java.util.List<?>) service.getClass()
                    .getMethod("ownedBy", UUID.class)
                    .invoke(service, avaUuid);
            if (owned == null || owned.isEmpty()) {
                sender.sendMessage("CLAIMS 0");
                return true;
            }
            sender.sendMessage("CLAIMS " + owned.size());
            for (Object rec : owned) {
                Object key = rec.getClass().getMethod("key").invoke(rec);
                String world = String.valueOf(key.getClass().getMethod("world").invoke(key));
                int x = ((Number) key.getClass().getMethod("x").invoke(key)).intValue();
                int y = ((Number) key.getClass().getMethod("y").invoke(key)).intValue();
                int z = ((Number) key.getClass().getMethod("z").invoke(key)).intValue();
                int radius = ((Number) rec.getClass().getMethod("radiusBlocks").invoke(rec)).intValue();
                Object dn = null;
                try {
                    dn = rec.getClass().getMethod("displayName").invoke(rec);
                } catch (Throwable ignored) {
                }
                String name = dn == null ? "" : String.valueOf(dn);
                sender.sendMessage(
                        "CLAIM " + world + " " + x + " " + y + " " + z + " " + radius
                                + (name.isBlank() ? "" : " " + name));
            }
        } catch (Throwable t) {
            sender.sendMessage("CLAIMS error " + t.getClass().getSimpleName());
            plugin.getLogger().warning("ava claims dump: " + t.getMessage());
        }
        return true;
    }

    /** Prefer Ava claim park; fall back to configured spawn/world-spawn. */
    private boolean spawnPresencePreferred(AvaPresenceService presence) {
        Location claim = firstOwnedClaimAnchor();
        if (claim != null) {
            return presence.spawnHere(claim);
        }
        presence.startIfEnabled();
        return presence.isSpawned();
    }

    private Location firstOwnedClaimAnchor() {
        try {
            org.bukkit.plugin.Plugin claimsPlug = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claimsPlug == null || !claimsPlug.isEnabled()) return null;
            Object service = claimsPlug.getClass().getMethod("claims").invoke(claimsPlug);
            UUID avaUuid = plugin.config().powers().avaUuid();
            @SuppressWarnings("unchecked")
            java.util.List<?> owned = (java.util.List<?>) service.getClass()
                    .getMethod("ownedBy", UUID.class)
                    .invoke(service, avaUuid);
            if (owned == null || owned.isEmpty()) return null;
            Object rec = owned.get(0);
            Object key = rec.getClass().getMethod("key").invoke(rec);
            String world = String.valueOf(key.getClass().getMethod("world").invoke(key));
            int x = ((Number) key.getClass().getMethod("x").invoke(key)).intValue();
            int y = ((Number) key.getClass().getMethod("y").invoke(key)).intValue();
            int z = ((Number) key.getClass().getMethod("z").invoke(key)).intValue();
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x + 0.5, y, z + 0.5);
        } catch (Throwable t) {
            plugin.getLogger().fine("presence claim anchor: " + t.getMessage());
            return null;
        }
    }

    private static String hireJobDetail(AvaHireService.Job job) {
        if (job.endsAt > 0) {
            long left = Math.max(0L, job.endsAt - System.currentTimeMillis());
            long mins = (left + 59_999L) / 60_000L;
            String extra = job.type == AvaHireService.JobType.HOUR && job.crew != null
                    ? " &8· &7" + job.crew.lastNote()
                    : "";
            return " &8· &f" + mins + "m left" + extra;
        }
        return " &8· &f"
                + job.broken
                + " cleared &8· &f"
                + job.blocks.size()
                + " left &8· &e"
                + AvaHireService.etaPhrase(job.blocks.size());
    }

    private static String landMessage(String code) {
        return switch (code) {
            case "not_in_claim" -> "&cStand in &fyour claim or territory&c to use Ava here.";
            case "not_your_claim" -> "&cOnly claim owners/trusted can use Ava here.";
            case "claims_offline" -> "&cClaims are offline — Ava land actions are paused.";
            default -> "&eAva land check failed · &f" + code;
        };
    }

    private static boolean isOps(CommandSender sender) {
        return sender.hasPermission("rootavacore.admin")
                || sender instanceof ConsoleCommandSender
                || sender.isOp();
    }

    private static boolean canSeeStaffHelp(CommandSender sender) {
        return isOps(sender)
                || sender.hasPermission("rootavacore.presence")
                || sender.hasPermission("rootavacore.judgment");
    }

    private static void sendQuotePanel(
            CommandSender sender,
            String job,
            int blocks,
            double cost,
            int standY,
            double wallet,
            double perBlock,
            int overlapSkipped,
            int oresAtNeg59,
            int buildsSkipped) {
        boolean claim = "claim".equalsIgnoreCase(job);
        ChatUi.banner(sender, "Quote");
        ChatUi.alert(
                sender,
                claim
                        ? "FULL CLAIM CIRCLE · NATURAL ONLY · NO REFUNDS"
                        : "NATURAL ONLY · NO REFUNDS IF STOPPED");
        ChatUi.entry(sender, "Job", job + " · Y " + standY + " → min · stand Y included");
        ChatUi.entry(sender, "Blocks", String.valueOf(Math.max(0, blocks)));
        ChatUi.entry(sender, "Ores @ -59", Math.max(0, oresAtNeg59) + " will drop");
        ChatUi.entry(sender, "ETA", AvaHireService.etaPhrase(Math.max(0, blocks)));
        ChatUi.entry(sender, "Price", goldFmt(cost) + " G · " + goldFmt(perBlock) + " G/block");
        ChatUi.entry(sender, "Wallet", goldFmt(wallet) + " G");
        if (buildsSkipped > 0) {
            ChatUi.entry(sender, "Builds", buildsSkipped + " left standing", "ok");
        } else {
            ChatUi.entry(sender, "Builds", "player-placed blocks are left standing", "ok");
        }
        if (overlapSkipped > 0) {
            ChatUi.entry(
                    sender,
                    "Overlap",
                    overlapSkipped + " columns in other players' claims skipped",
                    "alert");
        }
        ChatUi.entry(sender, "Under Y " + standY, "natural blocks WILL BE REMOVED", "alert");
        if (claim) {
            ChatUi.entry(
                    sender,
                    "Circle",
                    "Full ring including your expansions · stop via /ava stop · no refunds",
                    "alert");
        } else {
            ChatUi.entry(sender, "Stop", "/ava stop anytime · no refunds", "alert");
        }
        ChatUi.tip(sender, "Layer by layer, top down. Natural only. Rate +0.01 G/block per 100k.");
        sendQuoteButtons(sender);
    }

    private static void sendJobStatusPanel(CommandSender sender, AvaHireService.Job job, int crews) {
        ChatUi.banner(sender, "Hire");
        ChatUi.entry(sender, "Job", AvaHireService.jobLabel(job.type));
        if (job.endsAt > 0) {
            long left = Math.max(0L, job.endsAt - System.currentTimeMillis());
            long mins = (left + 59_999L) / 60_000L;
            String note = job.type == AvaHireService.JobType.HOUR && job.crew != null
                    ? job.crew.lastNote()
                    : "";
            ChatUi.entry(sender, "Left", mins + "m" + (note.isBlank() ? "" : " · " + note));
            ChatUi.entry(sender, "Paid", goldFmt(job.charged) + " G");
            ChatUi.tip(sender, "fleet " + crews);
            return;
        }
        ChatUi.entry(sender, "Progress", job.broken + " cleared · " + job.blocks.size() + " left");
        ChatUi.entry(sender, "ETA", AvaHireService.etaPhrase(job.blocks.size()));
        ChatUi.entry(sender, "Paid", goldFmt(job.charged) + " G");
        if (job.standY != Integer.MIN_VALUE) {
            ChatUi.entry(sender, "From Y", job.standY + " → min · stand Y included");
        }
        if (job.type == AvaHireService.JobType.CHUNK || job.type == AvaHireService.JobType.MINEDOWN) {
            ChatUi.entry(sender, "Ores @ -59", job.oresAtNeg59 + " in this dig");
            ChatUi.entry(sender, "Refunds", "none if stopped", "alert");
            ChatUi.entry(sender, "Builds", "left standing", "ok");
        }
        ChatUi.tip(sender, "Layer by layer, top down. /ava stop to halt. fleet " + crews);
    }

    private static int parseIntSafe(String[] parts, int index, int fallback) {
        if (parts == null || index < 0 || index >= parts.length) {
            return fallback;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double parseDoubleSafe(String[] parts, int index, double fallback) {
        if (parts == null || index < 0 || index >= parts.length) {
            return fallback;
        }
        try {
            return Double.parseDouble(parts[index].trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static void sendQuoteButtons(CommandSender sender) {
        sender.sendMessage(
                Component.text()
                        .append(ChatLinks.action("[Confirm]", "/ava hire confirm"))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(ChatLinks.danger("[Stop]", "/ava stop"))
                        .build());
    }

    private static String goldFmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.001) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private static String[] prepend(String first, String[] rest) {
        String[] out = new String[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(
                    List.of(
                            "help",
                            "world",
                            "fill",
                            "layer",
                            "hire",
                            "chest",
                            "speed",
                            "speak",
                            "hour",
                            "sidekick",
                            "mine",
                            "teardown",
                            "chunk",
                            "claim",
                            "clear",
                            "minedown",
                            "confirm",
                            "cancel",
                            "work",
                            "gift",
                            "eco",
                            "reload",
                            "presence",
                            "gear",
                            "judgment",
                            "claims",
                            "schem",
                            "plan",
                            "trust",
                            "stop",
                            "play"));
            String partial = args[0].toLowerCase(Locale.ROOT);
            return opts.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("plan")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("status", "set", "theme", "clear").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("schem")
                || args[0].equalsIgnoreCase("schematic")
                || args[0].equalsIgnoreCase("blueprint"))) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("capture", "dump", "rebuild", "stop").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("work")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("status", "pause", "resume").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            List<String> opts = new ArrayList<>(List.of("hire", "quote", "fill", "layer", "all"));
            if (isOps(sender)) {
                opts.add("work");
                opts.add("play");
            }
            return opts.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("fill") || args[0].equalsIgnoreCase("layer"))) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("here", "area", "square", "circle", "2", "5", "10", "16").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("fill") || args[0].equalsIgnoreCase("layer"))
                && args[1].equalsIgnoreCase("circle")) {
            return List.of("1", "3", "5", "8", "10", "16");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hire")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("hour", "work", "shift", "sidekick", "mine", "teardown", "claim", "chunk", "minedown", "confirm", "cancel", "stopall", "status", "help").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("presence")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("spawn", "despawn", "claim", "here", "follow", "hang", "stop").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("presence") && args[1].equalsIgnoreCase("follow")) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("judgment") || args[0].equalsIgnoreCase("kill"))) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static String formatTps() {
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length > 0) {
                return String.format(Locale.US, "%.1f", Math.min(20.0, tps[0]));
            }
        } catch (Throwable ignored) {
        }
        return "n/a";
    }
}
