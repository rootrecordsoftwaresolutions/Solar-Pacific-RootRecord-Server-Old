package com.rootrecord.minecraft.rootclaims;

import com.rootrecord.minecraft.rootclaims.gui.ClaimChestHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Virtual per-claim community chest (27 slots), openable anywhere inside the claim. */
public final class ClaimChestService {

    private final RootClaimsPlugin plugin;
    private final ClaimStore claims;
    private final ClaimService claimService;
    private final ClaimChestStore store;
    private final Map<String, OpenChest> open = new ConcurrentHashMap<>();

    public ClaimChestService(
            RootClaimsPlugin plugin, ClaimStore claims, ClaimService claimService, ClaimChestStore store) {
        this.plugin = plugin;
        this.claims = claims;
        this.claimService = claimService;
        this.store = store;
    }

    public ClaimChestStore store() {
        return store;
    }

    public void openFor(Player player) {
        ClaimRecord claim = claims.containing(player.getLocation());
        if (claim == null) {
            player.sendMessage(plugin.msg("chest-outside"));
            return;
        }
        if (!claimService.canManage(player, claim, true) && !claimService.isAdmin(player)) {
            player.sendMessage(plugin.msg("chest-denied").replace("{owner}", claim.ownerName()));
            return;
        }
        openClaimChest(player, claim);
    }

    public void openClaimChest(Player player, ClaimRecord claim) {
        String id = claim.key().storageId();
        OpenChest session = open.computeIfAbsent(id, ignored -> {
            ClaimChestHolder holder = new ClaimChestHolder(claim.key());
            Inventory inv = Bukkit.createInventory(
                    holder,
                    ClaimChestStore.CHEST_SIZE,
                    plugin.colorize("&8Claim chest &7- &f" + claim.ownerName()));
            holder.bind(inv);
            store.applyTo(inv, claim.key());
            return new OpenChest(holder, inv);
        });
        session.viewers.add(player.getUniqueId());
        player.openInventory(session.inventory);
        player.sendMessage(plugin.msg("chest-opened").replace("{owner}", claim.ownerName()));
    }

    public void onClose(Player player, ClaimChestHolder holder) {
        String id = holder.claimKey().storageId();
        OpenChest session = open.get(id);
        if (session == null) {
            return;
        }
        session.viewers.remove(player.getUniqueId());
        store.captureFrom(session.inventory, holder.claimKey());
        if (session.viewers.isEmpty()) {
            open.remove(id);
        }
    }

    /** Close viewers and return contents to {@code recipient} (overflow drops at feet). */
    public void dissolve(ClaimRecord claim, Player recipient) {
        String id = claim.key().storageId();
        OpenChest session = open.remove(id);
        ItemStack[] items;
        if (session != null) {
            items = session.inventory.getContents();
            store.takeAll(claim.key());
            for (UUID viewerId : session.viewers) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) {
                    viewer.closeInventory();
                }
            }
        } else {
            items = store.takeAll(claim.key());
        }
        if (recipient == null || items == null) {
            return;
        }
        int returned = 0;
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            HashMap<Integer, ItemStack> leftover = recipient.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                recipient.getWorld().dropItemNaturally(recipient.getLocation(), drop);
            }
            returned++;
        }
        if (returned > 0) {
            recipient.sendMessage(plugin.msg("chest-returned").replace("{count}", String.valueOf(returned)));
        }
    }

    public void saveAllOpen() {
        for (Map.Entry<String, OpenChest> entry : open.entrySet()) {
            store.captureFrom(entry.getValue().inventory, entry.getValue().holder.claimKey());
        }
    }

    /** Count of {@code material} across every claim chest owned by {@code ownerId}. */
    public int countOwned(UUID ownerId, org.bukkit.Material material) {
        if (ownerId == null || material == null || material.isAir()) {
            return 0;
        }
        saveAllOpen();
        int total = 0;
        for (ClaimRecord claim : claims.ownedBy(ownerId)) {
            total += countInContents(contentsFor(claim.key()), material);
        }
        return total;
    }

    /**
     * Remove up to {@code amount} of {@code material} from the owner's claim chests and give them to
     * {@code player} (overflow drops at feet).
     *
     * @return items successfully withdrawn from chests
     */
    public int withdrawOwned(Player player, org.bukkit.Material material, int amount) {
        if (player == null || material == null || material.isAir() || amount <= 0) {
            return 0;
        }
        saveAllOpen();
        int remaining = amount;
        int withdrawn = 0;
        for (ClaimRecord claim : claims.ownedBy(player.getUniqueId())) {
            if (remaining <= 0) {
                break;
            }
            ItemStack[] contents = contentsFor(claim.key());
            int before = countInContents(contents, material);
            if (before <= 0) {
                continue;
            }
            int take = Math.min(remaining, before);
            int removed = removeFromContents(contents, material, take);
            if (removed <= 0) {
                continue;
            }
            OpenChest session = open.get(claim.key().storageId());
            if (session != null) {
                session.inventory.setContents(contents);
                store.captureFrom(session.inventory, claim.key());
            } else {
                store.put(claim.key(), contents);
            }
            remaining -= removed;
            withdrawn += removed;
        }
        if (withdrawn > 0) {
            giveStacks(player, material, withdrawn);
        }
        return withdrawn;
    }

    private ItemStack[] contentsFor(ClaimKey key) {
        OpenChest session = open.get(key.storageId());
        if (session != null) {
            ItemStack[] live = session.inventory.getContents();
            ItemStack[] copy = new ItemStack[ClaimChestStore.CHEST_SIZE];
            if (live != null) {
                for (int i = 0; i < Math.min(copy.length, live.length); i++) {
                    if (live[i] != null && !live[i].getType().isAir()) {
                        copy[i] = live[i].clone();
                    }
                }
            }
            return copy;
        }
        return store.peek(key);
    }

    private static int countInContents(ItemStack[] contents, org.bukkit.Material material) {
        int total = 0;
        if (contents == null) {
            return 0;
        }
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static int removeFromContents(ItemStack[] contents, org.bukkit.Material material, int amount) {
        int remaining = amount;
        if (contents == null || remaining <= 0) {
            return 0;
        }
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            int left = stack.getAmount() - take;
            if (left <= 0) {
                contents[i] = null;
            } else {
                stack.setAmount(left);
            }
            remaining -= take;
        }
        return amount - remaining;
    }

    private static void giveStacks(Player player, org.bukkit.Material material, int amount) {
        int remaining = amount;
        int max = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int stackSize = Math.min(remaining, max);
            ItemStack stack = new ItemStack(material, stackSize);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            remaining -= stackSize;
        }
    }

    private static final class OpenChest {
        final ClaimChestHolder holder;
        final Inventory inventory;
        final java.util.Set<UUID> viewers = ConcurrentHashMap.newKeySet();

        OpenChest(ClaimChestHolder holder, Inventory inventory) {
            this.holder = holder;
            this.inventory = inventory;
        }
    }
}
