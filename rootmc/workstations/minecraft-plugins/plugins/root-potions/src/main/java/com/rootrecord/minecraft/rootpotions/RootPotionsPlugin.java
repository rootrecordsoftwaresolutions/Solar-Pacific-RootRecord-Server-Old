package com.rootrecord.minecraft.rootpotions;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootpotions.catalog.PotionCatalog;
import com.rootrecord.minecraft.rootpotions.catalog.PotionEntry;
import com.rootrecord.minecraft.rootpotions.command.PotionsCommand;
import com.rootrecord.minecraft.rootpotions.config.PotionsMessages;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootPotionsPlugin extends JavaPlugin {

    private RootRecordYamlConfig yamlConfig;
    private PotionsMessages messages;
    private PotionCatalog catalog;
    private int listPageSize = 8;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yamlConfig = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_POTIONS_CONFIG, "root-potions.yml");
        yamlConfig.load();
        reloadLocalConfig();

        var potions = getCommand("potions");
        if (potions != null) {
            PotionsCommand handler = new PotionsCommand(this);
            potions.setExecutor(handler);
            potions.setTabCompleter(handler);
        }

        getLogger().info("Root-Potions enabled — /potions brewing guide (" + catalog.size() + " entries).");
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        var cfg = yamlConfig != null ? yamlConfig.config() : null;
        messages = PotionsMessages.from(cfg);
        catalog = PotionCatalog.load(cfg);
        listPageSize = Math.max(1, cfg != null ? cfg.getInt("list-page-size", 8) : 8);
    }

    public PotionsMessages messages() {
        return messages;
    }

    public PotionCatalog catalog() {
        return catalog;
    }

    public int listPageSize() {
        return listPageSize;
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String key) {
        return colorize(messages.prefix() + messages.get(key, key));
    }

    public String rawMsg(String key) {
        return messages.get(key, key);
    }

    public void sendRecipe(org.bukkit.command.CommandSender sender, PotionEntry entry) {
        sender.sendMessage(colorize(rawMsg("recipe-header").replace("{name}", entry.name())));
        if (!entry.ingredient().isBlank()) {
            sender.sendMessage(colorize("&7Ingredient: &f" + entry.ingredient()));
        }
        for (String line : entry.lines()) {
            sender.sendMessage(colorize(line));
        }
    }
}
