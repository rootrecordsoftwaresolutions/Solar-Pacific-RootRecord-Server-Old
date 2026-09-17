package com.rootrecord.minecraft.rootskills.gui;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import com.rootrecord.minecraft.rootskills.model.SkillProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SkillsHubGui implements Listener {

    private static final String TITLE = "Root Skills";

    private final RootSkillsPlugin plugin;
    private final Map<UUID, SkillId> openDetail = new HashMap<>();

    public SkillsHubGui(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        if (player == null) {
            return;
        }
        Inventory inv = Bukkit.createInventory(player, 27, Component.text(TITLE, NamedTextColor.GOLD));
        PlayerSkillsProfile profile = plugin.repository().getOrCreate(player.getUniqueId());
        SkillId[] skills = SkillId.values();
        for (int i = 0; i < skills.length && i < 27; i++) {
            inv.setItem(i, skillIcon(skills[i], profile.skill(skills[i])));
        }
        openDetail.remove(player.getUniqueId());
        player.openInventory(inv);
    }

    public void openDetail(Player player, SkillId skill) {
        Inventory inv = Bukkit.createInventory(player, 27,
                Component.text(skill.key() + " details", NamedTextColor.YELLOW));
        PlayerSkillsProfile profile = plugin.repository().getOrCreate(player.getUniqueId());
        SkillProgress sp = profile.skill(skill);
        inv.setItem(13, skillIcon(skill, sp));
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(Component.text("Back", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(meta);
        inv.setItem(22, back);

        if (plugin.prestigeManager().canPrestige(profile, skill)) {
            ItemStack prestige = new ItemStack(Material.NETHER_STAR);
            ItemMeta pm = prestige.getItemMeta();
            pm.displayName(Component.text("Prestige", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            prestige.setItemMeta(pm);
            inv.setItem(16, prestige);
        }
        openDetail.put(player.getUniqueId(), skill);
        player.openInventory(inv);
    }

    private ItemStack skillIcon(SkillId skill, SkillProgress sp) {
        Material mat = plugin.skillCatalog() != null
                ? plugin.skillCatalog().require(skill).icon()
                : iconFor(skill);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String label = plugin.skillCatalog() != null
                ? plugin.skillCatalog().require(skill).displayName()
                : capitalize(skill.key());
        meta.displayName(Component.text(label, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        long next = plugin.xpService().formula().xpToNext(sp.level());
        long floor = plugin.xpService().formula().totalXpForLevel(sp.level());
        long into = Math.max(0L, sp.xp() - floor);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Level " + sp.level(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("XP " + into + " / " + next, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Prestige " + sp.prestige(), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click for details", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Component title = event.getView().title();
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(title);
        if (!plain.equals(TITLE) && !plain.endsWith(" details")) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        SkillId detail = openDetail.get(player.getUniqueId());
        if (detail != null) {
            if (clicked.getType() == Material.ARROW) {
                open(player);
                return;
            }
            if (clicked.getType() == Material.NETHER_STAR) {
                PlayerSkillsProfile profile = plugin.repository().getOrCreate(player.getUniqueId());
                if (plugin.prestigeManager().prestige(profile, detail)) {
                    openDetail(player, detail);
                } else {
                    player.sendMessage(plugin.msg("prestige.not-ready")
                            .replace("{threshold}", Integer.toString(plugin.prestigeManager().threshold())));
                }
                return;
            }
            return;
        }
        int slot = event.getRawSlot();
        SkillId[] skills = SkillId.values();
        if (slot >= 0 && slot < skills.length) {
            openDetail(player, skills[slot]);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Material iconFor(SkillId skill) {
        return switch (skill) {
            case MINING -> Material.IRON_PICKAXE;
            case WOODCUTTING -> Material.IRON_AXE;
            case HERBALISM -> Material.WHEAT;
            case EXCAVATION -> Material.IRON_SHOVEL;
            case FISHING -> Material.FISHING_ROD;
            case REPAIR -> Material.ANVIL;
            case SALVAGE -> Material.GRINDSTONE;
            case SMELTING -> Material.FURNACE;
            case ALCHEMY -> Material.BREWING_STAND;
            case TAMING -> Material.BONE;
            case ACROBATICS -> Material.FEATHER;
            case UNARMED -> Material.LEATHER;
            case SWORDS -> Material.IRON_SWORD;
            case AXES -> Material.DIAMOND_AXE;
            case ARCHERY -> Material.BOW;
            case CROSSBOWS -> Material.CROSSBOW;
            case TRIDENTS -> Material.TRIDENT;
            case MACES -> Material.MACE;
            case SPEARS -> Material.STICK;
            case DEFENSE -> Material.SHIELD;
            case ELYTRA -> Material.ELYTRA;
        };
    }
}
