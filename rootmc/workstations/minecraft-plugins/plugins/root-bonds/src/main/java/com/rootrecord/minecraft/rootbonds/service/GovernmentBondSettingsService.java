package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.config.BondsConfig;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-town/nation auto-bond opt-in stored in MySQL (mayor / nation leader toggles in /bonds). */
public final class GovernmentBondSettingsService {

    private final RootBondsPlugin plugin;
    private volatile BondsStore store;
    private final ConcurrentHashMap<UUID, Boolean> cache = new ConcurrentHashMap<>();

    public GovernmentBondSettingsService(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(BondsStore store) {
        this.store = store;
        cache.clear();
    }

    public boolean isEnabled(UUID accountUuid, boolean defaultEnabled) {
        if (accountUuid == null) {
            return defaultEnabled;
        }
        Boolean cached = cache.get(accountUuid);
        if (cached != null) {
            return cached;
        }
        BondsStore active = store;
        if (active == null || !plugin.mysqlReady()) {
            return defaultEnabled;
        }
        try {
            boolean enabled = active.isGovernmentAutoBondEnabled(accountUuid, defaultEnabled);
            cache.put(accountUuid, enabled);
            return enabled;
        } catch (SQLException ex) {
            plugin.getLogger().warning("Government bond settings read failed: " + ex.getMessage());
            return defaultEnabled;
        }
    }

    public void setEnabled(UUID accountUuid, String kind, String displayName, boolean enabled) {
        if (accountUuid == null) {
            return;
        }
        cache.put(accountUuid, enabled);
        BondsStore active = store;
        if (active == null || !plugin.mysqlReady()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                active.setGovernmentAutoBondEnabled(accountUuid, kind, displayName, enabled);
            } catch (SQLException ex) {
                plugin.getLogger().warning("Government bond settings write failed: " + ex.getMessage());
            }
        });
    }

    public boolean globalAutoBondEnabled() {
        BondsConfig cfg = plugin.bonds() == null ? null : plugin.bonds().config();
        return cfg == null || cfg.autoBondGovernments();
    }
}
