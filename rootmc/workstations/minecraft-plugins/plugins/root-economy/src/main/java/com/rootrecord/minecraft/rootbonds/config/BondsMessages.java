package com.rootrecord.minecraft.rootbonds.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class BondsMessages {

    private final String prefix;
    private final FileConfiguration cfg;

    public BondsMessages(FileConfiguration cfg) {
        this.cfg = cfg;
        this.prefix = cfg.getString("messages.prefix", "");
    }

    public String msg(String key) {
        String body = cfg.getString("messages." + key, "&7[" + key + "]");
        return prefix + body;
    }
}
