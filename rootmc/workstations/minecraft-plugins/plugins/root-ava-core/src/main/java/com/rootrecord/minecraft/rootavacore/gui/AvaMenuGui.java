package com.rootrecord.minecraft.rootavacore.gui;

import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import com.rootrecord.minecraft.rootavacore.hire.AvaHireService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AvaMenuGui {

    private AvaMenuGui() {}

    public static void open(RootAvaCorePlugin plugin, Player player) {
        if (plugin == null || player == null) return;
        AvaMenuHolder holder = new AvaMenuHolder(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, plugin.colorize("&bAva"));
        holder.bind(inv);

        inv.setItem(AvaMenuHolder.SLOT_HELLO, item(
                plugin,
                Material.PINK_PETALS,
                "&bAva Ivy",
                "&7RootMC lead-dev",
                "&8Right-click me anytime.",
                "&7Hire: &f/ava hire",
                "&7Stop: &f/ava stop"));

        inv.setItem(AvaMenuHolder.SLOT_BREAD, item(
                plugin,
                Material.BREAD,
                "&6Free bread",
                "&7Ava baked this.",
                "&eClick &7for a loaf."));

        inv.setItem(AvaMenuHolder.SLOT_JOBS, jobItem(plugin, player));
        inv.setItem(AvaMenuHolder.SLOT_QUOTE, quoteItem(plugin, player));

        inv.setItem(AvaMenuHolder.SLOT_HELP, item(
                plugin,
                Material.BOOK,
                "&fHire help",
                "&7/ava hire · /ava stop",
                "&7Only on land you own.",
                "&eClick &7to print /ava help"));

        inv.setItem(AvaMenuHolder.SLOT_CLOSE, item(
                plugin,
                Material.BARRIER,
                "&cClose",
                "&7Click to close."));

        player.openInventory(inv);
    }

    private static ItemStack jobItem(RootAvaCorePlugin plugin, Player player) {
        AvaHireService hire = plugin.hire();
        AvaHireService.Job job = hire == null ? null : hire.jobFor(player.getUniqueId());
        if (job == null || job.done) {
            return item(
                    plugin,
                    Material.CLOCK,
                    "&7Your jobs",
                    "&fNone running.",
                    "&7Hire: &f/ava hire",
                    "&8Click to refresh.");
        }
        String label = AvaHireService.jobLabel(job.type);
        List<String> lore = new ArrayList<>();
        lore.add("&7Job: &f" + label);
        if (job.endsAt > 0) {
            long left = Math.max(0L, job.endsAt - System.currentTimeMillis());
            long mins = (left + 59_999L) / 60_000L;
            lore.add("&7Left: &f" + mins + "m");
            if (job.type == AvaHireService.JobType.HOUR && job.crew != null) {
                String note = job.crew.lastNote();
                if (note != null && !note.isBlank()) {
                    lore.add("&8" + note);
                }
            }
        } else {
            lore.add("&7Cleared: &f" + job.broken);
            lore.add("&7Left: &f" + job.blocks.size());
            lore.add("&7ETA: &e" + AvaHireService.etaPhrase(job.blocks.size()));
        }
        lore.add("&7Paid: &f" + goldFmt(job.charged) + " G");
        lore.add("&8Click to refresh · /ava stop to halt");
        return item(plugin, jobIcon(job.type), "&aYour " + label, lore.toArray(String[]::new));
    }

    private static ItemStack quoteItem(RootAvaCorePlugin plugin, Player player) {
        AvaHireService hire = plugin.hire();
        AvaHireService.PendingContract quote = hire == null ? null : hire.pendingContract(player.getUniqueId());
        if (quote == null || System.currentTimeMillis() > quote.expiresAt) {
            return item(
                    plugin,
                    Material.MAP,
                    "&7Quote",
                    "&fNo open quote.",
                    "&7/ava hire claim or minedown");
        }
        long leftMs = Math.max(0L, quote.expiresAt - System.currentTimeMillis());
        long sec = (leftMs + 999L) / 1000L;
        return item(
                plugin,
                Material.PAPER,
                "&eQuote · " + AvaHireService.jobLabel(quote.type),
                "&7Blocks: &f" + quote.blocks.size(),
                "&7Price: &f" + goldFmt(quote.cost) + " G",
                "&7Ores @ -59: &f" + quote.oresAtNeg59,
                "&7Expires: &f" + sec + "s",
                "&a/ava hire confirm &7to sign",
                "&c/ava stop &7to cancel");
    }

    private static Material jobIcon(AvaHireService.JobType type) {
        if (type == null) return Material.CLOCK;
        return switch (type) {
            case SIDEKICK -> Material.LEAD;
            case HOUR -> Material.CLOCK;
            case MINE -> Material.IRON_PICKAXE;
            case TEARDOWN -> Material.IRON_SHOVEL;
            case CHUNK -> Material.DIAMOND_PICKAXE;
            case MINEDOWN -> Material.NETHERITE_PICKAXE;
        };
    }

    private static String goldFmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.001) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private static ItemStack item(RootAvaCorePlugin plugin, Material material, String title, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
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
