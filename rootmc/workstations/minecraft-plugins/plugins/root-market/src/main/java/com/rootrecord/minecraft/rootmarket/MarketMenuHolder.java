package com.rootrecord.minecraft.rootmarket;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

public final class MarketMenuHolder implements InventoryHolder {

    public enum Mode {
        CATEGORIES,
        HUB,
        ITEM,
        PLAYER
    }

    private final UUID viewerId;
    private final Mode mode;
    private final String itemKey;
    private final String ownerUuid;
    private final String ownerName;
    private final String categoryId;
    private final int page;
    private final List<String> keysOrIds;
    private Inventory inventory;

    private MarketMenuHolder(
            UUID viewerId,
            Mode mode,
            String itemKey,
            String ownerUuid,
            String ownerName,
            String categoryId,
            int page,
            List<String> keysOrIds) {
        this.viewerId = viewerId;
        this.mode = mode;
        this.itemKey = itemKey;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.categoryId = categoryId;
        this.page = page;
        this.keysOrIds = List.copyOf(keysOrIds);
    }

    public static MarketMenuHolder categories(UUID viewerId, List<String> categoryIds) {
        return new MarketMenuHolder(viewerId, Mode.CATEGORIES, null, null, null, null, 0, categoryIds);
    }

    public static MarketMenuHolder hub(UUID viewerId, int page, List<String> itemKeys, String categoryId) {
        return new MarketMenuHolder(viewerId, Mode.HUB, null, null, null, categoryId, page, itemKeys);
    }

    public static MarketMenuHolder item(
            UUID viewerId, String itemKey, int page, List<String> shopIds, String categoryId) {
        return new MarketMenuHolder(viewerId, Mode.ITEM, itemKey, null, null, categoryId, page, shopIds);
    }

    public static MarketMenuHolder player(
            UUID viewerId, String ownerUuid, String ownerName, int page, List<String> shopIds) {
        return new MarketMenuHolder(viewerId, Mode.PLAYER, null, ownerUuid, ownerName, null, page, shopIds);
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public Mode mode() {
        return mode;
    }

    public String itemKey() {
        return itemKey;
    }

    public String ownerUuid() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerName;
    }

    public String categoryId() {
        return categoryId;
    }

    public MarketCategory category() {
        return MarketCategory.fromId(categoryId);
    }

    public int page() {
        return page;
    }

    public List<String> keysOrIds() {
        return keysOrIds;
    }
}
