package com.rootrecord.minecraft.rootloans.config;

import org.bukkit.configuration.file.FileConfiguration;

public record LoansMessages(String prefix) {

    public static LoansMessages from(FileConfiguration cfg) {
        return new LoansMessages(cfg.getString("messages.prefix", ""));
    }

    public String raw(FileConfiguration cfg, String key) {
        return cfg.getString("messages." + key, key);
    }
}
