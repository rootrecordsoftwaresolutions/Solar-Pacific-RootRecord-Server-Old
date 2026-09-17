package com.rootrecord.minecraft.roothaste;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.roothaste.command.LightCommand;
import com.rootrecord.minecraft.roothaste.command.PassCommand;
import com.rootrecord.minecraft.roothaste.data.JointRecordStore;
import com.rootrecord.minecraft.roothaste.listener.JointChatListener;
import com.rootrecord.minecraft.roothaste.placeholder.RootHasteExpansion;
import com.rootrecord.minecraft.roothaste.service.JointSessionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class RootHastePlugin extends JavaPlugin {

    public static final String CONFIG_FILE = "root-haste.yml";
    private static final String LEGACY_CONFIG_FILE = "root-joint.yml";

    private RootRecordYamlConfig yaml;
    private JointSessionService sessions;
    private JointRecordStore records;
    private PassTheme theme = PassTheme.TORCH;
    private Metrics metrics;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);

        RootRecordFolders.ensureDir(this);
        migrateLegacyConfig();
        yaml = new RootRecordYamlConfig(this, CONFIG_FILE, "root-haste.yml");
        yaml.load();
        applyThemeFromConfig();
        records = new JointRecordStore(this, theme.recordsFile());
        sessions = new JointSessionService(this);
        sessions.reload(yaml.config());

        LightCommand light = new LightCommand(this);
        PassCommand pass = new PassCommand(this);
        bind("torch", light);
        bind("torchpass", pass);
        bind("joint", light);
        bind("pass", pass);

        getServer().getPluginManager().registerEvents(sessions, this);
        getServer().getPluginManager().registerEvents(new JointChatListener(this), this);
        Bukkit.getScheduler().runTask(this, this::registerPlaceholderExpansionIfPresent);
        Bukkit.getScheduler().runTaskLater(this, this::registerPlaceholderExpansionIfPresent, 40L);

        getLogger().info("Root-Haste enabled — theme=" + theme.name()
                + " (/" + theme.lightCommand() + ", /" + theme.passCommand() + ", /pass)"
                + (theme.isJoint() ? " [18+ joint unlocked]" : " [torch default]"));
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (sessions != null) {
            sessions.shutdown();
        }
    }

    public void reloadLocal() {
        if (yaml != null) {
            yaml.reload();
        }
        PassTheme previous = theme;
        applyThemeFromConfig();
        if (records == null || previous != theme) {
            records = new JointRecordStore(this, theme.recordsFile());
        } else {
            records.load();
        }
        if (sessions != null && yaml != null) {
            if (previous != theme && sessions.isActive()) {
                sessions.shutdown();
            }
            sessions.reload(yaml.config());
        }
    }

    private void migrateLegacyConfig() {
        Path dir = RootRecordFolders.dir(this).toPath();
        Path haste = dir.resolve(CONFIG_FILE);
        Path joint = dir.resolve(LEGACY_CONFIG_FILE);
        if (Files.exists(haste) || !Files.exists(joint)) {
            return;
        }
        try {
            Files.copy(joint, haste, StandardCopyOption.COPY_ATTRIBUTES);
            getLogger().info("Migrated " + LEGACY_CONFIG_FILE + " → " + CONFIG_FILE);
        } catch (IOException ex) {
            getLogger().warning("Could not migrate legacy Root-Joint config: " + ex.getMessage());
        }
    }

    private void applyThemeFromConfig() {
        FileConfiguration cfg = configFile();
        boolean unlock = cfg != null && cfg.getBoolean("unlock-joint", false);
        theme = unlock ? PassTheme.JOINT : PassTheme.TORCH;
    }

    public PassTheme theme() {
        return theme;
    }

    public boolean jointUnlocked() {
        return theme.isJoint();
    }

    /** Persist {@code cost-gold} and apply live. */
    public boolean setCostGold(double amount) {
        if (yaml == null || sessions == null) {
            return false;
        }
        double gold = Math.max(0, amount);
        yaml.config().set("cost-gold", gold);
        yaml.save();
        sessions.setCostGold(gold);
        return true;
    }

    public JointSessionService sessions() {
        return sessions;
    }

    public JointRecordStore records() {
        return records;
    }

    public FileConfiguration configFile() {
        return yaml != null ? yaml.config() : null;
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String logicalKey) {
        FileConfiguration cfg = configFile();
        String prefix = cfg != null ? cfg.getString("messages.prefix", "") : "";
        String mapped = theme.messageKey(logicalKey);
        String body = null;
        if (cfg != null) {
            body = cfg.getString(theme.messagesPath() + "." + mapped);
            if (body == null) {
                body = cfg.getString("messages." + mapped);
            }
            if (body == null) {
                body = cfg.getString("messages." + logicalKey);
            }
        }
        return colorize((prefix == null ? "" : prefix) + (body == null ? logicalKey : body));
    }

    public boolean hasUse(Player player) {
        return player != null
                && (player.hasPermission("roothaste.use")
                        || player.hasPermission("rootjoint.use")
                        || player.hasPermission("roottorch.use"));
    }

    public boolean hasBypassFee(Player player) {
        return player != null
                && (player.hasPermission("roothaste.bypass-fee")
                        || player.hasPermission("rootjoint.bypass-fee")
                        || player.hasPermission("roottorch.bypass-fee"));
    }

    public boolean hasReload(org.bukkit.command.CommandSender sender) {
        return sender.hasPermission("roothaste.reload")
                || sender.hasPermission("rootjoint.reload")
                || sender.hasPermission("roottorch.reload");
    }

    public boolean hasPrice(org.bukkit.command.CommandSender sender) {
        return sender.hasPermission("roothaste.price")
                || sender.hasPermission("rootjoint.price")
                || sender.hasPermission("roottorch.price")
                || hasReload(sender);
    }

    public void registerPlaceholderExpansionIfPresent() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new RootHasteExpansion(this, "roothaste").register();
            new RootHasteExpansion(this, "rootjoint").register();
            new RootHasteExpansion(this, "roottorch").register();
        } catch (Throwable ex) {
            getLogger().warning("PlaceholderAPI expansion register failed: " + ex.getMessage());
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) {
            cmd.setExecutor(ce);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }
}
