package com.rootrecord.minecraft.common;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Transfers registered bond ownership when the physical certificate changes hands. */
public interface RootMcBondTransferService {

    boolean isBondCertificate(ItemStack stack);

    /** Register bonded notes in stacks to {@code newOwner}. Returns count transferred. */
    int transferCertificates(Player newOwner, ItemStack... stacks);

    /** Register bonded notes to an owner who may be offline (e.g. buy-shop restock). */
    int transferCertificatesTo(java.util.UUID newOwnerUuid, String newOwnerName, ItemStack... stacks);
}
