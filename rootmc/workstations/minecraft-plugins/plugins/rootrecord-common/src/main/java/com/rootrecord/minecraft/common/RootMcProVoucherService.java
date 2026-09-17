package com.rootrecord.minecraft.common;

import org.bukkit.inventory.ItemStack;

/** Soft SPI: Root-Memberships registers; RootMC vault claim uses it for Pro vouchers. */
public interface RootMcProVoucherService {

    ItemStack createVoucher(String tier, String voucherId);

    boolean isProVoucher(ItemStack stack);

    String voucherIdOf(ItemStack stack);

    String tierOf(ItemStack stack);
}
