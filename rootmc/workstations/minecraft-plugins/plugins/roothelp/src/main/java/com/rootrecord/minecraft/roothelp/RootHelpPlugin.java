package com.rootrecord.minecraft.roothelp;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.roothelp.cloud.HelpCloudClient;
import com.rootrecord.minecraft.roothelp.command.CommandTestCommand;
import com.rootrecord.minecraft.roothelp.command.FeedbackCommand;
import com.rootrecord.minecraft.roothelp.command.CommandsCommand;
import com.rootrecord.minecraft.roothelp.command.DiscordCommand;
import com.rootrecord.minecraft.roothelp.command.MapCommand;
import com.rootrecord.minecraft.roothelp.command.RulesCommand;
import com.rootrecord.minecraft.roothelp.commandtest.CommandTestConfig;
import com.rootrecord.minecraft.roothelp.commandtest.CommandTestListener;
import com.rootrecord.minecraft.roothelp.commandtest.CommandTestReminderTask;
import com.rootrecord.minecraft.roothelp.commandtest.CommandTestService;
import com.rootrecord.minecraft.roothelp.commandtest.CommandTestStore;
import com.rootrecord.minecraft.roothelp.config.HelpConfig;
import com.rootrecord.minecraft.roothelp.config.HelpMessages;
import com.rootrecord.minecraft.roothelp.catalog.CommandCatalog;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootHelpPlugin extends JavaPlugin {

    private RootRecordYamlConfig yamlConfig;
    private HelpConfig helpConfig;
    private HelpMessages messages;
    private CommandCatalog catalog;
    private HelpCloudClient cloud;
    private CommandTestConfig commandTestConfig;
    private CommandTestStore commandTestStore;
    private CommandTestService commandTestService;
    private CommandTestReminderTask commandTestReminders;

    @Override
    public void onEnable() {
        RootRecordCloudConfig.ensureDefaults(this);
        RootRecordFolders.ensureDir(this);
        yamlConfig = new RootRecordYamlConfig(this, RootRecordFolders.ROOTHELP_CONFIG, "roothelp.yml");
        yamlConfig.load();
        reloadLocalConfig();

        var rules = getCommand("rules");
        if (rules != null) {
            rules.setExecutor(new RulesCommand(this));
        }
        var cmds = getCommand("cmds");
        if (cmds != null) {
            var handler = new CommandsCommand(this);
            cmds.setExecutor(handler);
            cmds.setTabCompleter(handler);
        }
        var discord = getCommand("discord");
        if (discord != null) {
            discord.setExecutor(new DiscordCommand(this));
        }
        var map = getCommand("map");
        if (map != null) {
            map.setExecutor(new MapCommand(this));
        }
        var feedback = getCommand("feedback");
        if (feedback != null) {
            feedback.setExecutor(new FeedbackCommand(this));
        }
        var cmdtest = getCommand("cmdtest");
        if (cmdtest != null && commandTestService != null) {
            if (commandTestService.enabled()) {
                var testHandler = new CommandTestCommand(this, commandTestService);
                cmdtest.setExecutor(testHandler);
                cmdtest.setTabCompleter(testHandler);
                getServer().getPluginManager().registerEvents(new CommandTestListener(this, commandTestService), this);
                commandTestReminders = new CommandTestReminderTask(this, commandTestService);
                commandTestReminders.start();
            } else {
                // Registered in plugin.yml but disabled in roothelp.yml — swallow, no "unavailable" spam.
                cmdtest.setExecutor((sender, command, label, args) -> true);
            }
        }

        getLogger().info("RootHelp enabled — /rules, /cmds, /discord, /map, /feedback"
                + (commandTestService != null && commandTestService.enabled() ? ", /cmdtest onboarding" : ""));
    }

    @Override
    public void onDisable() {
        if (commandTestReminders != null) {
            commandTestReminders.stop();
        }
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        var cfg = yamlConfig != null ? yamlConfig.config() : null;
        helpConfig = HelpConfig.from(cfg);
        messages = HelpMessages.from(cfg);
        catalog = CommandCatalog.load(cfg);
        cloud = new HelpCloudClient(RootRecordCloudConfig.resolve(this, cfg));
        commandTestConfig = CommandTestConfig.from(this, cfg);
        commandTestStore = new CommandTestStore(commandTestConfig);
        try {
            if (commandTestConfig.mysqlConfigured()) {
                commandTestStore.initSchema();
            }
        } catch (Exception ex) {
            getLogger().severe("Command test MySQL init failed: " + ex.getMessage());
        }
        commandTestService = new CommandTestService(this, commandTestConfig, commandTestStore);
        if (commandTestReminders != null) {
            commandTestReminders.stop();
            if (commandTestService.enabled()) {
                commandTestReminders.start();
            }
        }
    }

    public HelpConfig helpConfig() {
        return helpConfig;
    }

    public HelpMessages messages() {
        return messages;
    }

    public CommandCatalog catalog() {
        return catalog;
    }

    public HelpCloudClient cloud() {
        return cloud;
    }

    public CommandTestService commandTestService() {
        return commandTestService;
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String key) {
        return colorize(messages.prefix() + messages.get(key));
    }

    public String rawMsg(String key) {
        return messages.get(key);
    }
}
