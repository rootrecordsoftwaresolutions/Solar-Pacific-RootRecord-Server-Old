package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.block.Block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks players entering shop prices or edits in chat. */
public final class ShopInputManager {

    public enum Kind {
        CREATE_PRICE,
        EDIT_PRICE,
        EDIT_SALE_QTY,
        VIRTUAL_LIST_PRICE
    }

    public record Pending(
            Kind kind,
            String world,
            int chestX,
            int chestY,
            int chestZ,
            int signX,
            int signY,
            int signZ,
            String itemKey,
            int saleQty,
            String listingId,
            long binId) {

        static Pending create(
                String world,
                int chestX,
                int chestY,
                int chestZ,
                int signX,
                int signY,
                int signZ,
                String itemKey,
                int saleQty) {
            return new Pending(
                    Kind.CREATE_PRICE,
                    world,
                    chestX,
                    chestY,
                    chestZ,
                    signX,
                    signY,
                    signZ,
                    itemKey,
                    saleQty,
                    null,
                    0L);
        }

        static Pending editPrice(ShopListing shop) {
            return new Pending(
                    Kind.EDIT_PRICE,
                    shop.world(),
                    shop.x(),
                    shop.y(),
                    shop.z(),
                    shop.signX(),
                    shop.signY(),
                    shop.signZ(),
                    shop.itemKey(),
                    shop.saleQty(),
                    shop.id(),
                    0L);
        }

        static Pending editSaleQty(ShopListing shop) {
            return new Pending(
                    Kind.EDIT_SALE_QTY,
                    shop.world(),
                    shop.x(),
                    shop.y(),
                    shop.z(),
                    shop.signX(),
                    shop.signY(),
                    shop.signZ(),
                    shop.itemKey(),
                    shop.saleQty(),
                    shop.id(),
                    0L);
        }

        public static Pending virtualList(long binId, int qty, String itemKey) {
            return new Pending(
                    Kind.VIRTUAL_LIST_PRICE,
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    itemKey,
                    qty,
                    null,
                    binId);
        }

        Block chestBlock(org.bukkit.World w) {
            return w == null ? null : w.getBlockAt(chestX, chestY, chestZ);
        }

        Block signBlock(org.bukkit.World w) {
            return w == null ? null : w.getBlockAt(signX, signY, signZ);
        }
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void put(UUID playerId, Pending session) {
        pending.put(playerId, session);
    }

    public Pending remove(UUID playerId) {
        return pending.remove(playerId);
    }

    public Pending get(UUID playerId) {
        return pending.get(playerId);
    }

    public boolean has(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public void clear(UUID playerId) {
        pending.remove(playerId);
    }
}
