package com.rootrecord.minecraft.rootadmin.rails;

import org.bukkit.Material;
import org.bukkit.Tag;

public final class RailMaterials {

    private RailMaterials() {}

    public static boolean isRail(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        return Tag.RAILS.isTagged(material);
    }
}
