package com.rootrecord.minecraft.roothelp.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/** /offer bread — 8 stacks of bread → Ava Potion (90s Resistance, Regeneration, Strength). */
public final class OfferCommand implements CommandExecutor {

    public static final int BREAD_REQUIRED = 8 * 64;
    private static final int DURATION_TICKS = 90 * 20;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("bread")) {
            player.sendMessage("§7Usage: §f/offer bread §7· 8 stacks of bread → Ava Potion");
            return true;
        }
        PlayerInventory inv = player.getInventory();
        if (countMaterial(inv, Material.BREAD) < BREAD_REQUIRED) {
            player.sendMessage("§cNeed §f8 stacks of bread §c(512) to offer.");
            return true;
        }
        removeMaterial(inv, Material.BREAD, BREAD_REQUIRED);
        ItemStack potion = avaPotion();
        var overflow = inv.addItem(potion);
        if (!overflow.isEmpty()) {
            for (ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        player.sendMessage("§aOffered 8 stacks of bread · received §fAva Potion§a.");
        return true;
    }

    private static ItemStack avaPotion() {
        ItemStack stack = new ItemStack(Material.POTION, 1);
        if (!(stack.getItemMeta() instanceof PotionMeta meta)) {
            return stack;
        }
        meta.displayName(Component.text("Ava Potion", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        try {
            meta.setBasePotionType(PotionType.WATER);
        } catch (Throwable ignored) {
            // older PotionMeta
        }
        meta.addCustomEffect(new PotionEffect(PotionEffectType.RESISTANCE, DURATION_TICKS, 0, true, true, true), true);
        meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, DURATION_TICKS, 0, true, true, true), true);
        meta.addCustomEffect(new PotionEffect(PotionEffectType.STRENGTH, DURATION_TICKS, 0, true, true, true), true);
        stack.setItemMeta(meta);
        return stack;
    }

    private static int countMaterial(PlayerInventory inv, Material type) {
        int n = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == type) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    private static void removeMaterial(PlayerInventory inv, Material type, int amount) {
        int need = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && need > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != type) {
                continue;
            }
            int use = Math.min(need, stack.getAmount());
            stack.setAmount(stack.getAmount() - use);
            if (stack.getAmount() <= 0) {
                inv.setItem(i, null);
            }
            need -= use;
        }
    }
}
