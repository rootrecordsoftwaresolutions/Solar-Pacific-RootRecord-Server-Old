package com.rootrecord.minecraft.rootskills.engine;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.talents.TalentDefinition;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EffectRunner {

    private final RootSkillsPlugin plugin;

    public EffectRunner(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void run(Player player, List<Map<String, Object>> effects, Map<String, Object> context) {
        run(player, effects, context, null);
    }

    public void run(
            Player player,
            List<Map<String, Object>> effects,
            Map<String, Object> context,
            TalentDefinition talent) {
        if (player == null || effects == null || effects.isEmpty()) {
            return;
        }
        int rank = 1;
        if (talent != null) {
            rank = Math.max(1, plugin.repository().getOrCreate(player.getUniqueId()).talent(talent.id()).rank());
        }
        for (Map<String, Object> effect : effects) {
            apply(player, effect, context, rank, talent);
        }
    }

    private void apply(
            Player player,
            Map<String, Object> effect,
            Map<String, Object> context,
            int rank,
            TalentDefinition talent) {
        String id = String.valueOf(effect.getOrDefault("id", "")).trim().toUpperCase(Locale.ROOT)
                .replace('-', '_');
        switch (id) {
            case "MESSAGE" -> player.sendMessage(plugin.colorize(String.valueOf(effect.getOrDefault("text", ""))));
            case "SOUND" -> {
                try {
                    Sound sound = Sound.valueOf(String.valueOf(effect.getOrDefault("sound", "ENTITY_PLAYER_LEVELUP")));
                    player.playSound(player.getLocation(), sound, 1f, 1f);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "PARTICLE" -> {
                try {
                    Particle particle = Particle.valueOf(String.valueOf(effect.getOrDefault("particle", "CRIT")));
                    Location loc = player.getLocation().add(0, 1, 0);
                    player.getWorld().spawnParticle(particle, loc, toInt(effect.get("count"), 20), 0.4, 0.5, 0.4, 0.01);
                } catch (IllegalArgumentException ignored) {
                }
            }
            case "POTION" -> {
                PotionEffectType type = PotionEffectType.getByName(
                        String.valueOf(effect.getOrDefault("potion", "SPEED")));
                if (type != null) {
                    int duration = toInt(effect.get("duration"), 100);
                    int amplifier = toInt(effect.get("amplifier"), 0);
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                }
            }
            case "DAMAGE_BONUS" -> {
                double amount = toDouble(effect.get("amount"), toDouble(effect.get("amount-per-rank"), 1.0) * rank);
                if (plugin.abilitySessions() != null) {
                    plugin.abilitySessions().setFlag(player.getUniqueId(), "damage_bonus", amount, 5000L);
                }
            }
            case "DECREASE_DAMAGE", "DAMAGE_REDUCTION" -> {
                double amount = toDouble(effect.get("amount"), 0.1);
                if (plugin.abilitySessions() != null) {
                    plugin.abilitySessions().setFlag(player.getUniqueId(), "decrease_damage", amount, 5000L);
                }
            }
            case "HEAL" -> {
                double amount = toDouble(effect.get("amount"), 2.0);
                double max = player.getAttribute(Attribute.MAX_HEALTH) != null
                        ? player.getAttribute(Attribute.MAX_HEALTH).getValue()
                        : 20.0;
                player.setHealth(Math.min(max, player.getHealth() + amount));
            }
            case "GIVE_ITEM_DURABILITY" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand != null && hand.hasItemMeta()) {
                    ItemMeta meta = hand.getItemMeta();
                    if (meta instanceof Damageable d) {
                        int repair = toInt(effect.get("amount"), 10);
                        d.setDamage(Math.max(0, d.getDamage() - repair));
                        hand.setItemMeta(meta);
                    }
                }
            }
            case "BREAK_BONUS", "DOUBLE_DROP" -> {
                if (plugin.abilitySessions() != null) {
                    long dur = talent != null ? talent.durationSeconds() * 1000L : 10_000L;
                    plugin.abilitySessions().start(player.getUniqueId(), "break_bonus", Math.max(1000L, dur));
                }
            }
            case "SPEED_BURST", "SPEED", "HASTE" -> {
                int duration = toInt(effect.get("duration"),
                        talent != null ? talent.durationSeconds() * 20 : 100);
                int amp = toInt(effect.get("amplifier"), 1);
                PotionEffectType type = id.contains("HASTE") ? PotionEffectType.HASTE : PotionEffectType.SPEED;
                player.addPotionEffect(new PotionEffect(type, duration, amp));
            }
            case "SUPER_BREAKER", "GIGA_DRILL", "TREE_FELLER", "SERRATED_STRIKES" -> {
                long dur = talent != null ? talent.durationSeconds() * 1000L : 15_000L;
                if (plugin.abilitySessions() != null) {
                    plugin.abilitySessions().start(player.getUniqueId(), id.toLowerCase(Locale.ROOT), dur);
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, (int) (dur / 50), 2));
                player.sendMessage(plugin.colorize("&eAbility active: &6" + id.toLowerCase(Locale.ROOT)));
            }
            case "XP_BONUS" -> {
                double multiplier = toDouble(effect.get("multiplier"), 1.1);
                if (plugin.abilitySessions() != null) {
                    plugin.abilitySessions().setFlag(player.getUniqueId(), "xp_bonus", multiplier, 10_000L);
                }
            }
            case "MANA_RESTORE" -> plugin.manaManager().restore(
                    plugin.repository().getOrCreate(player.getUniqueId()),
                    toDouble(effect.get("amount"), 10));
            default -> plugin.getLogger().fine("Unknown effect: " + id);
        }
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(o.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
