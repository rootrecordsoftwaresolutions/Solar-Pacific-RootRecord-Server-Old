package com.rootrecord.minecraft.rootadmin;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.command.PluginCommandRegistrar;
import com.rootrecord.minecraft.common.command.ServerRestartBridge;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootadmin.command.*;
import com.rootrecord.minecraft.rootadmin.listener.AdminCommandProbeListener;
import com.rootrecord.minecraft.rootadmin.listener.ActivityLogListener;
import com.rootrecord.minecraft.rootadmin.listener.ForcedTeleportBlockListener;
import com.rootrecord.minecraft.rootadmin.listener.GamemodeGuardListener;
import com.rootrecord.minecraft.rootadmin.listener.StaffDeopListener;
import com.rootrecord.minecraft.rootadmin.listener.TownyMobCombatListener;
import com.rootrecord.minecraft.rootadmin.rails.RailOwnershipStore;
import com.rootrecord.minecraft.rootadmin.rails.RailProtectionListener;
import com.rootrecord.minecraft.rootadmin.cloud.ReportCloudClient;
import com.rootrecord.minecraft.rootadmin.probe.AdminCommandProbeService;
import com.rootrecord.minecraft.rootadmin.report.PlayerActivityLog;
import com.rootrecord.minecraft.rootadmin.report.ReportService;
import com.rootrecord.minecraft.rootadmin.world.WorldBorderService;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootAdminPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private RootEssentialsPlugin essentials;
    private PlayerActivityLog activityLog;
    private ReportService reportService;
    private ReportCloudClient reportCloud;
    private WorldBorderService worldBorderService;
    private RailOwnershipStore railOwnershipStore;
    private AdminCommandProbeService adminCommandProbe;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_ADMIN_CONFIG, "root-admin.yml");
        yaml.load();
        reportCloud = new ReportCloudClient(RootRecordCloudConfig.resolve(this, yaml.config()));

        essentials = resolveEssentials();
        if (essentials == null) {
            getLogger().severe("Root-Essentials is required — disabling Root-Admin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        int lookback = yaml.config().getInt("reports.lookback-minutes", 30);
        activityLog = new PlayerActivityLog(lookback);
        reportService = new ReportService(this, activityLog);
        adminCommandProbe = new AdminCommandProbeService(this, reportCloud);

        worldBorderService = new WorldBorderService(this);
        railOwnershipStore = new RailOwnershipStore(this);
        registerCommands();
        getServer().getScheduler().runTask(this, this::hostRestartCommandIfNeeded);
        getServer().getPluginManager().registerEvents(new ActivityLogListener(activityLog), this);
        getServer().getPluginManager().registerEvents(new AdminCommandProbeListener(adminCommandProbe), this);
        getServer().getPluginManager().registerEvents(new ForcedTeleportBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new RailProtectionListener(this, railOwnershipStore), this);
        getServer().getPluginManager().registerEvents(new GamemodeGuardListener(this), this);
        StaffDeopListener staffDeop = new StaffDeopListener(this);
        getServer().getPluginManager().registerEvents(staffDeop, this);
        getServer().getScheduler().runTaskLater(this, staffDeop::deopStaffOnline, 40L);
        getServer().getPluginManager().registerEvents(new TownyMobCombatListener(), this);
        if (yaml.config().getBoolean("world-border.apply-on-start", true)) {
            getServer().getScheduler().runTaskLater(this, this::applyWorldBorder, 20L);
        }
        getLogger().info("Root-Admin enabled — staff commands, /report, admin-probe.");
    }

    public void reloadLocalConfig() {
        yaml.reload();
        if (adminCommandProbe != null) {
            adminCommandProbe.reload();
        }
    }

    public boolean probeEnabled() {
        return yaml.config().getBoolean("admin-probe.enabled", true);
    }

    public int probeThreshold() {
        return Math.max(1, yaml.config().getInt("admin-probe.threshold", 5));
    }

    public int probeWindowMinutes() {
        return Math.max(5, yaml.config().getInt("admin-probe.window-minutes", 60));
    }

    public boolean probeNotifyStaff() {
        return yaml.config().getBoolean("admin-probe.notify-staff", true);
    }

    public boolean probeDiscordPost() {
        return yaml.config().getBoolean("admin-probe.discord-post", true);
    }

    @Override
    public void onDisable() {
        if (railOwnershipStore != null) {
            railOwnershipStore.flush();
        }
    }

    public void applyWorldBorder() {
        if (worldBorderService != null) {
            worldBorderService.applyFromConfig();
        }
    }

    private RootEssentialsPlugin resolveEssentials() {
        var plugin = getServer().getPluginManager().getPlugin("Root-Essentials");
        if (plugin instanceof RootEssentialsPlugin re && plugin.isEnabled()) {
            return re;
        }
        return null;
    }

    public RootEssentialsPlugin essentials() {
        return essentials;
    }

    public ReportService reports() {
        return reportService;
    }

    public int lookbackMinutes() {
        return yaml.config().getInt("reports.lookback-minutes", 30);
    }

    public int reportCooldownSeconds() {
        return yaml.config().getInt("reports.cooldown-seconds", 120);
    }

    public boolean notifyStaff() {
        return yaml.config().getBoolean("reports.notify-staff", true);
    }

    public boolean saveReportFiles() {
        return yaml.config().getBoolean("reports.save-files", true);
    }

    public boolean discordReportsEnabled() {
        return yaml.config().getBoolean("reports.discord-post", true);
    }

    public ReportCloudClient reportCloud() {
        return reportCloud;
    }

    public boolean railsEnabled() {
        return yaml.config().getBoolean("rails.enabled", true);
    }

    public boolean railsProtectUnknown() {
        return yaml.config().getBoolean("rails.protect-unknown", false);
    }

    public void sendRailDenyMessage(Player player, String ownerName) {
        String owner = ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
        player.sendMessage(colorize(msg("rails-deny").replace("{owner}", owner)));
        player.sendMessage(colorize(messageBody("rails-deny-request").replace("{owner}", owner)));
        player.sendMessage(colorize(messageBody("rails-deny-rules")));
        player.sendMessage(colorize(messageBody("rails-deny-admin")));
    }

    private String messageBody(String key) {
        return yaml.config().getString("messages." + key, key);
    }

    public String msg(String key) {
        String p = yaml.config().getString("messages.prefix", "");
        String body = yaml.config().getString("messages." + key, key);
        return colorize(p + body);
    }

    public String colorize(String input) {
        return input == null ? "" : input.replace('&', '\u00A7');
    }

    private void registerCommands() {
        RootEssentialsPlugin ess = essentials;
        bind("kick", new ModerationCommands.Kick(ess));
        bind("ban", new ModerationCommands.Ban(ess));
        bind("tempban", new ModerationCommands.TempBan(ess));
        bind("unban", new ModerationCommands.Unban(ess));
        bind("mute", new ModerationCommands.Mute(ess));
        bind("unmute", new ModerationCommands.Unmute(ess));
        bind("tpoffline", new AdminCommands.TpOffline(ess));
        bind("vanish", new AdminCommands.Vanish(ess));
        bind("socialspy", new AdminCommands.SocialSpy(ess));
        bind("invsee", new AdminCommands.Invsee(ess));
        bind("echest", new AdminCommands.Echest(ess));
        bind("enderchest", new AdminCommands.Echest(ess));
        bind("setspawn", new AdminCommands.SetSpawn(ess));
        bind("tppos", new AdminCommands.TpPos(ess));
        bind("sudo", new AdminCommands.Sudo(ess));
        bind("give", new AdminCommands.Give(ess));
        bind("speed", new AdminCommands.Speed(ess));
        bind("realname", new AdminCommands.Realname(ess));
        bind("whois", new AdminCommands.Whois(ess));
        bind("recipe", new AdminCommands.Recipe(ess));
        bind("skull", new AdminCommands.Skull(ess));
        bind("lightning", new AdminCommands.Lightning(ess));
        bind("ext", new AdminCommands.Ext(ess));
        bind("extinguish", new AdminCommands.Ext(ess));
        bind("mutechat", new AdminCommands.Mutechat(ess));
        bind("unmutechat", new AdminCommands.Unmutechat(ess));
        bind("tpo", new AdminTeleportCommands.Tpo(ess));
        bind("tpohere", new AdminTeleportCommands.TpoHere(ess));
        bind("tpall", new AdminTeleportCommands.TpAll(ess));
        bind("fly", new AdminPlayerCommands.Fly(ess));
        bind("god", new AdminPlayerCommands.God(ess));
        bind("gamemode", new AdminPlayerCommands.Gamemode(ess));
        bind("gm", new AdminPlayerCommands.Gamemode(ess));
        bind("gms", new AdminPlayerCommands.Gamemode(ess));
        bind("gmc", new AdminPlayerCommands.Gamemode(ess));
        bind("gma", new AdminPlayerCommands.Gamemode(ess));
        bind("gmsp", new AdminPlayerCommands.Gamemode(ess));
        bind("broadcast", new AdminPlayerCommands.Broadcast(ess));
        bind("time", new WorldCommands.Time(ess));
        bind("day", new WorldCommands.Day(ess));
        bind("night", new WorldCommands.Night(ess));
        bind("weather", new WorldCommands.Weather(ess));
        bind("remove", new AdminExtraCommands.Remove(ess));
        bind("burn", new AdminExtraCommands.Burn(ess));
        bind("world", new AdminExtraCommands.World(ess));
        bind("setwarp", new AdminWarpCommands.SetWarp(ess));
        bind("delwarp", new AdminWarpCommands.DelWarp(ess));
        bind("item", new AdminCommands.Give(ess));
        bind("report", new ReportCommand(this));
        bind("worldborder", new WorldBorderCommand(this));
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(executor);
        else getLogger().warning("Command not in plugin.yml: " + name);
    }

    private void hostRestartCommandIfNeeded() {
        RestartHostCommand handler = new RestartHostCommand();
        if (getServer().getCommandMap().getCommand("rootrestart") == null) {
            if (!ServerRestartBridge.isActive()) {
                getLogger().warning("Root-Restart is not active — /rootrestart unavailable.");
            } else {
                PluginCommand cmd = PluginCommandRegistrar.register(
                        this,
                        "rootrestart",
                        "Graceful server restart with countdown",
                        "/rootrestart [cancel]",
                        java.util.List.of("rrrestart"));
                if (cmd != null) {
                    cmd.setExecutor(handler);
                    cmd.setTabCompleter(handler);
                    getLogger().warning("Hosted /rootrestart from Root-Admin (Root-Restart did not register it).");
                }
            }
        }
        if (getServer().getCommandMap().getCommand("rootstop") == null) {
            if (!ServerRestartBridge.isActive()) {
                getLogger().warning("Root-Restart is not active — /rootstop unavailable.");
            } else {
                PluginCommand cmd = PluginCommandRegistrar.register(
                        this,
                        "rootstop",
                        "Graceful stop for update with countdown",
                        "/rootstop [cancel]",
                        java.util.List.of("rrstop"));
                if (cmd != null) {
                    cmd.setExecutor(handler);
                    cmd.setTabCompleter(handler);
                    getLogger().warning("Hosted /rootstop from Root-Admin (Root-Restart did not register it).");
                }
            }
        }
    }
}
