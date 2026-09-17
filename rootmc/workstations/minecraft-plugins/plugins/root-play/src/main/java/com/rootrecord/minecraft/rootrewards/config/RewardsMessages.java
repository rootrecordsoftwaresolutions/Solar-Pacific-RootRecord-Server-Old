package com.rootrecord.minecraft.rootrewards.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class RewardsMessages {

    private final String prefix;

    RewardsMessages(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public static RewardsMessages from(FileConfiguration cfg) {
        return new RewardsMessages(cfg.getString("messages.prefix", ""));
    }

    public String prefix() {
        return prefix;
    }

    public String get(FileConfiguration cfg, String key) {
        return cfg.getString("messages." + key, "");
    }
}
