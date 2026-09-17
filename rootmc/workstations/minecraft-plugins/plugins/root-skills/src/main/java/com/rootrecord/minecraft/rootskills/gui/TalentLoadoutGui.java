package com.rootrecord.minecraft.rootskills.gui;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import com.rootrecord.minecraft.rootskills.model.TalentState;
import com.rootrecord.minecraft.rootskills.talents.TalentDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** SHIFT+F opens talent loadout; click to equip/unequip or activate. */
public final class TalentLoadoutGui implements Listener {

    private static final String TITLE = "Talent Loadout";

    private final RootSkillsPlugin plugin;

    public TalentLoadoutGui(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, Component.text(TITLE, NamedTextColor.AQUA));
        PlayerSkillsProfile profile = plugin.repository().getOrCreate(player.getUniqueId());
        int slot = 0;
        for (TalentDefinition def : plugin.talentManager().all()) {
            if (slot >= 54) {
                break;
            }
            TalentState state = profile.talent(def.id());
            ItemStack item = new ItemStack(state.equipped() ? Material.LIME_DYE
                    : (state.unlocked() ? Material.YELLOW_DYE : Material.GRAY_DYE));
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(def.displayName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(def.description(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Skill: " + def.skill().key() + " @" + def.unlockLevel(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(state.unlocked() ? (state.equipped() ? "Equipped" : "Unlocked") : "Locked",
                            state.unlocked() ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Left-click: equip/unequip", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Right-click: activate", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            // encode talent id in PDC-less way via localized name fallback: store in display lore last line
            item.setItemMeta(meta);
            // Use item amount + custom model isn't reliable; put id in persistent data
            item.editMeta(m -> m.getPersistentDataContainer().set(
                    plugin.talentKey(),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    def.id()));
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!plugin.getConfig().getBoolean("features.talents", true)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("talents.shift-f-loadout", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (plugin.getConfig().getBoolean("talents.cancel-swap-on-loadout", true)) {
            event.setCancelled(true);
        }
        open(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!TITLE.equals(plain)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String talentId = clicked.getItemMeta().getPersistentDataContainer()
                .get(plugin.talentKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (talentId == null) {
            return;
        }
        PlayerSkillsProfile profile = plugin.repository().getOrCreate(player.getUniqueId());
        TalentState state = profile.talent(talentId);
        if (!state.unlocked()) {
            player.sendMessage(plugin.msg("talents.locked").replace("{talent}", talentId));
            return;
        }
        if (event.isRightClick()) {
            plugin.talentManager().activateAbility(player, talentId);
            return;
        }
        state.setEquipped(!state.equipped());
        if (state.equipped()) {
            state.setSlot(event.getRawSlot());
        } else {
            state.setSlot(-1);
        }
        profile.markDirty();
        plugin.repository().saveAsync(profile);
        open(player);
    }
}
