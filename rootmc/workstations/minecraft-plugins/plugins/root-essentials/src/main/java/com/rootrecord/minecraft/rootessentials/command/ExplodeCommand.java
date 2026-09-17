package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.towny.TownyWildernessAccess;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-destruct: costs 1 TNT + full wallet; keeps inventory; hurts nearby players.
 */
public final class ExplodeCommand implements CommandExecutor, TabCompleter {

    private static final double BLAST_RADIUS = 5.0;
    private static final double VICTIM_HEALTH = 1.0; // half a heart
    private static final int SLOWNESS_TICKS = 20 * 60;

    private final RootEssentialsPlugin plugin;
    private final Set<UUID> keepInventoryDeaths = ConcurrentHashMap.newKeySet();

    public ExplodeCommand(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean shouldKeepInventory(UUID uuid) {
        return uuid != null && keepInventoryDeaths.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!Permissions.has(player, "explode")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length >= 1 && "cancel".equalsIgnoreCase(args[0])) {
            player.sendMessage(plugin.msg("explode-cancelled"));
            return true;
        }
        if (args.length >= 1 && "confirm".equalsIgnoreCase(args[0])) {
            executeExplode(player);
            return true;
        }
        offerExplode(player);
        return true;
    }

    private void offerExplode(Player player) {
        if (!hasTnt(player)) {
            player.sendMessage(plugin.msg("explode-need-tnt"));
            return;
        }
        double balance;
        try {
            balance = plugin.balance(player.getUniqueId(), player.getName());
        } catch (Exception ex) {
            balance = 0;
        }
        player.sendMessage(plugin.msg("explode-confirm-prompt"));
        player.sendMessage(plugin.msg("explode-wallet-warn")
                .replace("{balance}", plugin.money(balance))
                .replace("{currency}", plugin.currency()));
        player.sendMessage(ChatLinks.confirmCancel("/explode confirm", "/explode cancel"));
    }

    private void executeExplode(Player player) {
        if (!hasTnt(player)) {
            player.sendMessage(plugin.msg("explode-need-tnt"));
            return;
        }
        if (!removeOneTnt(player)) {
            player.sendMessage(plugin.msg("explode-need-tnt"));
            return;
        }

        Location loc = player.getLocation();
        List<Player> victims = nearbyPlayers(player, loc);
        double seized = plugin.seizeWalletToReserve(player.getUniqueId(), player.getName());
        if (seized > 0) {
            player.sendMessage(plugin.msg("explode-wallet-seized")
                    .replace("{amount}", plugin.money(seized))
                    .replace("{currency}", plugin.currency()));
        }

        playExplosionFx(loc);
        for (Player victim : victims) {
            hurtVictim(victim);
        }

        String town = TownyWildernessAccess.townNameAt(loc);
        String place = (town == null || town.isBlank())
                ? plugin.rawMsg("explode-place-wilderness")
                : plugin.rawMsg("explode-place-town").replace("{town}", town);
        String victimsText = formatVictims(victims);
        String broadcast = plugin.msg("explode-broadcast")
                .replace("{player}", player.getName())
                .replace("{victims}", victimsText)
                .replace("{place}", plugin.colorize(place));
        Bukkit.broadcastMessage(broadcast);

        keepInventoryDeaths.add(player.getUniqueId());
        player.setHealth(0);
    }

    private static List<Player> nearbyPlayers(Player bomber, Location loc) {
        List<Player> out = new ArrayList<>();
        if (loc.getWorld() == null) {
            return out;
        }
        for (Player other : loc.getWorld().getPlayers()) {
            if (other == null || !other.isOnline() || other.getUniqueId().equals(bomber.getUniqueId())) {
                continue;
            }
            if (other.getWorld() == null || !other.getWorld().equals(loc.getWorld())) {
                continue;
            }
            if (other.getLocation().distanceSquared(loc) <= BLAST_RADIUS * BLAST_RADIUS) {
                out.add(other);
            }
        }
        return out;
    }

    private static void hurtVictim(Player victim) {
        double max = victim.getMaxHealth();
        victim.setHealth(Math.max(0.5, Math.min(VICTIM_HEALTH, max)));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOWNESS_TICKS, 0, false, true, true));
        victim.playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
    }

    private static void playExplosionFx(Location loc) {
        if (loc.getWorld() == null) {
            return;
        }
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.85f);
        // Visual boom only — no block break / entity damage from the world explosion API.
        loc.getWorld().createExplosion(loc, 0f, false, false);
    }

    private static String formatVictims(List<Player> victims) {
        if (victims.isEmpty()) {
            return "nobody";
        }
        List<String> names = new ArrayList<>(victims.size());
        for (Player p : victims) {
            names.add(p.getName());
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(i == names.size() - 1 ? ", and " : ", ");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    private static boolean hasTnt(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == Material.TNT && stack.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeOneTnt(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != Material.TNT || stack.getAmount() <= 0) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("confirm", "cancel").stream()
                .filter(option -> option.startsWith(prefix))
                .toList();
    }
}
