package com.rootrecord.minecraft.rootadmin.rails;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/** Rail footprint: support below, rail, and two clearance blocks above. */
public record RailColumn(Block support, Block rail, Block clearance1, Block clearance2) {

    public static RailColumn fromRail(Block rail) {
        return new RailColumn(
                rail.getRelative(BlockFace.DOWN),
                rail,
                rail.getRelative(BlockFace.UP),
                rail.getRelative(BlockFace.UP, 2)
        );
    }

    public List<Block> allSlots() {
        return List.of(support, rail, clearance1, clearance2);
    }

    /** Resolve the rail block when breaking support or clearance slots. */
    public static Block findRailAnchor(Block block) {
        if (RailMaterials.isRail(block.getType())) {
            return block;
        }
        for (int up = 1; up <= 2; up++) {
            Block candidate = block.getRelative(BlockFace.UP, up);
            if (RailMaterials.isRail(candidate.getType())) {
                return candidate;
            }
        }
        for (int down = 1; down <= 2; down++) {
            Block candidate = block.getRelative(BlockFace.DOWN, down);
            if (RailMaterials.isRail(candidate.getType())) {
                return candidate;
            }
        }
        return null;
    }
}
