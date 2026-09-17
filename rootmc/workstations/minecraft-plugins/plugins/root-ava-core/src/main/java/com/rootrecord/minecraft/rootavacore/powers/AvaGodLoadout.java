package com.rootrecord.minecraft.rootavacore.powers;

import com.rootrecord.minecraft.rootavacore.AvaConfig;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Autonomous Ava_Ivy loadout — full netherite + permanent haste/speed.
 */
public final class AvaGodLoadout {

    private final RootAvaCorePlugin plugin;
    private BukkitTask task;

    public AvaGodLoadout(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void applyNow() {
        Player ava = findAva();
        if (ava != null) {
            apply(ava);
        }
    }

    private void tick() {
        if (!plugin.powers().playEnabled()) return;
        AvaConfig.PowersConfig cfg = plugin.config().powers();
        if (!cfg.godGear() && !cfg.permanentBuffs()) return;
        Player ava = findAva();
        if (ava == null) return;
        apply(ava);
    }

    private Player findAva() {
        AvaConfig.PowersConfig cfg = plugin.config().powers();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (cfg.isAvaPlayer(p.getUniqueId(), p.getName())) {
                return p;
            }
        }
        return null;
    }

    public void apply(Player player) {
        if (player == null) return;
        AvaConfig.PowersConfig cfg = plugin.config().powers();
        if (cfg.permanentBuffs()) {
            int haste = Math.max(0, cfg.hasteAmplifier());
            int speed = Math.max(0, cfg.speedAmplifier());
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 220, haste, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 220, speed, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, true));
        }
        if (!cfg.godGear()) return;
        PlayerInventory inv = player.getInventory();
        ensureArmor(inv);
        ensureTools(inv);
        player.updateInventory();
    }

    private void ensureArmor(PlayerInventory inv) {
        if (needsReplace(inv.getHelmet(), Material.NETHERITE_HELMET)) {
            inv.setHelmet(enchanted(Material.NETHERITE_HELMET, true));
        }
        if (needsReplace(inv.getChestplate(), Material.NETHERITE_CHESTPLATE)) {
            inv.setChestplate(enchanted(Material.NETHERITE_CHESTPLATE, true));
        }
        if (needsReplace(inv.getLeggings(), Material.NETHERITE_LEGGINGS)) {
            inv.setLeggings(enchanted(Material.NETHERITE_LEGGINGS, true));
        }
        if (needsReplace(inv.getBoots(), Material.NETHERITE_BOOTS)) {
            inv.setBoots(enchanted(Material.NETHERITE_BOOTS, true));
        }
    }

    private void ensureTools(PlayerInventory inv) {
        ensureHotbarTool(inv, Material.NETHERITE_PICKAXE, 0);
        ensureHotbarTool(inv, Material.NETHERITE_SHOVEL, 1);
        ensureHotbarTool(inv, Material.NETHERITE_AXE, 2);
        ensureHotbarTool(inv, Material.NETHERITE_SWORD, 3);
    }

    private void ensureHotbarTool(PlayerInventory inv, Material type, int slot) {
        ItemStack cur = inv.getItem(slot);
        if (!needsReplace(cur, type)) return;
        inv.setItem(slot, enchanted(type, false));
    }

    private static boolean needsReplace(ItemStack stack, Material want) {
        if (stack == null || stack.getType().isAir()) return true;
        return stack.getType() != want;
    }

    private static ItemStack enchanted(Material type, boolean armor) {
        ItemStack stack = new ItemStack(type);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.setDisplayName("§dAva · God " + pretty(type));
            stack.setItemMeta(meta);
        }
        if (armor) {
            stack.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
            stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            stack.addUnsafeEnchantment(Enchantment.MENDING, 1);
            if (type == Material.NETHERITE_BOOTS) {
                stack.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 4);
                stack.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 3);
            }
            if (type == Material.NETHERITE_HELMET) {
                stack.addUnsafeEnchantment(Enchantment.RESPIRATION, 3);
                stack.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
            }
        } else if (type == Material.NETHERITE_PICKAXE) {
            stack.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
            stack.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
            stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            stack.addUnsafeEnchantment(Enchantment.MENDING, 1);
        } else if (type == Material.NETHERITE_SHOVEL || type == Material.NETHERITE_AXE) {
            stack.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
            stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            stack.addUnsafeEnchantment(Enchantment.MENDING, 1);
        } else if (type == Material.NETHERITE_SWORD) {
            stack.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            stack.addUnsafeEnchantment(Enchantment.LOOTING, 3);
            stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            stack.addUnsafeEnchantment(Enchantment.MENDING, 1);
        }
        return stack;
    }

    private static String pretty(Material type) {
        String n = type.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
