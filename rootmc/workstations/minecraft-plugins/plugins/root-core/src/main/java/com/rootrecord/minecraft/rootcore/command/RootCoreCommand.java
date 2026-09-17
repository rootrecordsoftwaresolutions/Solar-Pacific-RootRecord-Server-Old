package com.rootrecord.minecraft.rootcore.command;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import com.rootrecord.minecraft.rootcore.comms.CommsConfig;
import com.rootrecord.minecraft.rootcore.comms.DiscordChatBridge;
import com.rootrecord.minecraft.rootcore.config.SiteUrls;
import com.rootrecord.minecraft.rootcore.license.LicenseConnectService;
import com.rootrecord.minecraft.rootcore.license.LicenseGate;
import com.rootrecord.minecraft.rootcore.license.LicenseStatus;
import com.rootrecord.minecraft.rootcore.suite.NetworkConnectProbe;
import com.rootrecord.minecraft.rootcore.update.CorePluginUpdater;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class RootCoreCommand implements CommandExecutor, TabCompleter {

    private final RootCorePlugin plugin;

    public RootCoreCommand(RootCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootcore.admin")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "status" -> {
                sendStatus(sender);
                yield true;
            }
            case "connect" -> {
                sendConnect(sender);
                yield true;
            }
            case "reload" -> {
                plugin.reloadCore();
                sender.sendMessage(color("&aRoot-Core reloaded — connection files re-checked."));
                yield true;
            }
            case "license" -> {
                sendLicense(sender);
                yield true;
            }
            case "update" -> {
                CorePluginUpdater updater = plugin.updater();
                if (updater == null) {
                    sender.sendMessage(color("&cUpdater not initialized."));
                    yield true;
                }
                if (!updater.enabled()) {
                    sender.sendMessage(color(
                            "&eUpdater disabled — set &fupdater.enabled: true &ein root-core.yml"));
                    yield true;
                }
                sender.sendMessage(color("&7Checking plugin manifest…"));
                updater.checkNowAsync(() -> sender.sendMessage(color(
                        "&aUpdater: &f" + updater.lastResult()
                                + (updater.lastError().isBlank() ? "" : " &c" + updater.lastError()))));
                yield true;
            }
            case "comms", "discord" -> handleComms(sender, args);
            default -> {
                sender.sendMessage(color(
                        "&eUsage: /rootcore <status|connect|reload|license|update|comms>"));
                yield true;
            }
        };
    }

    private boolean handleComms(CommandSender sender, String[] args) {
        String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "reload" -> {
                plugin.reloadComms();
                sender.sendMessage(color("&aRoot-Core comms reloaded."));
                yield true;
            }
            case "status" -> {
                sendCommsStatus(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(color("&eUsage: /rootcore comms <status|reload>"));
                yield true;
            }
        };
    }

    private void sendCommsStatus(CommandSender sender) {
        DiscordChatBridge bridge = plugin.commsBridge();
        CommsConfig cfg = bridge != null ? bridge.config() : null;
        sender.sendMessage(color("&6--- Root-Core comms ---"));
        sender.sendMessage(color("&7Ready: &f" + (bridge != null && bridge.isReady())));
        if (Bukkit.getPluginManager().getPlugin("Root-Discord") != null) {
            sender.sendMessage(color(
                    "&cLegacy Root-Discord jar still present — remove it to enable Core comms."));
        }
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
        sender.sendMessage(color(
                "&7Slack server-logs: &f"
                        + (cfg.slackServerLogsWebhook().isBlank() ? "blank" : "webhook set")
                        + " &7enabled=&f" + cfg.slackServerLogsEnabled()));
        sender.sendMessage(color("&7Server tag: &f" + cfg.serverTag()));
    }

    private void sendConnect(CommandSender sender) {
        sender.sendMessage(color("&6--- Root-Core connect ---"));
        NetworkConnectProbe.SyncResult sync = NetworkConnectProbe.evaluateSync(plugin);
        for (NetworkConnectProbe.Line line : sync.lines()) {
            sender.sendMessage(color(formatProbeLine(line)));
        }

        RootCoreApi api = plugin.api();
        LicenseConnectService connect = plugin.licenseConnect();
        String apiBase = NetworkConnectProbe.resolveApiBase(plugin, api, connect);
        sender.sendMessage(color("&7Probing API &f" + apiBase + "&7…"));

        final boolean syncOk = sync.connected();
        final String syncHint = sync.firstFailHint();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String reachErr = NetworkConnectProbe.probeApiReachable(apiBase);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (reachErr == null) {
                    sender.sendMessage(color("&aOK &7API reachable: &f" + apiBase));
                } else {
                    sender.sendMessage(color("&cFAIL &7API unreachable: &f" + reachErr));
                }
                boolean connected = syncOk && reachErr == null;
                if (connected) {
                    sender.sendMessage(color("&aNETWORK CONNECTED"));
                } else {
                    String hint = !syncOk
                            ? syncHint
                            : "Outbound HTTPS to " + apiBase + " failed — check firewall/DNS";
                    sender.sendMessage(color("&cNETWORK INCOMPLETE &8— &f" + hint));
                    sender.sendMessage(color("&7Guide: &fhttps://rootmc.net/wiki/plugins/network-setup/"));
                }
            });
        });
    }

    private void sendStatus(CommandSender sender) {
        NetworkConnectProbe.SyncResult sync = NetworkConnectProbe.evaluateSync(plugin);
        if (sync.connected()) {
            sender.sendMessage(color("&aNETWORK CONNECTED &8(/rootcore connect for checklist)"));
        } else {
            sender.sendMessage(color(
                    "&cNETWORK INCOMPLETE &8— &f" + sync.firstFailHint()
                            + " &8(/rootcore connect)"));
        }

        RootCoreApi api = plugin.api();
        File folder = RootRecordFolders.dir(plugin);
        RootMcDatabaseConfig.DatabaseSettings db = api.databaseSettings();
        LicenseGate gate = plugin.licenseGate();

        sender.sendMessage(color("&6--- Root-Core status ---"));
        sender.sendMessage(color("&7Folder: &f" + folder.getAbsolutePath()));
        sender.sendMessage(color("&7Ready: &f" + api.isReady()));
        sender.sendMessage(color(
                "&7core-connection-version: &f" + RootMcCoreConnection.readConnectionVersion(plugin)
                        + " &8(contract " + RootMcCoreConnection.CORE_CONNECTION_VERSION + ")"));
        if (db != null) {
            sender.sendMessage(color(
                    "&7Database: &f" + db.host() + ":" + db.port() + "/" + db.database()
                            + " &7user=&f" + db.username()
                            + " &7password=&f" + maskSecret(db.password())));
        } else {
            sender.sendMessage(color("&7Database: &cunavailable"));
        }
        sender.sendMessage(color("&7Cloud api-base: &f" + api.apiBase()));
        sender.sendMessage(color(
                "&7Cloud server-id: &f" + (api.serverId().isBlank() ? "(missing)" : api.serverId())));
        sender.sendMessage(color("&7Cloud credentials: &f" + (api.hasCloudCredentials() ? "present" : "incomplete")));
        var discord = api.discordSettings();
        sender.sendMessage(color(
                "&7Discord bot: &f" + (discord.hasBotToken() ? "present" : "blank")
                        + " &7guild: &f" + (discord.hasGuild() ? "set" : "blank")
                        + " &7ingame-chat: &f" + (discord.hasIngameChatChannel() ? "set" : "blank")));
        sender.sendMessage(color("&7License mode: &f" + gate.mode().name().toLowerCase(Locale.ROOT)));
        LicenseConnectService connect = plugin.licenseConnect();
        if (connect != null) {
            sender.sendMessage(color(
                    "&7Product-key: &f" + (connect.productKeyPresent() ? "present" : "blank")));
            sender.sendMessage(color(
                    "&7License bind server-id: &f"
                            + (connect.boundServerId().isBlank() ? "(none)" : connect.boundServerId())));
            sender.sendMessage(color("&7License API: &f" + connect.licenseApiBase()));
            String ok = connect.lastPresenceOk();
            String err = connect.lastPresenceError();
            if (err != null && !err.isBlank()) {
                sender.sendMessage(color("&7Last presence: &cfail &8— " + err));
            } else if (ok != null && !ok.isBlank()) {
                sender.sendMessage(color("&7Last presence: &aok &8@ " + ok));
            } else {
                sender.sendMessage(color("&7Last presence: &f(pending)"));
            }
        }
        FileConfiguration cfg = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        CorePluginUpdater updater = plugin.updater();
        if (updater != null) {
            sender.sendMessage(color("&7Updater: &f" + (updater.enabled() ? "enabled" : "disabled")));
            sender.sendMessage(color("&7Force ensure: &f"
                    + (updater.forceEnsureAll() ? "all manifest plugins" : "ensure list only")));
            sender.sendMessage(color("&7Manifest: &f" + SiteUrls.pluginsManifest(cfg)));
            sender.sendMessage(color("&7Last update check: &f" + updater.lastResult()));
        }
        sender.sendMessage(color("&7Keys portal: &f" + SiteUrls.keysUrl(cfg)));

        boolean towny = Bukkit.getPluginManager().getPlugin("Towny") != null;
        boolean vault = Bukkit.getPluginManager().getPlugin("Vault") != null;
        if (towny && !vault) {
            sender.sendMessage(color(
                    "&cTowny without Vault — install Vault.jar (economy + Root-Perms permission bridge)."));
        } else if (towny) {
            sender.sendMessage(color("&7Towny + Vault: &aok"));
        }

        List<String> rootPlugins = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .map(Plugin::getName)
                .filter(RootCoreCommand::isRootSuitePlugin)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
        sender.sendMessage(color("&7Detected Root plugins (&f" + rootPlugins.size() + "&7): &f"
                + (rootPlugins.isEmpty() ? "(none)" : String.join(", ", rootPlugins))));
    }

    private void sendLicense(CommandSender sender) {
        LicenseGate gate = plugin.licenseGate();
        LicenseStatus status = gate.status("Root-Core");
        sender.sendMessage(color("&6--- Root-Core license ---"));
        sender.sendMessage(color("&7Mode: &f" + gate.mode().name().toLowerCase(Locale.ROOT)));
        sender.sendMessage(color("&7Status: &f" + status.name()));
        sender.sendMessage(color("&7isLicensed(*): &f" + gate.isLicensed("Root-Times")));
        sender.sendMessage(color("&7verify-remote: &f" + gate.verifyRemoteEnabled() + " &8(stub)"));
        LicenseConnectService connect = plugin.licenseConnect();
        if (connect != null) {
            sender.sendMessage(color(
                    "&7Product-key: &f" + (connect.productKeyPresent() ? "present" : "blank in root-core.yml")));
            sender.sendMessage(color("&7Server-name: &f"
                    + (connect.serverName().isBlank() ? "(blank)" : connect.serverName())));
            sender.sendMessage(color("&7Bound server-id: &f"
                    + (connect.boundServerId().isBlank() ? "(none)" : connect.boundServerId())));
        }
        FileConfiguration cfg = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        sender.sendMessage(color(
                "&7Install: &fserver-name &7+ &fproduct-key &7in root-core.yml → bind/presence → My Servers."));
        sender.sendMessage(color("&7Get a key: &f" + SiteUrls.keysUrl(cfg)));
        sender.sendMessage(color(
                "&8Future enforce: signed offline lease. Secrets never printed here."));
    }

    private static String formatProbeLine(NetworkConnectProbe.Line line) {
        return switch (line.level()) {
            case OK -> "&aOK &7" + line.text();
            case FAIL -> "&cFAIL &7" + line.text();
            case WARN -> "&eWARN &7" + line.text();
        };
    }

    private static boolean isRootSuitePlugin(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("rootmc")
                || n.equals("roothelp")
                || n.equals("rootmc-shops")
                || n.startsWith("root-");
    }

    private static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "(blank)";
        }
        if (value.length() <= 2) {
            return "**";
        }
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rootcore.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = List.of(
                    "status", "connect", "reload", "license", "update", "comms", "discord");
            List<String> out = new ArrayList<>();
            for (String o : options) {
                if (o.startsWith(prefix)) {
                    out.add(o);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!sub.equals("comms") && !sub.equals("discord")) {
                return List.of();
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String o : List.of("status", "reload")) {
                if (o.startsWith(prefix)) {
                    out.add(o);
                }
            }
            return out;
        }
        return List.of();
    }
}
