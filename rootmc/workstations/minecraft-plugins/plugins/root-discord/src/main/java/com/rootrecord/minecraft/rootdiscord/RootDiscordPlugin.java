package com.rootrecord.minecraft.rootdiscord;

import com.rootrecord.minecraft.common.RootDiscordApi;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RootDiscordPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Metrics metrics;
    private RootRecordYamlConfig yaml;
    private DiscordChatBridge bridge;
    private RootDiscordApiImpl api;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_DISCORD_CONFIG, "root-discord.yml");
        yaml.load();
        api = new RootDiscordApiImpl(this);
        getServer().getServicesManager().register(RootDiscordApi.class, api, this, ServicePriority.Normal);
        startBridge();
        var cmd = getCommand("rootdiscord");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("Root-Discord enabled — ready=" + (bridge != null && bridge.isReady()));
    }

    @Override
    public void onDisable() {
        if (bridge != null) {
            bridge.stop();
            bridge = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        RootBStats.shutdown(metrics);
    }

    public DiscordChatBridge bridge() {
        return bridge;
    }

    public RootDiscordApi discordApi() {
        return api;
    }

    public void reloadDiscord() {
        if (yaml != null) {
            yaml.load();
        }
        startBridge();
        getLogger().info("Root-Discord reloaded — ready=" + (bridge != null && bridge.isReady()));
    }

    private void startBridge() {
        if (bridge != null) {
            bridge.stop();
            bridge = null;
        }
        DiscordChatConfig config = DiscordChatConfig.from(this, yaml != null ? yaml.config() : null);
        bridge = new DiscordChatBridge(this, config);
        bridge.start();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootdiscord.admin")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "status" -> {
                sendStatus(sender);
                yield true;
            }
            case "reload" -> {
                reloadDiscord();
                sender.sendMessage(color("&aRoot-Discord reloaded."));
                yield true;
            }
            default -> {
                sender.sendMessage(color("&eUsage: /rootdiscord <status|reload>"));
                yield true;
            }
        };
    }

    private void sendStatus(CommandSender sender) {
        DiscordChatConfig cfg = bridge != null ? bridge.config() : null;
        sender.sendMessage(color("&6--- Root-Discord status ---"));
        sender.sendMessage(color("&7Ready: &f" + (bridge != null && bridge.isReady())));
        if (cfg == null) {
            sender.sendMessage(color("&7Config: &cunavailable"));
            return;
        }
        sender.sendMessage(color("&7Chat enabled: &f" + cfg.chatEnabled()));
        sender.sendMessage(color("&7Bot token: &f" + (cfg.botToken().isBlank() ? "blank" : "present")));
        sender.sendMessage(color("&7Guild: &f" + (cfg.guildId().isBlank() ? "blank" : "set")));
        sender.sendMessage(color(
                "&7Ingame-chat channel: &f" + (cfg.channelId().isBlank() ? "blank" : "set")));
        sender.sendMessage(color(
                "&7Server-logs channel: &f" + (cfg.serverLogsChannelId().isBlank() ? "blank" : "set")));
        sender.sendMessage(color("&7Server tag: &f" + cfg.serverTag()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rootdiscord.admin") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : List.of("status", "reload")) {
            if (o.startsWith(prefix)) {
                out.add(o);
            }
        }
        return out;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
