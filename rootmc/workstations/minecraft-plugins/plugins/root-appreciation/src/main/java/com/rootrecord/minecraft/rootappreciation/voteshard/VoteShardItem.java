package com.rootrecord.minecraft.rootappreciation.voteshard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Vote Shard / Block / Geode — physical governance weight (mimics Appreciation tokens).
 * Only holdings in the double /ec count toward Council power.
 */
public final class VoteShardItem {

    public enum Form {
        SHARD(Material.AMETHYST_SHARD, "Vote Shard", "vote_shard"),
        BLOCK(Material.AMETHYST_BLOCK, "Vote Shard Block", "vote_shard_block"),
        GEODE(Material.BUDDING_AMETHYST, "Vote Geode", "vote_geode");

        final Material material;
        final String display;
        final String keyName;

        Form(Material material, String display, String keyName) {
            this.material = material;
            this.display = display;
            this.keyName = keyName;
        }

        public String display() {
            return display;
        }
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final NamespacedKey formKey;
    private final NamespacedKey weightKey;

    public VoteShardItem(JavaPlugin plugin) {
        this.formKey = new NamespacedKey(plugin, "vote_shard_form");
        this.weightKey = new NamespacedKey(plugin, "vote_shard_weight");
    }

    public boolean isVoteShardItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(formKey, PersistentDataType.STRING);
    }

    public Form formOf(ItemStack stack) {
        if (!isVoteShardItem(stack)) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(formKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        for (Form f : Form.values()) {
            if (f.keyName.equalsIgnoreCase(raw)) {
                return f;
            }
        }
        return null;
    }

    /** Per-item certificate weight (Block/Geode) or 1 for unit shards. */
    public int unitWeight(ItemStack stack) {
        if (!isVoteShardItem(stack)) {
            return 0;
        }
        Integer w = stack.getItemMeta().getPersistentDataContainer().get(weightKey, PersistentDataType.INTEGER);
        return w == null || w < 1 ? 1 : w;
    }

    /** Total voting power of this stack (amount × unit weight). */
    public int stackPower(ItemStack stack) {
        if (!isVoteShardItem(stack)) {
            return 0;
        }
        return Math.max(0, stack.getAmount()) * unitWeight(stack);
    }

    public ItemStack create(Form form, int unitWeight, int amount) {
        Form f = form == null ? Form.SHARD : form;
        int weight = Math.max(1, unitWeight);
        int amt = Math.max(1, Math.min(64, amount));
        // Blocks/Geodes are certificates — keep amount 1 with weight on PDC.
        if (f != Form.SHARD) {
            amt = 1;
        }
        ItemStack stack = new ItemStack(f.material, amt);
        applyMeta(stack, f, weight);
        return stack;
    }

    /** Maximally condensed form for an exact weight — always one Vote Geode certificate. */
    public ItemStack condensed(int totalWeight) {
        return create(Form.GEODE, Math.max(1, totalWeight), 1);
    }

    public void refreshLore(ItemStack stack) {
        if (!isVoteShardItem(stack)) {
            return;
        }
        Form form = formOf(stack);
        if (form == null) {
            return;
        }
        applyMeta(stack, form, unitWeight(stack));
    }

    private void applyMeta(ItemStack stack, Form form, int unitWeight) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        int power = Math.max(1, stack.getAmount()) * Math.max(1, unitWeight);
        meta.displayName(Component.text(form.display, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Voting power: " + power, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(LEGACY.deserialize("&7Only counts in &f/ec &7(double ender chest).")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(LEGACY.deserialize("&8Merge in /ec: shards → block → geode")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(formKey, PersistentDataType.STRING, form.keyName);
        meta.getPersistentDataContainer().set(weightKey, PersistentDataType.INTEGER, Math.max(1, unitWeight));
        stack.setItemMeta(meta);
    }

    public int sumPower(ItemStack[] contents) {
        int total = 0;
        if (contents == null) {
            return 0;
        }
        for (ItemStack stack : contents) {
            total += stackPower(stack);
        }
        return total;
    }

    /** Remove all vote-shard forms from contents; return total power removed. Mutates array. */
    public int clearAllForms(ItemStack[] contents) {
        int power = 0;
        if (contents == null) {
            return 0;
        }
        for (int i = 0; i < contents.length; i++) {
            if (isVoteShardItem(contents[i])) {
                power += stackPower(contents[i]);
                contents[i] = null;
            }
        }
        return power;
    }
}
