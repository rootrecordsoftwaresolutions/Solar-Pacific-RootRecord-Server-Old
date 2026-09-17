package com.rootrecord.minecraft.rootskills.engine;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import org.bukkit.entity.Player;

import java.util.Map;

public final class TriggerBus {

    private final RootSkillsPlugin plugin;

    public TriggerBus(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void fire(Player player, String triggerId, Map<String, Object> context) {
        if (player == null || triggerId == null || triggerId.isBlank()) {
            return;
        }
        Map<String, Object> ctx = context == null ? Map.of() : context;
        if (plugin.talentManager() != null) {
            plugin.talentManager().handleTrigger(player, triggerId, ctx);
        }
    }
}
