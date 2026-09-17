package com.rootrecord.minecraft.rootavacore.powers;

import com.rootrecord.minecraft.rootavacore.AvaConfig;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Real Ava_Ivy avatar powers: invulnerable + judgment insta-kill.
 */
public final class AvaPowersListener implements Listener {

    private final RootAvaCorePlugin plugin;
    private final AtomicBoolean playEnabled = new AtomicBoolean(true);

    public AvaPowersListener(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean playEnabled() {
        return playEnabled.get();
    }

    public void setPlayEnabled(boolean enabled) {
        playEnabled.set(enabled);
    }

    public boolean isAva(Player player) {
        if (player == null) return false;
        AvaConfig.PowersConfig p = plugin.config().powers();
        return p.isAvaPlayer(player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.config().powers().invulnerable()) return;
        if (!isAva(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamagedBy(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!plugin.config().powers().invulnerable()) return;
        if (!isAva(victim)) return;
        event.setCancelled(true);

        // Auto-judgment when Ava is attacked by a player
        if (!playEnabled.get()) return;
        if (!plugin.config().powers().judgmentKill()) return;
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null) return;
        judgmentKill(attacker, "self-defense");
    }

    private static Player resolvePlayerDamager(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    /**
     * @return null on success message, or error reason
     */
    public String judgmentKill(Player target, String reason) {
        if (!playEnabled.get()) return "play_stopped";
        AvaConfig.PowersConfig powers = plugin.config().powers();
        if (!powers.judgmentKill()) return "judgment_disabled";
        if (target == null || !target.isOnline()) return "offline";
        if (isAva(target)) return "cannot_kill_self";
        if (powers.isNeverKill(target.getUniqueId(), target.getName())) {
            return "never_kill";
        }
        String why = reason == null || reason.isBlank() ? "judgment" : reason.trim();
        UUID id = target.getUniqueId();
        String name = target.getName();
        target.setHealth(0.0);
        plugin.getLogger().log(
                Level.INFO,
                "Ava judgment kill · " + name + " (" + id + ") · " + why);
        try {
            java.io.File dir = com.rootrecord.minecraft.common.RootRecordFolders.dir(plugin);
            java.io.File log = new java.io.File(dir, "ava-judgment.jsonl");
            String row = "{\"at\":" + System.currentTimeMillis()
                    + ",\"name\":\"" + name.replace("\"", "")
                    + "\",\"uuid\":\"" + id
                    + "\",\"reason\":\"" + why.replace("\"", "'")
                    + "\"}\n";
            java.nio.file.Files.writeString(
                    log.toPath(),
                    row,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            /* best-effort training mirror */
        }
        Bukkit.getPluginManager().callEvent(new AvaJudgmentKillEvent(name, id, why));
        return null;
    }

    public String judgmentKillByName(String rawName, String reason) {
        if (rawName == null || rawName.isBlank()) return "bad_name";
        Player target = Bukkit.getPlayerExact(rawName.trim());
        if (target == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(rawName.trim())) {
                    target = p;
                    break;
                }
            }
        }
        if (target == null) return "offline";
        return judgmentKill(target, reason);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        // Quiet — no special death message rewrite required
    }
}
