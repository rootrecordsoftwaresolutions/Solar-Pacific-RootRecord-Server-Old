package com.rootrecord.minecraft.rootheads;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootHeadsPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private HeadsConfig config;
    private HeadItemFactory items;
    private HeadDropService drops;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_HEADS_CONFIG, "root-heads.yml");
        yaml.load();
        config = HeadsConfig.from(yaml.config());
        items = new HeadItemFactory(this);
        drops = new HeadDropService(this);

        HeadsCommand cmd = new HeadsCommand(this);
        PluginCommand bound = getCommand("rootheads");
        if (bound != null) {
            bound.setExecutor(cmd);
            bound.setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(new HeadDeathListener(this), this);
        getLogger().info("Root-Heads enabled — cosmetic mob heads (" + config.mobs().size() + " configured).");
    }

    public void reloadAll() {
        yaml.load();
        config = HeadsConfig.from(yaml.config());
        drops.clearCooldowns();
    }

    public HeadsConfig config() {
        return config;
    }

    public HeadItemFactory items() {
        return items;
    }

    public HeadDropService drops() {
        return drops;
    }

    public String msg(String key) {
        return colorize(config.messages().getOrDefault(key, key));
    }

    public String colorize(String raw) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
