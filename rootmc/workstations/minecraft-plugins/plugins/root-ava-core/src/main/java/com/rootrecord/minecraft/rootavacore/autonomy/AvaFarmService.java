package com.rootrecord.minecraft.rootavacore.autonomy;

import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import com.rootrecord.minecraft.rootavacore.presence.AvaPresenceService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Ava autofarm — plant / bone-meal / harvest / replant on hydrated farmland in her claim.
 * Crops: wheat, carrots, potatoes, beetroot. Also trims sugar cane 2+ tall.
 */
public final class AvaFarmService {

    private final RootAvaCorePlugin plugin;
    private final AvaStorageService storage;
    private UUID crewId = AvaPresenceService.HOME_CREW;

    public AvaFarmService(RootAvaCorePlugin plugin, AvaStorageService storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public String tick(Object claim, Location anchor, List<Block> chests, int budget) {
        return tick(claim, anchor, chests, budget, AvaPresenceService.HOME_CREW);
    }

    public String tick(Object claim, Location anchor, List<Block> chests, int budget, UUID crewId) {
        this.crewId = crewId == null ? AvaPresenceService.HOME_CREW : crewId;
        if (anchor == null || anchor.getWorld() == null || budget <= 0) {
            return "farm idle";
        }
        World world = anchor.getWorld();
        int r = 18;
        int cx = anchor.getBlockX();
        int cy = anchor.getBlockY();
        int cz = anchor.getBlockZ();
        int harvested = 0;
        int planted = 0;
        int fed = 0;
        int cane = 0;

        List<Block> farmland = new ArrayList<>();
        List<Block> mature = new ArrayList<>();
        List<Block> growing = new ArrayList<>();
        List<Block> caneTops = new ArrayList<>();

        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > r * r) continue;
                for (int y = Math.max(world.getMinHeight(), cy - 6); y <= Math.min(world.getMaxHeight() - 2, cy + 4); y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!inZone(claim, b.getLocation())) continue;
                    Material t = b.getType();
                    if (t == Material.FARMLAND) {
                        Block above = b.getRelative(BlockFace.UP);
                        if (above.getType().isAir()) {
                            farmland.add(b);
                        } else if (isCrop(above.getType())) {
                            if (isMature(above)) {
                                mature.add(above);
                            } else {
                                growing.add(above);
                            }
                        }
                    } else if (t == Material.SUGAR_CANE
                            && b.getRelative(BlockFace.DOWN).getType() == Material.SUGAR_CANE
                            && b.getRelative(BlockFace.UP).getType() != Material.SUGAR_CANE) {
                        caneTops.add(b);
                    }
                }
            }
        }

        for (Block crop : mature) {
            if (harvested + planted + fed + cane >= budget) break;
            harvestAndReplant(crop, chests, claim, anchor);
            harvested++;
        }
        for (Block soil : farmland) {
            if (harvested + planted + fed + cane >= budget) break;
            if (plantOn(soil.getRelative(BlockFace.UP), chests, claim, anchor)) {
                planted++;
            }
        }
        for (Block crop : growing) {
            if (harvested + planted + fed + cane >= budget) break;
            if (storage.countMaterial(chests, Material.BONE_MEAL) <= 0) break;
            if (crop.applyBoneMeal(BlockFace.UP) && storage.withdraw(chests, List.of(Material.BONE_MEAL), 1) > 0) {
                appear(crop.getLocation());
                fed++;
            }
        }
        for (Block top : caneTops) {
            if (harvested + planted + fed + cane >= budget) break;
            Collection<ItemStack> drops = top.getDrops(new ItemStack(Material.IRON_HOE));
            top.setType(Material.AIR, false);
            for (ItemStack drop : drops) {
                storage.deposit(drop, chests);
            }
            appear(top.getLocation());
            cane++;
        }

        if (harvested + planted + fed + cane == 0) {
            return farmland.isEmpty() && mature.isEmpty()
                    ? "farm idle — till a patch (or finish homestead farm)"
                    : "farm idle — need seeds/carrots/potatoes";
        }
        return "farm +" + planted + " plant · " + harvested + " harvest · "
                + fed + " meal · " + cane + " cane";
    }

    private void harvestAndReplant(Block crop, List<Block> chests, Object claim, Location anchor) {
        Material type = crop.getType();
        Collection<ItemStack> drops = crop.getDrops(new ItemStack(Material.IRON_HOE));
        Material seed = seedForCrop(type);
        crop.setType(Material.AIR, false);
        boolean replanted = false;
        if (seed != null) {
            List<ItemStack> rest = new ArrayList<>();
            for (ItemStack d : drops) {
                if (!replanted && d != null && d.getType() == seed && d.getAmount() > 0) {
                    d.setAmount(d.getAmount() - 1);
                    replanted = true;
                }
                if (d != null && d.getAmount() > 0) {
                    rest.add(d);
                }
            }
            if (!replanted && storage.withdraw(chests, List.of(seed), 1) > 0) {
                replanted = true;
            }
            if (replanted) {
                crop.setType(type, false);
                if (crop.getBlockData() instanceof Ageable age) {
                    age.setAge(0);
                    crop.setBlockData(age, false);
                }
            }
            for (ItemStack d : rest) {
                ItemStack left = storage.deposit(d, chests);
                if (left != null && left.getAmount() > 0) {
                    Location dropAt = !chests.isEmpty()
                            ? chests.get(0).getLocation().add(0.5, 0.2, 1)
                            : anchor;
                    if (dropAt.getWorld() != null) {
                        dropAt.getWorld().dropItemNaturally(dropAt, left);
                    }
                }
            }
        } else {
            for (ItemStack d : drops) {
                storage.deposit(d, chests);
            }
        }
        appear(crop.getLocation());
    }

    private boolean plantOn(Block air, List<Block> chests, Object claim, Location anchor) {
        if (!air.getType().isAir()) return false;
        Material[][] options = {
                {Material.WHEAT_SEEDS, Material.WHEAT},
                {Material.CARROT, Material.CARROTS},
                {Material.POTATO, Material.POTATOES},
                {Material.BEETROOT_SEEDS, Material.BEETROOTS},
        };
        for (Material[] pair : options) {
            if (storage.withdraw(chests, List.of(pair[0]), 1) > 0) {
                air.setType(pair[1], false);
                appear(air.getLocation());
                return true;
            }
        }
        return false;
    }

    private static boolean isCrop(Material type) {
        return type == Material.WHEAT
                || type == Material.CARROTS
                || type == Material.POTATOES
                || type == Material.BEETROOTS;
    }

    private static boolean isMature(Block crop) {
        if (!(crop.getBlockData() instanceof Ageable age)) return false;
        return age.getAge() >= age.getMaximumAge();
    }

    private static Material seedForCrop(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            default -> null;
        };
    }

    private boolean inZone(Object claim, Location at) {
        if (claim == null || at == null) return false;
        try {
            return Boolean.TRUE.equals(claim.getClass().getMethod("contains", Location.class).invoke(claim, at));
        } catch (Throwable t) {
            return true;
        }
    }

    private void appear(Location at) {
        AvaPresenceService presence = plugin.presence();
        if (presence != null) {
            presence.appearWorking(crewId, at);
        }
    }
}
