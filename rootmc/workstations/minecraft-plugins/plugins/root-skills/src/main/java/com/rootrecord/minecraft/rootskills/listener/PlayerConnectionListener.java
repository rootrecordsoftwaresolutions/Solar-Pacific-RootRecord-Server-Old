package com.rootrecord.minecraft.rootskills.listener;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final RootSkillsPlugin plugin;

    public PlayerConnectionListener(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.repository().loadAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName(), () -> {
            if (plugin.boosterManager() != null) {
                plugin.boosterManager().loadPlayer(event.getPlayer().getUniqueId());
            }
            var profile = plugin.repository().getOrCreate(event.getPlayer().getUniqueId());
            if (profile.maxMana() <= 0 || profile.mana() <= 0 && profile.maxMana() > 0) {
                profile.setMaxMana(plugin.getConfig().getDouble("mana.max", 100));
                if (profile.mana() <= 0) {
                    profile.setMana(profile.maxMana());
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.abilitySessions() != null) {
            plugin.abilitySessions().clear(event.getPlayer().getUniqueId());
        }
        if (plugin.cooldownService() != null) {
            plugin.cooldownService().clear(event.getPlayer().getUniqueId());
        }
        plugin.repository().unload(event.getPlayer().getUniqueId());
    }
}
