package com.rootrecord.minecraft.common;

import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads {@code plugins/RootMC/rootmc-ui.yml} and applies {@link FancyHeadlines} toggles. */
public final class FancyUiConfig {

    private FancyUiConfig() {}

    public static void load(JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }
        RootRecordYamlConfig yaml = new RootRecordYamlConfig(
                plugin, RootRecordFolders.ROOTMC_UI_CONFIG, "rootmc-ui.yml");
        yaml.load();
        apply(yaml.config());
    }

    public static void apply(FileConfiguration cfg) {
        if (cfg == null) {
            FancyHeadlines.setEnabled(true);
            FancyHeadlines.setUseGlyph(true);
            return;
        }
        FancyHeadlines.setEnabled(cfg.getBoolean("fancy-headlines", true));
        FancyHeadlines.setUseGlyph(cfg.getBoolean("use-glyph", true));
    }
}
