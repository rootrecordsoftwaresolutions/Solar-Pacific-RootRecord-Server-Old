package com.rootrecord.minecraft.rootbonds.gui;

public record BondsMenuSession(BondsMenuHolder holder) {

    public boolean isGoldSlot(int rawSlot, int goldStart, int goldSlots) {
        return rawSlot >= goldStart && rawSlot < goldStart + goldSlots;
    }
}
