package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.service.PlayerStateService;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TeleportCommands {

    private TeleportCommands() {}

    public static final class Tpa implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Tpa(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!Permissions.has(player, "tpa")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { ChatUi.tip(player, "/tpa <player>"); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            plugin.playerState().offerTpa(target.getUniqueId(), new PlayerStateService.TpaRequest(player.getUniqueId(), target.getUniqueId(), false, System.currentTimeMillis()));
            if (plugin.playerState().isTpAuto(target.getUniqueId())) {
                return new TpAccept(plugin).onCommand(target, command, label, args);
            }
            target.sendMessage(tpaPrompt(
                    player.getName() + " wants to teleport to you.",
                    "Accept teleport request",
                    "Deny teleport request"));
            ChatUi.entry(player, "Tpa", "sent → " + target.getName(), "open");
            return true;
        }
    }

    public static final class TpaHere implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TpaHere(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!Permissions.has(player, "tpahere")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { ChatUi.tip(player, "/tpahere <player>"); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            plugin.playerState().offerTpa(target.getUniqueId(), new PlayerStateService.TpaRequest(player.getUniqueId(), target.getUniqueId(), true, System.currentTimeMillis()));
            if (plugin.playerState().isTpAuto(target.getUniqueId())) {
                return new TpAccept(plugin).onCommand(target, command, label, args);
            }
            target.sendMessage(tpaPrompt(
                    player.getName() + " wants you to teleport to them.",
                    "Accept teleport request",
                    "Deny teleport request"));
            ChatUi.entry(player, "Tpa", "here → " + target.getName(), "open");
            return true;
        }
    }

    public static final class TpAccept implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TpAccept(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!Permissions.has(player, "tpaccept")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            var req = plugin.playerState().pendingTpa(player.getUniqueId());
            if (req == null) { ChatUi.entry(player, "Tpa", "none pending · wait for /tpa", "alert"); return true; }
            Player requester = Bukkit.getPlayer(req.requester());
            if (requester == null) {
                plugin.playerState().clearTpa(player.getUniqueId());
                player.sendMessage(plugin.msg("player-not-found").replace("{player}", "requester"));
                return true;
            }
            plugin.playerState().clearTpa(player.getUniqueId());
            if (req.here()) {
                plugin.teleportPlayer(player, () -> requester.getLocation(), () -> {
                    ChatUi.entry(player, "Tpa", "accepted", "ok");
                    ChatUi.entry(requester, "Tpa", "accepted", "ok");
                });
            } else {
                plugin.teleportPlayer(requester, () -> player.getLocation(), () -> {
                    ChatUi.entry(player, "Tpa", "accepted", "ok");
                    ChatUi.entry(requester, "Tpa", "accepted", "ok");
                });
            }
            return true;
        }
    }

    public static final class TpDeny implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TpDeny(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!Permissions.has(player, "tpdeny")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (plugin.playerState().pendingTpa(player.getUniqueId()) == null) {
                ChatUi.entry(player, "Tpa", "none pending · wait for /tpa", "alert");
                return true;
            }
            plugin.playerState().clearTpa(player.getUniqueId());
            ChatUi.entry(player, "Tpa", "denied", "ok");
            return true;
        }
    }

    public static final class TpAuto implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TpAuto(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!Permissions.has(player, "tpauto")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            boolean on = plugin.playerState().toggleTpAuto(player.getUniqueId());
            ChatUi.entry(player, "Tpa", on ? "auto-accept on" : "auto-accept off", on ? "ok" : "open");
            return true;
        }
    }

    private static Component tpaPrompt(String lead, String acceptHover, String denyHover) {
        Component accept = Component.text("[Accept]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .hoverEvent(HoverEvent.showText(Component.text(acceptHover, NamedTextColor.GRAY)));
        Component deny = Component.text("[Deny]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpdeny"))
                .hoverEvent(HoverEvent.showText(Component.text(denyHover, NamedTextColor.GRAY)));
        return Component.text(lead + " ", NamedTextColor.GRAY)
                .append(accept)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(deny);
    }
}
