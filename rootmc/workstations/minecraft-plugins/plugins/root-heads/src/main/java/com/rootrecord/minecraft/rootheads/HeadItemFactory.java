package com.rootrecord.minecraft.rootheads;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class HeadItemFactory {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static final String KIND_MOB = "mob";
    public static final String KIND_PLAYER = "player";

    private final NamespacedKey markerKey;
    private final NamespacedKey kindKey;
    private final NamespacedKey mobKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey versionKey;

    public HeadItemFactory(RootHeadsPlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "root_head");
        this.kindKey = new NamespacedKey(plugin, "head_kind");
        this.mobKey = new NamespacedKey(plugin, "mob_id");
        this.ownerKey = new NamespacedKey(plugin, "owner_uuid");
        this.versionKey = new NamespacedKey(plugin, "plugin_version");
    }

    public boolean isRootHead(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    public String readKind(ItemStack stack) {
        if (!isRootHead(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
    }

    public String readMobId(ItemStack stack) {
        if (!isRootHead(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
    }

    public String readOwnerUuid(ItemStack stack) {
        if (!isRootHead(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
    }

    public ItemStack createMobHead(HeadsConfig.MobHead mob, String pluginVersion) {
        ItemStack stack = new ItemStack(mob.material(), 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(legacy(mob.displayName()));
        List<Component> lore = new ArrayList<>();
        lore.add(legacy("&7Root-Heads collectible"));
        lore.add(legacy("&8Mob drop · cosmetic"));
        meta.lore(lore);

        if (meta instanceof SkullMeta skull && mob.material() == Material.PLAYER_HEAD) {
            applyTexture(skull, mob.id(), mob.texture());
        }

        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(kindKey, PersistentDataType.STRING, KIND_MOB);
        pdc.set(mobKey, PersistentDataType.STRING, mob.id().toLowerCase(Locale.ROOT));
        pdc.set(versionKey, PersistentDataType.STRING, pluginVersion == null ? "" : pluginVersion);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createPlayerHead(OfflinePlayer owner, HeadsConfig.PlayerHeads settings, String pluginVersion) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        String name = owner.getName() != null ? owner.getName() : owner.getUniqueId().toString();
        meta.displayName(legacy(settings.displayName().replace("{player}", name)));
        List<Component> lore = new ArrayList<>();
        for (String line : settings.lore()) {
            lore.add(legacy(line == null ? "" : line.replace("{player}", name)));
        }
        meta.lore(lore);
        meta.setOwningPlayer(owner);

        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(kindKey, PersistentDataType.STRING, KIND_PLAYER);
        pdc.set(mobKey, PersistentDataType.STRING, "player");
        pdc.set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        pdc.set(versionKey, PersistentDataType.STRING, pluginVersion == null ? "" : pluginVersion);
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyTexture(SkullMeta meta, String id, String texture) {
        if (texture == null || texture.isBlank()) {
            return;
        }
        UUID uuid = UUID.nameUUIDFromBytes(("RootHeads:" + id).getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(uuid, id.length() > 16 ? id.substring(0, 16) : id);
        profile.setProperty(new ProfileProperty("textures", texture.trim()));
        meta.setPlayerProfile(profile);
    }

    private static Component legacy(String raw) {
        return LEGACY.deserialize(raw == null ? "" : raw).decoration(TextDecoration.ITALIC, false);
    }
}
