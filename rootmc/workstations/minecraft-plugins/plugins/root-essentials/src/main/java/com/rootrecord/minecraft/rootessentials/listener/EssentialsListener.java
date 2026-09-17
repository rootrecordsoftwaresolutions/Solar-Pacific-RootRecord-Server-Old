package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("deprecation")
public final class EssentialsListener implements Listener {

    private final RootEssentialsPlugin plugin;

    public EssentialsListener(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            var ban = plugin.moderation().activeBan(event.getUniqueId());
            if (ban.isPresent()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "Banned: " + ban.get().reason());
                return;
            }
            String ip = event.getAddress() == null ? null : event.getAddress().getHostAddress();
            var ipBan = plugin.moderation().activeIpBan(ip);
            if (ipBan.isPresent()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "Banned: " + ipBan.get().reason());
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Ban check failed: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            plugin.ensureBalanceRow(player);
        } catch (Exception ex) {
            plugin.getLogger().warning("Balance init failed for " + player.getName() + ": " + ex.getMessage());
        }
        if (plugin.playerState().isVanished(player.getUniqueId())) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("essentials.vanish.see") && !online.hasPermission("rootessentials.vanish.see")) {
                    online.hidePlayer(plugin, player);
                }
            }
        }
        plugin.grantNewbieKit(player);
        if (plugin.newPlayerGrace() != null) {
            plugin.newPlayerGrace().recordJoin(player);
        }
        try {
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                plugin.moderation().recordLastIp(
                        player.getUniqueId(), player.getAddress().getAddress().getHostAddress());
            }
        } catch (Exception ex) {
            plugin.getLogger().fine("Last IP record failed: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.playerState().clearAfk(event.getPlayer().getUniqueId());
        plugin.playerState().clearTpa(event.getPlayer().getUniqueId());
        plugin.teleportWarmup().onQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.playerState().isGod(player.getUniqueId())
                || plugin.playerState().isAfk(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerPlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        plugin.teleportWarmup().tagCombat(victim);
        plugin.teleportWarmup().tagCombat(attacker);
    }

    private static Player attackerPlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler
    public void onTrashClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (title == null || !title.contains("Trash")) return;
        for (ItemStack stack : event.getInventory().getContents()) {
            if (stack != null) stack.setAmount(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        try {
            if (plugin.moderation().activeMute(player.getUniqueId()).isPresent()) {
                event.setCancelled(true);
                player.sendMessage(plugin.msg("muted"));
                return;
            }
        } catch (Exception ignored) {
        }
        if (plugin.playerState().isGlobalChatMuted()
                && !Permissions.has(player, "mutechat.exempt")
                && !player.hasPermission("essentials.mutechat.exempt")) {
            event.setCancelled(true);
            player.sendMessage(plugin.msg("chat-muted"));
            return;
        }
        event.getRecipients().removeIf(recipient ->
                plugin.playerState().isIgnoring(recipient.getUniqueId(), player.getUniqueId()));
        if (plugin.playerState().isAfk(player.getUniqueId())) {
            plugin.playerState().clearAfk(player.getUniqueId());
        }
        String msg = event.getMessage();
        if (Permissions.has(player, "chat.color") || Permissions.has(player, "chat.rgb")) {
            msg = msg.replace('&', '\u00A7');
        }
        event.setMessage(msg);
    }

    /**
     * Staff use a red global channel marker instead of the green one, and no [Staff] chat badge.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatStaffFormat(AsyncPlayerChatEvent event) {
        if (!isStaffForChat(event.getPlayer())) {
            return;
        }
        String format = event.getFormat();
        if (format == null || format.isBlank()) {
            return;
        }
        event.setFormat(applyStaffChatFormat(format));
    }

    private static String applyStaffChatFormat(String format) {
        String updated = format
                .replace("§2✦", "§4✦")
                .replace("&2✦", "&4✦");
        return stripStaffBadge(updated, "Staff");
    }

    private static String stripStaffBadge(String format, String label) {
        String[] needles = {
                "§8[§c" + label + "§8]§r ",
                "§8[&c" + label + "§8]§r ",
                "§8[" + label + "§8]§r ",
                "[" + label + "] ",
        };
        for (String needle : needles) {
            if (format.contains(needle)) {
                format = format.replace(needle, "");
            }
        }
        return format;
    }

    private static boolean isStaffForChat(Player player) {
        return player.hasPermission("group.helper")
                || player.hasPermission("group.moderator")
                || player.hasPermission("group.admin")
                || player.hasPermission("group.dev")
                || player.hasPermission("group.developer")
                || player.hasPermission("group.owner")
                || player.hasPermission("rootadmin.admin")
                || player.hasPermission("rootadmin.mod");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        plugin.playerState().clearAfk(event.getPlayer().getUniqueId());
        plugin.teleportWarmup().onMove(event.getPlayer(), event.getFrom(), event.getTo());
    }
}
