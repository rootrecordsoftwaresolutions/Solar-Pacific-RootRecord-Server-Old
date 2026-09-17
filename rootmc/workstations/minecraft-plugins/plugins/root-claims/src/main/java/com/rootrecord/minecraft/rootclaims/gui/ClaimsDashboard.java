package com.rootrecord.minecraft.rootclaims.gui;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.rootclaims.ClaimKey;
import com.rootrecord.minecraft.rootclaims.ClaimRecord;
import com.rootrecord.minecraft.rootclaims.ClaimService;
import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClaimsDashboard {

    public static final int SLOT_OVERVIEW = 4;
    public static final int SLOT_WALLET = 10;
    public static final int SLOT_BANKS = 12;
    public static final int SLOT_BLOCKS = 14;
    public static final int SLOT_HERE = 16;
    public static final int SLOT_CLAIM_START = 19;
    public static final int SLOT_CLAIM_END = 43;
    public static final int SLOT_NEW_CLAIM = 45;
    public static final int SLOT_LINES = 47;
    public static final int SLOT_SPAWN = 49;
    public static final int SLOT_CHEST = 51;
    public static final int SLOT_SETTINGS = 53;

    private final RootClaimsPlugin plugin;
    private final ClaimService claims;

    public ClaimsDashboard(RootClaimsPlugin plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    public void open(Player player) {
        if (!plugin.enabledFlag()) {
            player.sendMessage(plugin.msg("disabled"));
            return;
        }
        List<ClaimRecord> owned = claims.ownedBy(player.getUniqueId());
        double bankTotal = 0;
        long claimedBlocks = 0;
        List<ClaimRecord> rootsForBank = claims.rootsOwnedBy(player.getUniqueId());
        for (ClaimRecord root : rootsForBank) {
            bankTotal += Math.max(0, claims.bankBalance(root));
        }
        for (ClaimRecord claim : owned) {
            claimedBlocks += claimedBlocksFor(claim);
        }
        double wallet = 0;
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy != null) {
            try {
                wallet = economy.balance(player.getUniqueId());
            } catch (RuntimeException ignored) {
                wallet = 0;
            }
        }
        double nextPrice = claims.nextClaimPrice(player.getUniqueId());
        ClaimRecord here = claims.claimAt(player);
        ClaimRecord territory = here == null ? claims.territoryAt(player) : null;

        ClaimsDashboardHolder holder = new ClaimsDashboardHolder(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.colorize("&8Claims"));
        holder.bind(inv);

        inv.setItem(SLOT_OVERVIEW, item(
                Material.FILLED_MAP,
                "&bArea overview",
                "&7Owner: &f" + player.getName(),
                "&7Areas: &f" + claims.rootsOwnedBy(player.getUniqueId()).size()
                        + "&7 / &f" + plugin.maxAreasPerPlayer(),
                "&7Circles: &f" + owned.size() + "&7 / &f" + plugin.maxClaimsPerPlayer(),
                "&7Next: &f" + formatNextPrice(nextPrice),
                "",
                "&8Area banks fund expansions."));

        inv.setItem(SLOT_WALLET, item(
                Material.GOLD_INGOT,
                "&6Wallet",
                "&7Balance: &f" + GoldMoney.format(wallet) + " G",
                "&7Founding an area uses wallet Gold.",
                "&7Expansions use the area bank."));

        inv.setItem(SLOT_BANKS, item(
                Material.BARREL,
                "&6Area banks",
                "&7Total across your areas: &f" + GoldMoney.format(bankTotal) + " G",
                "&7Deposit: &f/c bank deposit <amount>"));

        inv.setItem(SLOT_BLOCKS, item(
                Material.GRASS_BLOCK,
                "&aClaimed area",
                "&7Approx. protected blocks: &f" + String.format(Locale.US, "%,d", claimedBlocks),
                "&7(circle area Σ π|r² per claim)",
                "&7Anchor radius default: &f" + plugin.anchorRadiusBlocks()));

        if (here != null) {
            inv.setItem(SLOT_HERE, item(
                    Material.COMPASS,
                    "&eYou are here",
                    "&7Inside &f" + here.ownerName() + "&7's claim",
                    "&7Anchor: &f" + here.key().world() + " " + here.key().x() + ", " + here.key().y() + ", " + here.key().z(),
                    "&7Radius: &f" + here.radiusBlocks(),
                    "&7Bank: &f" + GoldMoney.format(claims.bankBalance(here)) + " G",
                    "&7Mobs: &f" + (here.mobsAllowed() ? "on" : "off"),
                    "&7Spawn: &f" + (here.spawnPublic() ? "public" : "private")));
        } else if (territory != null) {
            inv.setItem(SLOT_HERE, item(
                    Material.RECOVERY_COMPASS,
                    "&eTerritory band",
                    "&7Near &f" + territory.ownerName() + "&7's claim",
                    "&7Buffer: &f+" + plugin.territoryBufferBlocks() + " &7blocks",
                    "&7Wilderness - no build protection"));
        } else {
            inv.setItem(SLOT_HERE, item(
                    Material.COMPASS,
                    "&7Wilderness",
                    "&7Not inside a claim or territory band.",
                    "&7Use &fNew claim &7to place an anchor."));
        }

        int slot = SLOT_CLAIM_START;
        int index = 1;
        List<ClaimRecord> roots = claims.rootsOwnedBy(player.getUniqueId());
        for (ClaimRecord root : roots) {
            if (slot > SLOT_CLAIM_END) {
                break;
            }
            holder.putClaimSlot(slot, root.key());
            boolean hereClaim = here != null && claims.areaRoot(here).key().equals(root.key());
            int level = claims.areaLevel(root);
            inv.setItem(slot, item(
                    hereClaim ? Material.BEACON : Material.OAK_SIGN,
                    "&a" + root.areaNameOrOwner(),
                    "&7Level: &f" + level + "/" + plugin.maxLevelPerArea(),
                    "&7World: &f" + root.key().world(),
                    "&7Coords: &f" + root.key().x() + ", " + root.key().y() + ", " + root.key().z(),
                    "&7Bank: &f" + GoldMoney.format(claims.bankBalance(root)) + " G",
                    "&7Visitor spawn: &f" + (root.spawnPublic() ? "public" : "private"),
                    root.hasCustomSpawn() ? "&7Spawn point: &acustom" : "&7Spawn point: &8anchor",
                    "",
                    "&eLeft-click &7-> teleport to area spawn",
                    "&eRight-click &7-> area settings"));
            slot++;
            index++;
        }
        if (roots.isEmpty()) {
            inv.setItem(SLOT_CLAIM_START, item(
                    Material.BARRIER,
                    "&cNo areas yet",
                    "&7Click &fNew area &7while standing",
                    "&7where you want your first area."));
        } else if (roots.size() > (SLOT_CLAIM_END - SLOT_CLAIM_START + 1)) {
            inv.setItem(SLOT_CLAIM_END, item(
                    Material.PAPER,
                    "&e+" + (roots.size() - (SLOT_CLAIM_END - SLOT_CLAIM_START + 1)) + " more",
                    "&7Use &f/c list &7for the full list."));
        }

        inv.setItem(SLOT_NEW_CLAIM, item(
                Material.GOLDEN_SHOVEL,
                roots.isEmpty() ? "&aNew area (free)" : "&aExpand / new",
                "&7Close this menu and confirm in chat.",
                "&7/c new &7found area · &f/c claim &7expand",
                "&7Price: &f" + formatNextPrice(nextPrice)));
        inv.setItem(SLOT_LINES, item(
                Material.END_CRYSTAL,
                "&dClaim lines",
                "&7Toggle ground-level outer claim rim.",
                "&8Overlapping claims blend as one.",
                "&8Also: &f/c lines"));
        inv.setItem(SLOT_SPAWN, item(
                Material.ENDER_PEARL,
                "&bArea spawn",
                "&7Teleport to your area spawn.",
                "&8Also: &f/spawn &7or &f/c spawn",
                "&8Visit others: &f/spawn <name>",
                "&8(when their spawn is public)"));
        inv.setItem(SLOT_CHEST, item(
                Material.CHEST,
                "&6Community chest",
                "&7Shared 27-slot chest for area",
                "&7members.",
                "&7Open anywhere inside the claim.",
                "&8Also: &f/c chest"));
        inv.setItem(SLOT_SETTINGS, item(
                Material.COMPARATOR,
                "&eArea settings",
                "&7Toggle public / private spawn",
                "&7and hostile mobs for this area.",
                "&7Stand in your area, or right-click",
                "&7an area icon above.",
                "&8Also: &f/c toggle spawn <on|off>"));

        player.openInventory(inv);
    }

    public ClaimRecord settingsTarget(Player player) {
        ClaimRecord here = claims.ownedClaimAt(player);
        if (here != null) {
            return here;
        }
        ClaimRecord managed = claims.claimAt(player);
        if (managed != null && claims.canManage(player, managed, false)) {
            return managed;
        }
        return claims.ownedSpawnClaim(player);
    }

    public ClaimRecord claimForKey(Player player, ClaimKey key) {
        for (ClaimRecord owned : claims.ownedBy(player.getUniqueId())) {
            if (owned.key().equals(key)) {
                return owned;
            }
        }
        return claims.claimByKey(key);
    }

    public void teleportToClaim(Player player, ClaimKey key) {
        ClaimRecord claim = null;
        for (ClaimRecord owned : claims.ownedBy(player.getUniqueId())) {
            if (owned.key().equals(key)) {
                claim = owned;
                break;
            }
        }
        if (claim == null) {
            player.sendMessage(plugin.msg("claim-spawn-none"));
            return;
        }
        var target = claims.spawnLocation(claim);
        if (target == null) {
            player.sendMessage(plugin.msg("claim-spawn-missing-world"));
            return;
        }
        player.closeInventory();
        player.teleport(target);
        player.sendMessage(plugin.msg("claim-spawn-success").replace("{owner}", claim.ownerName()));
    }

    static long claimedBlocksFor(ClaimRecord claim) {
        int r = Math.max(1, claim.radiusBlocks());
        return Math.round(Math.PI * (double) r * (double) r);
    }

    private static String formatNextPrice(double nextPrice) {
        if (nextPrice <= 0) {
            return "Free";
        }
        return GoldMoney.format(nextPrice) + " G";
    }

    private ItemStack item(Material material, String title, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(plugin.colorize(title));
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(plugin.colorize(line == null ? "" : line));
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
