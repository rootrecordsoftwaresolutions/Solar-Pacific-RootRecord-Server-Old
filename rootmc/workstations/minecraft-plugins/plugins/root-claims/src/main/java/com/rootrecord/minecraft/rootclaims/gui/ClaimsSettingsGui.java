package com.rootrecord.minecraft.rootclaims.gui;

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

/** Per-claim settings (spawn open/private, mobs). */
public final class ClaimsSettingsGui {

    public static final int SLOT_PUBLIC_SPAWN = 11;
    public static final int SLOT_MOBS = 13;
    public static final int SLOT_INFO = 4;
    public static final int SLOT_BACK = 22;

    private final RootClaimsPlugin plugin;
    private final ClaimService claims;

    public ClaimsSettingsGui(RootClaimsPlugin plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    public void open(Player player, ClaimRecord claim) {
        if (claim == null) {
            player.sendMessage(plugin.msg("settings-no-claim"));
            return;
        }
        ClaimRecord root = claims.areaRoot(claim);
        if (!claims.canManage(player, root, false)) {
            player.sendMessage(plugin.msg("not-owner").replace("{owner}", root.ownerName()));
            return;
        }
        ClaimsSettingsHolder holder = new ClaimsSettingsHolder(player.getUniqueId(), root.key());
        Inventory inv = Bukkit.createInventory(holder, 27, plugin.colorize("&8Area settings"));
        holder.bind(inv);
        paint(inv, root);
        player.openInventory(inv);
    }

    public void refresh(Player player, ClaimKey key) {
        ClaimRecord claim = claims.claimByKey(key);
        if (claim == null) {
            return;
        }
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof ClaimsSettingsHolder)) {
            return;
        }
        paint(player.getOpenInventory().getTopInventory(), claims.areaRoot(claim));
    }

    private void paint(Inventory inv, ClaimRecord claim) {
        ClaimRecord root = claims.areaRoot(claim);
        inv.setItem(SLOT_INFO, item(
                Material.OAK_SIGN,
                "&b" + root.areaNameOrOwner(),
                "&7Owner: &f" + root.ownerName(),
                "&7Level: &f" + claims.areaLevel(root) + "/" + plugin.maxLevelPerArea(),
                "&7World: &f" + root.key().world(),
                "&7Coords: &f" + root.key().x() + ", " + root.key().y() + ", " + root.key().z(),
                "",
                "&7Visitors use &f/spawn " + root.ownerName(),
                "&7when public spawn is on."));

        boolean open = root.spawnPublic();
        inv.setItem(SLOT_PUBLIC_SPAWN, item(
                open ? Material.OAK_DOOR : Material.IRON_DOOR,
                open ? "&aPublic spawn: ON" : "&cPublic spawn: OFF",
                open
                        ? "&7Anyone can run &f/spawn " + root.ownerName()
                        : "&7Only you and members can use this spawn.",
                "",
                "&eClick &7to toggle open / private"));

        boolean mobs = claim.mobsAllowed();
        inv.setItem(SLOT_MOBS, item(
                mobs ? Material.ZOMBIE_HEAD : Material.BARRIER,
                mobs ? "&aHostile mobs: ON" : "&cHostile mobs: OFF",
                mobs ? "&7Hostiles may spawn / stay in this claim." : "&7Hostiles are blocked and despawned.",
                "",
                "&eClick &7to toggle"));

        inv.setItem(SLOT_BACK, item(
                Material.ARROW,
                "&fBack",
                "&7Return to the claims dashboard."));
    }

    public void togglePublicSpawn(Player player, ClaimKey key) {
        ClaimRecord claim = manageClaim(player, key);
        if (claim == null) {
            return;
        }
        ClaimRecord root = claims.areaRoot(claim);
        boolean next = !root.spawnPublic();
        root.spawnPublic(next);
        claims.save();
        claims.recordSettingToggle(player, claim, "public-spawn", next);
        player.sendMessage(plugin.msg(next ? "toggle-spawn-public" : "toggle-spawn-private"));
        refresh(player, key);
    }

    public void toggleMobs(Player player, ClaimKey key) {
        ClaimRecord claim = manageClaim(player, key);
        if (claim == null) {
            return;
        }
        boolean next = !claim.mobsAllowed();
        claim.mobsAllowed(next);
        claims.save();
        claims.recordSettingToggle(player, claim, "mobs", next);
        if (!next) {
            int removed = claims.despawnHostiles(claim);
            player.sendMessage(plugin.msg("toggle-mobs-off")
                    .replace("{count}", String.valueOf(removed))
                    .replace("{buffer}", String.valueOf(plugin.territoryBufferBlocks())));
        } else {
            player.sendMessage(plugin.msg("toggle-mobs-on"));
        }
        refresh(player, key);
    }

    private ClaimRecord manageClaim(Player player, ClaimKey key) {
        ClaimRecord claim = claims.claimByKey(key);
        if (claim == null) {
            player.sendMessage(plugin.msg("settings-no-claim"));
            return null;
        }
        if (!claims.canManage(player, claim, false)) {
            player.sendMessage(plugin.msg("not-owner").replace("{owner}", claim.ownerName()));
            return null;
        }
        return claim;
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
            lore.add(plugin.colorize(line));
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
