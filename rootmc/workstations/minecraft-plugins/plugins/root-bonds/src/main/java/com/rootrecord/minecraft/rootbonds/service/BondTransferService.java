package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.common.RootMcBondTransferService;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.item.BondCertificate;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Level;

/** Registers bond ownership when the physical certificate changes hands. */
public final class BondTransferService implements RootMcBondTransferService {

    private final RootBondsPlugin plugin;
    private final BondService bonds;

    public BondTransferService(RootBondsPlugin plugin, BondService bonds) {
        this.plugin = plugin;
        this.bonds = bonds;
    }

    @Override
    public boolean isBondCertificate(ItemStack stack) {
        return bonds != null && bonds.certificates().readBondId(stack) != null;
    }

    @Override
    public int transferCertificates(Player newOwner, ItemStack... stacks) {
        if (newOwner == null || stacks == null) {
            return 0;
        }
        return transferCertificatesTo(newOwner.getUniqueId(), newOwner.getName(), stacks);
    }

    public int registerCertificatesInInventory(Player owner, ItemStack... stacks) {
        if (owner == null || stacks == null) {
            return 0;
        }
        return transferCertificatesTo(owner.getUniqueId(), owner.getName(), stacks);
    }

    @Override
    public int transferCertificatesTo(UUID newOwnerUuid, String newOwnerName, ItemStack... stacks) {
        if (!bonds.enabled() || newOwnerUuid == null || newOwnerName == null || newOwnerName.isBlank() || stacks == null) {
            return 0;
        }
        BondCertificate certificates = bonds.certificates();
        int transferred = 0;
        for (ItemStack stack : stacks) {
            UUID bondId = certificates.readBondId(stack);
            if (bondId == null) {
                continue;
            }
            if (transferOwner(bondId, newOwnerUuid, newOwnerName)) {
                transferred++;
            }
        }
        if (transferred > 0) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> plugin.cloudSync().syncSnapshot(false));
        }
        return transferred;
    }

    boolean transferOwner(UUID bondId, UUID newOwnerUuid, String newOwnerName) {
        try {
            if (bonds.store().transferOwner(bondId, newOwnerUuid, newOwnerName)) {
                return true;
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond transfer failed for " + bondId + ": " + ex.getMessage(), ex);
        }
        return false;
    }
}
