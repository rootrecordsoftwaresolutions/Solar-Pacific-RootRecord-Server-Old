package com.rootrecord.minecraft.rootskills.mana;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public final class ManaManager {

    private final RootSkillsPlugin plugin;
    private BukkitTask regenTask;

    public ManaManager(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("features.mana", true);
    }

    public void startRegenTask() {
        stop();
        if (!enabled()) {
            return;
        }
        double regen = plugin.getConfig().getDouble("mana.regen-per-second", 1.0);
        double max = plugin.getConfig().getDouble("mana.max", 100.0);
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerSkillsProfile profile = plugin.repository().find(player.getUniqueId()).orElse(null);
                if (profile == null) {
                    continue;
                }
                if (profile.maxMana() <= 0) {
                    profile.setMaxMana(max);
                }
                if (profile.mana() < profile.maxMana()) {
                    profile.setMana(Math.min(profile.maxMana(), profile.mana() + regen));
                }
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
    }

    public void tickRegen(UUID playerId) {
        // handled by task
    }

    public boolean spend(PlayerSkillsProfile profile, double amount) {
        if (!enabled() || profile == null || amount <= 0) {
            return true;
        }
        if (profile.mana() < amount) {
            return false;
        }
        profile.setMana(profile.mana() - amount);
        return true;
    }

    public void restore(PlayerSkillsProfile profile, double amount) {
        if (!enabled() || profile == null || amount <= 0) {
            return;
        }
        profile.setMana(Math.min(profile.maxMana(), profile.mana() + amount));
    }

    public double getMana(UUID playerId) {
        return plugin.repository().getOrCreate(playerId).mana();
    }
}
