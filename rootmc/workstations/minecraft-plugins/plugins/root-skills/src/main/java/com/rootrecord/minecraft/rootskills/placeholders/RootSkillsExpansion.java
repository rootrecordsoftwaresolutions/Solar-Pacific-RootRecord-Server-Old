package com.rootrecord.minecraft.rootskills.placeholders;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.Optional;

/** PlaceholderAPI expansion: %rootskills_power%, %rootskills_<skill>_level%, etc. */
public final class RootSkillsExpansion extends PlaceholderExpansion {

    private final RootSkillsPlugin plugin;

    public RootSkillsExpansion(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "rootskills";
    }

    @Override
    public String getAuthor() {
        return "Root Record";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || player == null || player.getUniqueId() == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        Optional<PlayerSkillsProfile> profile = plugin.repository().find(player.getUniqueId());
        if (profile.isEmpty()) {
            return "0";
        }
        if ("power".equals(key) || "powerlevel".equals(key)) {
            return Integer.toString(profile.get().powerLevel());
        }
        if ("mana".equals(key)) {
            return Double.toString(profile.get().mana());
        }
        if ("class".equals(key) || "classid".equals(key)) {
            String cid = profile.get().classId();
            return cid == null ? "" : cid;
        }
        // skill_level / skill_xp patterns
        int underscore = key.lastIndexOf('_');
        if (underscore <= 0) {
            return null;
        }
        String skillPart = key.substring(0, underscore);
        String field = key.substring(underscore + 1);
        Optional<SkillId> skill = SkillId.fromString(skillPart);
        if (skill.isEmpty()) {
            return null;
        }
        return switch (field) {
            case "level", "lvl" -> Integer.toString(profile.get().skill(skill.get()).level());
            case "xp" -> Long.toString(profile.get().skill(skill.get()).xp());
            case "prestige" -> Integer.toString(profile.get().skill(skill.get()).prestige());
            default -> null;
        };
    }
}
