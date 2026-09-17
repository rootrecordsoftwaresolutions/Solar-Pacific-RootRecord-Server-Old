package com.rootrecord.minecraft.rootskills.prestige;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.api.events.RootSkillsPrestigeEvent;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import com.rootrecord.minecraft.rootskills.model.SkillProgress;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PrestigeManager {

    private final RootSkillsPlugin plugin;

    public PrestigeManager(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("features.prestige", true);
    }

    public int threshold() {
        return plugin.getConfig().getInt("prestige-threshold", 1000);
    }

    public boolean canPrestige(PlayerSkillsProfile profile, SkillId skill) {
        if (!enabled() || profile == null || skill == null) {
            return false;
        }
        return profile.skill(skill).level() >= threshold();
    }

    public boolean prestige(PlayerSkillsProfile profile, SkillId skill) {
        if (!canPrestige(profile, skill)) {
            return false;
        }
        SkillProgress progress = profile.skill(skill);
        int old = progress.prestige();
        progress.setPrestige(old + 1);
        progress.setLevel(0);
        progress.setXp(0L);
        double buffPer = plugin.getConfig().getDouble("prestige.buff-per-prestige", 0.05);
        progress.setPrestigeBuff(1.0 + (buffPer * progress.prestige()));
        profile.markDirty();
        plugin.repository().saveAsync(profile);

        Player player = Bukkit.getPlayer(profile.playerId());
        if (player != null) {
            Bukkit.getPluginManager().callEvent(
                    new RootSkillsPrestigeEvent(player, skill, old, progress.prestige()));
            player.sendMessage(plugin.msg("prestige.done")
                    .replace("{skill}", skill.key())
                    .replace("{prestige}", Integer.toString(progress.prestige())));
        }
        return true;
    }
}
