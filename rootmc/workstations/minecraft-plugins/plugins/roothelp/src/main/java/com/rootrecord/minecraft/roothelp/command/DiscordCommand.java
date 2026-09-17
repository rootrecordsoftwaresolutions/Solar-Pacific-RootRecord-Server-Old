package com.rootrecord.minecraft.roothelp.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.FancyHeadlines;
import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import com.rootrecord.minecraft.roothelp.cloud.HelpCloudClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class DiscordCommand implements CommandExecutor {

    private final RootHelpPlugin plugin;

    public DiscordCommand(RootHelpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roothelp.discord")) {
            sender.sendMessage(plugin.colorize("&cYou don't have permission."));
            return true;
        }

        FancyHeadlines.sendBanner(sender, "Discord");
        var cfg = plugin.helpConfig();
        sender.sendMessage(plugin.msg("discord-header"));
        sender.sendMessage(plugin.colorize(plugin.rawMsg("discord-invite")));
        sender.sendMessage(ChatLinks.labelDashUrl("[" + cfg.discordGuildName() + "]", cfg.discordInviteUrl()));

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&7Sign in as a player to check your link status."));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            HelpCloudClient.LinkProfile profile = null;
            Exception cloudError = null;
            try {
                if (plugin.cloud().hasCredentials()) {
                    profile = plugin.cloud().fetchLinkProfile(player.getUniqueId().toString());
                }
            } catch (Exception ex) {
                cloudError = ex;
            }
            if (profile == null || !profile.minecraftLinked()) {
                HelpCloudClient.LinkProfile local = readLocalLink(player.getUniqueId(), cfg.verifyUrl());
                if (local != null && local.minecraftLinked()) {
                    profile = local;
                    cloudError = null;
                }
            }
            HelpCloudClient.LinkProfile finalProfile = profile;
            Exception finalErr = cloudError;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (finalProfile != null) {
                    sendProfile(player, finalProfile, cfg.verifyUrl());
                } else if (finalErr != null) {
                    sender.sendMessage(plugin.colorize(
                            plugin.rawMsg("discord-fetch-fail").replace("{error}", finalErr.getMessage())));
                } else if (!plugin.cloud().hasCredentials()) {
                    sender.sendMessage(plugin.msg("discord-no-cloud"));
                } else {
                    sendProfile(
                            player,
                            new HelpCloudClient.LinkProfile(
                                    false, false, null, null, null, null, null, cfg.verifyUrl()),
                            cfg.verifyUrl());
                }
            });
        });
        return true;
    }

    /** Local-first: Official-synced rootstat_players via RootMC MySQL (no Cloudflare). */
    private HelpCloudClient.LinkProfile readLocalLink(UUID uuid, String verifyUrl) {
        try {
            Plugin rootMc = Bukkit.getPluginManager().getPlugin("RootMC");
            if (rootMc == null || !rootMc.isEnabled()) {
                return null;
            }
            Method playersMethod = rootMc.getClass().getMethod("players");
            Object store = playersMethod.invoke(rootMc);
            if (store == null) {
                return null;
            }
            Method find = store.getClass().getMethod("findByUuid", UUID.class);
            Object opt = find.invoke(store, uuid);
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) {
                return null;
            }
            Object row = optional.get();
            Method verified = row.getClass().getMethod("verified");
            if (!(Boolean) verified.invoke(row)) {
                return null;
            }
            String account = null;
            try {
                Method email = row.getClass().getMethod("email");
                Object e = email.invoke(row);
                if (e != null && !e.toString().isBlank()) {
                    account = e.toString();
                }
            } catch (ReflectiveOperationException ignored) {
            }
            if (account == null) {
                try {
                    Method accountId = row.getClass().getMethod("accountId");
                    Object a = accountId.invoke(row);
                    if (a != null && !a.toString().isBlank()) {
                        account = a.toString();
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            // Discord username not in local cache — MC linked only until cloud refresh.
            return new HelpCloudClient.LinkProfile(
                    true, false, account != null ? account : "RootRecord", null, null, null, null, verifyUrl);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private void sendProfile(Player player, HelpCloudClient.LinkProfile profile, String verifyUrl) {
        String verify = profile.verifyUrl() != null && !profile.verifyUrl().isBlank()
                ? profile.verifyUrl()
                : verifyUrl;

        if (!profile.minecraftLinked()) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("discord-mc-unlinked")));
            player.sendMessage(ChatLinks.action("[Link Minecraft]", "/link"));
            return;
        }

        player.sendMessage(plugin.colorize(
                plugin.rawMsg("discord-mc-linked")
                        .replace("{account}", profile.accountLabel() != null ? profile.accountLabel() : "RootRecord")));

        if (!profile.discordLinked()) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("discord-discord-unlinked")));
            player.sendMessage(ChatLinks.actionUrl("[Open Verify Page]", verify));
        } else {
            String user = profile.discordUsername() != null ? profile.discordUsername() : "Discord";
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("discord-discord-linked").replace("{user}", user)));
            player.sendMessage(plugin.colorize(plugin.rawMsg("discord-both-linked")));

            if (profile.discordProfileUrl() != null && !profile.discordProfileUrl().isBlank()) {
                player.sendMessage(Component.text()
                        .append(Component.text("Discord profile: ", NamedTextColor.GRAY))
                        .append(ChatLinks.url(user, profile.discordProfileUrl()))
                        .build());
            }
        }

        if (profile.statsUrl() != null && !profile.statsUrl().isBlank()) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("discord-stats")));
            player.sendMessage(ChatLinks.url(profile.statsUrl()));
        }
    }
}
