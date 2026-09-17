package com.rootrecord.minecraft.rootgamble;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** Ava_Ivy coin opponent — wallet is the Server Reserve. */
public final class AvaGamble {

    public static final UUID AVA_UUID = UUID.fromString("78c3de61-0fd6-4800-9eda-cc178eaae34b");
    public static final String AVA_NAME = "Ava_Ivy";

    private AvaGamble() {}

    public static boolean isAvaName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return "ava".equals(n) || "ava_ivy".equals(n);
    }

    public static boolean isAva(Player player) {
        if (player == null) {
            return false;
        }
        if (AVA_UUID.equals(player.getUniqueId())) {
            return true;
        }
        return isAvaName(player.getName());
    }
}
