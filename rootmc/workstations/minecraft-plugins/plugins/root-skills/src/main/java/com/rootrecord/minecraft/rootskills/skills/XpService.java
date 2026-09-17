package com.rootrecord.minecraft.rootskills.skills;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.api.events.RootSkillsLevelUpEvent;
import com.rootrecord.minecraft.rootskills.api.events.RootSkillsXpGainEvent;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import com.rootrecord.minecraft.rootskills.model.SkillProgress;
import com.rootrecord.minecraft.rootskills.storage.SkillsRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class XpService {

    private final RootSkillsPlugin plugin;
    private final SkillsRepository repository;
    private XpFormula formula;

    public XpService(RootSkillsPlugin plugin, SkillsRepository repository, XpFormula formula) {
        this.plugin = plugin;
        this.repository = repository;
        this.formula = formula;
    }

    public void setFormula(XpFormula formula) {
        this.formula = formula;
    }

    public XpFormula formula() {
        return formula;
    }

    public void addXp(UUID playerId, SkillId skill, long rawAmount) {
        addXp(playerId, skill, rawAmount, true);
    }

    public void addXp(UUID playerId, SkillId skill, long rawAmount, boolean shareParty) {
        if (playerId == null || skill == null || rawAmount <= 0) {
            return;
        }

        double mult = formula.globalXpMultiplier();
        mult *= plugin.getConfig().getDouble("skill-multipliers." + skill.key(), 1.0);
        if (plugin.boosterManager() != null) {
            mult *= plugin.boosterManager().xpMultiplier(playerId, skill);
        }
        if (plugin.classManager() != null) {
            PlayerSkillsProfile tmp = repository.getOrCreate(playerId);
            mult *= plugin.classManager().skillXpMultiplier(tmp.classId(), skill);
        }
        PlayerSkillsProfile profile = repository.getOrCreate(playerId);
        SkillProgress progress = profile.skill(skill);
        mult *= progress.prestigeBuff();
        double solarXp = SolarXp.multiplier();
        if (solarXp > 1.0000001d) {
            mult *= solarXp;
        }

        long amount = Math.max(0L, (long) Math.floor(rawAmount * mult));
        if (amount <= 0) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            RootSkillsXpGainEvent event = new RootSkillsXpGainEvent(player, skill, amount);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getAmount() <= 0) {
                return;
            }
            amount = event.getAmount();
        }

        int oldLevel = progress.level();
        progress.addXp(amount);
        int newLevel = formula.levelForTotalXp(progress.xp());
        if (newLevel > oldLevel) {
            progress.setLevel(newLevel);
            if (player != null) {
                for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                    Bukkit.getPluginManager().callEvent(new RootSkillsLevelUpEvent(player, skill, lvl - 1, lvl));
                }
                player.sendMessage(plugin.msg("skills.level-up")
                        .replace("{skill}", skill.key())
                        .replace("{level}", Integer.toString(newLevel)));
                if (plugin.talentManager() != null) {
                    plugin.talentManager().unlockByLevel(profile);
                }
                if (plugin.triggerBus() != null) {
                    Map<String, Object> ctx = new HashMap<>();
                    ctx.put("skill", skill.key());
                    ctx.put("level", newLevel);
                    plugin.triggerBus().fire(player, "on_level_up", ctx);
                }
            }
        }
        profile.markDirty();
        repository.saveAsync(profile);

        if (player != null) {
            long next = formula.xpToNext(progress.level());
            long floor = formula.totalXpForLevel(progress.level());
            long into = Math.max(0L, progress.xp() - floor);
            Component bar = Component.text(
                    "+" + amount + " " + skill.key() + " XP  (" + into + "/" + next + ")",
                    NamedTextColor.GOLD);
            if (solarXp > 1.0000001d) {
                bar = bar.append(Component.text(
                        "  ☀ +" + String.format(java.util.Locale.US, "%.0f%%", (solarXp - 1.0d) * 100.0d),
                        NamedTextColor.YELLOW));
            } else {
                bar = bar.append(Component.text("  ☀", NamedTextColor.DARK_GRAY));
            }
            if (!oreRadarActive(player)) {
                player.sendActionBar(bar);
            }
        }

        if (shareParty && plugin.partyManager() != null && player != null) {
            plugin.partyManager().shareXp(player, skill, amount);
        }
    }

    /** Radar owns the action bar while scanning — don't overwrite compass pings. */
    private static boolean oreRadarActive(Player player) {
        try {
            Plugin eco = Bukkit.getPluginManager().getPlugin("Root-Economy");
            if (eco == null || !eco.isEnabled()) {
                return false;
            }
            Object active = eco.getClass().getMethod("oreRadarActive", java.util.UUID.class)
                    .invoke(eco, player.getUniqueId());
            return active instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
