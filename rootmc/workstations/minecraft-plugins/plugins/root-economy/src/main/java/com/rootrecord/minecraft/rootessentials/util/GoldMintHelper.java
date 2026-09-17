package com.rootrecord.minecraft.rootessentials.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Wallet G ↔ gold items at the fixed mint peg (block=9G, ingot=1G, nugget=1/9 G). */
public final class GoldMintHelper {

    private static final int NUGGETS_PER_INGOT = 9;
    private static final int NUGGETS_PER_BLOCK = 81;

    private GoldMintHelper() {}

    /** Whole nugget-units affordably from wallet G (fractions below 1/9 G are not mintable). */
    public static int toNuggetUnits(double gold) {
        if (gold <= 0) {
            return 0;
        }
        return (int) Math.floor(gold * NUGGETS_PER_INGOT + 1e-9);
    }

    public static double goldCost(int nuggetUnits) {
        return nuggetUnits / (double) NUGGETS_PER_INGOT;
    }

    public record Condensed(int blocks, int ingots, int nuggets) {
        public int nuggetUnits() {
            return blocks * NUGGETS_PER_BLOCK + ingots * NUGGETS_PER_INGOT + nuggets;
        }
    }

    public static Condensed condense(int nuggetUnits) {
        int blocks = nuggetUnits / NUGGETS_PER_BLOCK;
        int rem = nuggetUnits % NUGGETS_PER_BLOCK;
        int ingots = rem / NUGGETS_PER_INGOT;
        int nuggets = rem % NUGGETS_PER_INGOT;
        return new Condensed(blocks, ingots, nuggets);
    }

    public static List<ItemStack> toItemStacks(Condensed condensed) {
        List<ItemStack> stacks = new ArrayList<>();
        appendStacks(stacks, Material.GOLD_BLOCK, condensed.blocks());
        appendStacks(stacks, Material.GOLD_INGOT, condensed.ingots());
        appendStacks(stacks, Material.GOLD_NUGGET, condensed.nuggets());
        return stacks;
    }

    public static boolean fitsInStorage(Inventory inv, int nuggetUnits) {
        if (nuggetUnits <= 0) {
            return true;
        }
        Inventory test = Bukkit.createInventory(null, 36);
        ItemStack[] storage = inv.getStorageContents();
        test.setContents(storage == null ? new ItemStack[36] : storage.clone());
        ItemStack[] stacks = toItemStacks(condense(nuggetUnits)).toArray(ItemStack[]::new);
        return test.addItem(stacks).isEmpty();
    }

    public static int maxUnitsForStorage(Inventory inv, int walletUnits) {
        if (walletUnits <= 0) {
            return 0;
        }
        int lo = 0;
        int hi = walletUnits;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (fitsInStorage(inv, mid)) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static void appendStacks(List<ItemStack> out, Material material, int count) {
        while (count > 0) {
            int stackSize = Math.min(64, count);
            out.add(new ItemStack(material, stackSize));
            count -= stackSize;
        }
    }
}
