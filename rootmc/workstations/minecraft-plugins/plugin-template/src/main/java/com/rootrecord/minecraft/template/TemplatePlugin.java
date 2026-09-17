package com.rootrecord.minecraft.template;

import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.plugin.java.JavaPlugin;

/** Minimal Paper plugin used to verify the Gradle toolchain. Copy and rename for new plugins. */
public final class TemplatePlugin extends JavaPlugin {

    public static final String CONFIG_FILE = "template.yml";

    private RootRecordYamlConfig yamlConfig;

    @Override
    public void onEnable() {
        yamlConfig = new RootRecordYamlConfig(this, CONFIG_FILE, CONFIG_FILE);
        yamlConfig.load();
        getLogger().info("RootRecord template plugin enabled (v" + getDescription().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        getLogger().info("RootRecord template plugin disabled.");
    }
}
