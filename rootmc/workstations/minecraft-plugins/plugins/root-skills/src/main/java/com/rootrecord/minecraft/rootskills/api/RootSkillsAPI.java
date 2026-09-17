package com.rootrecord.minecraft.rootskills.api;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface RootSkillsAPI {

    static RootSkillsAPI get() {
        RootSkillsAPI api = RootSkillsPlugin.api();
        if (api == null) {
            throw new IllegalStateException("Root-Skills is not enabled");
        }
        return api;
    }

    Optional<PlayerSkillsProfile> profile(UUID playerId);

    default Optional<PlayerSkillsProfile> profile(Player player) {
        return player == null ? Optional.empty() : profile(player.getUniqueId());
    }

    int getLevel(UUID playerId, SkillId skill);

    long getXp(UUID playerId, SkillId skill);

    void addXp(UUID playerId, SkillId skill, long amount);

    void setLevel(UUID playerId, SkillId skill, int level);

    int getPowerLevel(UUID playerId);

    double getMana(UUID playerId);

    String getClassId(UUID playerId);

    int getPrestige(UUID playerId, SkillId skill);

    RootSkillsPlugin plugin();
}
