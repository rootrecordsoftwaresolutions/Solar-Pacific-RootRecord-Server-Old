package com.rootrecord.minecraft.roothaste.service;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcPublicReachout;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.roothaste.RootHastePlugin;
import com.rootrecord.minecraft.roothaste.data.JointRecordStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Single global joint session — light, pass, burn-out warnings, holder effects. */
public final class JointSessionService implements Listener {

    private final RootHastePlugin plugin;

    private double costGold = 1.0;
    private long holdMs = 180_000L;
    private String treasuryChannel = "service-fee:joint";
    private boolean discordRelay = true;
    private String chatTagRaw = "&a[J]&r ";
    private int regenAmp = 0;
    private int nightVisionAmp = 0;
    private int hasteAmp = 0;
    private boolean holderLightEnabled = true;
    private int holderLightLevel = 12;
    private int[] warnGlobalSeconds = {30};
    private int[] warnHolderSeconds = {45, 15, 5, 4, 3, 2, 1};

    private boolean recordRewardEnabled = true;
    private double recordRewardStarterGold = 75.0;
    private double recordRewardHolderGold = 25.0;

    private UUID holderId;
    private String holderName;
    private UUID sparkedById;
    private String sparkedByName;
    private long chainStartedAtMs;
    private long burnOutAtMs;
    private final Set<Integer> warnedAt = new HashSet<>();
    private BukkitTask tickTask;
    private BukkitTask lightTask;
    /** World + block coords of the temporary LIGHT we placed; null when none. */
    private World lightWorld;
    private int lightX;
    private int lightY;
    private int lightZ;
    private boolean hasLightBlock;

    public JointSessionService(RootHastePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration cfg) {
        if (cfg == null) {
            return;
        }
        costGold = Math.max(0, cfg.getDouble("cost-gold", 1.0));
        holdMs = Math.max(5_000L, cfg.getLong("hold-seconds", 180) * 1000L);
        String defaultChannel = plugin.theme().defaultTreasuryChannel();
        treasuryChannel = cfg.getString("treasury-channel", defaultChannel);
        if (treasuryChannel == null || treasuryChannel.isBlank()) {
            treasuryChannel = defaultChannel;
        }
        discordRelay = cfg.getBoolean("discord-relay", true);
        recordRewardEnabled = cfg.getBoolean("record-reward.enabled", true);
        recordRewardStarterGold = Math.max(0, cfg.getDouble("record-reward.starter-gold", 75.0));
        recordRewardHolderGold = Math.max(0, cfg.getDouble("record-reward.holder-gold", 25.0));
        String defaultTag = plugin.theme().defaultChatTag();
        String tag = cfg.getString("chat-tag", defaultTag);
        chatTagRaw = tag == null || tag.isBlank() ? defaultTag : tag;
        regenAmp = Math.max(0, cfg.getInt("effects.regeneration-amplifier", 0));
        nightVisionAmp = Math.max(0, cfg.getInt("effects.night-vision-amplifier", 0));
        hasteAmp = Math.max(0, cfg.getInt("effects.haste-amplifier", 0));
        holderLightEnabled = cfg.getBoolean("holder-light.enabled", true);
        holderLightLevel = Math.max(1, Math.min(15, cfg.getInt("holder-light.level", 12)));
        if (!holderLightEnabled) {
            clearHolderLight();
            stopLightTask();
        } else if (isActive()) {
            ensureLightTask();
        }
        List<Integer> globalWarns = cfg.getIntegerList("warn-global-seconds");
        if (globalWarns != null && !globalWarns.isEmpty()) {
            warnGlobalSeconds = globalWarns.stream().mapToInt(Integer::intValue).filter(s -> s > 0).toArray();
        } else {
            warnGlobalSeconds = new int[] {30};
        }
        List<Integer> holderWarns = cfg.getIntegerList("warn-holder-seconds");
        if (holderWarns != null && !holderWarns.isEmpty()) {
            warnHolderSeconds = holderWarns.stream().mapToInt(Integer::intValue).filter(s -> s > 0).toArray();
        } else {
            // Legacy: warn-seconds treated as holder-only spam; 30s stays global.
            List<Integer> legacy = cfg.getIntegerList("warn-seconds");
            if (legacy != null && !legacy.isEmpty()) {
                warnHolderSeconds = legacy.stream()
                        .mapToInt(Integer::intValue)
                        .filter(s -> s > 0 && s != 30)
                        .toArray();
            } else {
                warnHolderSeconds = new int[] {45, 15, 5, 4, 3, 2, 1};
            }
        }
    }

    public String chatTagRaw() {
        return chatTagRaw;
    }

    public String chatTagColored() {
        return ChatColor.translateAlternateColorCodes('&', chatTagRaw);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        stopLightTask();
        clearHolderLight();
        Player holder = holderPlayer();
        if (holder != null) {
            clearEffects(holder);
            clearTag(holder);
        }
        holderId = null;
        holderName = null;
        sparkedById = null;
        sparkedByName = null;
        chainStartedAtMs = 0L;
        warnedAt.clear();
    }

    public double costGold() {
        return costGold;
    }

    public void setCostGold(double amount) {
        costGold = Math.max(0, amount);
    }

    public boolean isActive() {
        return holderId != null;
    }

    public UUID holderId() {
        return holderId;
    }

    public boolean isHolder(Player player) {
        return player != null && holderId != null && holderId.equals(player.getUniqueId());
    }

    /** @return null on success, otherwise a message key or preformatted denial */
    public String tryLight(Player player) {
        if (player == null) {
            return "players-only";
        }
        if (!plugin.hasUse(player)) {
            return "no-permission";
        }
        if (isActive()) {
            return "already-active";
        }
        double charged = 0;
        if (!plugin.hasBypassFee(player) && costGold > 0) {
            RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
            if (economy == null) {
                plugin.getLogger().warning("light denied: economy provider missing (need Root-Economy + Vault).");
                return "economy-unavailable";
            }
            RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
            if (treasury == null) {
                plugin.getLogger().warning("light denied: treasury bridge missing (Root-Economy.treasury()).");
                return "economy-unavailable";
            }
            double bal = economy.balance(player.getUniqueId());
            if (bal + 1e-9 < costGold) {
                return "insufficient:" + plugin.msg("insufficient")
                        .replace("{fee}", format(costGold))
                        .replace("{balance}", format(bal));
            }
            if (!economy.withdraw(player.getUniqueId(), costGold)) {
                return "fee-failed:" + plugin.msg("fee-failed").replace("{fee}", format(costGold));
            }
            try {
                // Full fee → Server Reserve (tax-free), same pattern as claim / wilderness fees.
                String channel = treasuryChannel == null || treasuryChannel.isBlank()
                        ? plugin.theme().defaultTreasuryChannel()
                        : treasuryChannel.trim();
                if (!channel.toLowerCase(Locale.ROOT).endsWith(":tax-free")) {
                    channel = channel + ":tax-free";
                }
                treasury.creditTreasury(
                        costGold,
                        TreasuryLedgerType.TOWNY_SINK,
                        player.getUniqueId(),
                        player.getName(),
                        channel);
            } catch (Exception ex) {
                economy.deposit(player.getUniqueId(), costGold);
                plugin.getLogger().warning("Pass-game treasury sink failed (refunded): " + ex.getMessage());
                return "fee-failed:" + plugin.msg("fee-failed").replace("{fee}", format(costGold));
            }
            charged = costGold;
            double after = economy.balance(player.getUniqueId());
            player.sendMessage(plugin.msg("fee-charged")
                    .replace("{fee}", format(charged))
                    .replace("{balance}", format(after)));
        }
        sparkedById = player.getUniqueId();
        sparkedByName = player.getName();
        chainStartedAtMs = System.currentTimeMillis();
        startSession(player);
        String sparked = plugin.msg("light").replace("{player}", player.getName());
        if (charged > 0) {
            String feeBit = plugin.msg("light-fee")
                    .replace("{fee}", format(charged))
                    .replace("%fee%", format(charged));
            if (feeBit.isBlank() || "light-fee".equals(feeBit) || "sparked-fee".equals(feeBit) || "lit-fee".equals(feeBit)) {
                feeBit = plugin.colorize(" &7(" + format(charged) + " G -> Server Reserve)");
            }
            sparked = sparked + feeBit;
        }
        broadcast(sparked);
        announceCurrentRecord();
        return null;
    }

    private void announceCurrentRecord() {
        JointRecordStore records = plugin.records();
        if (records == null || records.longestMs() <= 0) {
            broadcast(plugin.msg("no-record"));
            return;
        }
        broadcast(plugin.msg("longest")
                .replace("{duration}", JointRecordStore.formatDuration(records.longestMs()))
                .replace("{player}", records.sparkedBy().isBlank() ? "?" : records.sparkedBy())
                .replace("{holder}", records.lastHolder().isBlank() ? "?" : records.lastHolder()));
    }

    public String tryPass(Player from, Player to) {
        if (from == null) {
            return "players-only";
        }
        if (!plugin.hasUse(from)) {
            return "no-permission";
        }
        if (!isActive()) {
            return "not-holding";
        }
        if (!isHolder(from)) {
            return "not-holding";
        }
        if (to == null || !to.isOnline()) {
            return "player-not-found";
        }
        if (to.getUniqueId().equals(from.getUniqueId())) {
            return "pass-self";
        }
        clearEffects(from);
        clearTag(from);
        clearHolderLight();
        startSession(to);
        long sessionMs = chainStartedAtMs > 0 ? Math.max(0L, System.currentTimeMillis() - chainStartedAtMs) : 0L;
        String session = JointRecordStore.formatDuration(sessionMs);
        JointRecordStore records = plugin.records();
        String highscore = (records == null || records.longestMs() <= 0)
                ? "none"
                : JointRecordStore.formatDuration(records.longestMs());
        broadcast(plugin.msg("passed")
                .replace("{from}", from.getName())
                .replace("{to}", to.getName())
                .replace("{session}", session)
                .replace("{highscore}", highscore)
                .replace("{record}", highscore));
        return null;
    }

    private void startSession(Player holder) {
        holderId = holder.getUniqueId();
        holderName = holder.getName();
        burnOutAtMs = System.currentTimeMillis() + holdMs;
        warnedAt.clear();
        applyEffects(holder);
        applyTag(holder);
        ensureTicker();
        ensureLightTask();
        updateHolderLight(holder);
    }

    private void ensureTicker() {
        if (tickTask != null) {
            return;
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void ensureLightTask() {
        if (!holderLightEnabled || lightTask != null) {
            return;
        }
        lightTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLight, 1L, 5L);
    }

    private void stopLightTask() {
        if (lightTask != null) {
            lightTask.cancel();
            lightTask = null;
        }
    }

    private void tickLight() {
        if (!isActive() || !holderLightEnabled) {
            clearHolderLight();
            return;
        }
        updateHolderLight(holderPlayer());
    }

    private void tick() {
        if (!isActive()) {
            return;
        }
        long remainingMs = burnOutAtMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
            burnOut();
            return;
        }
        int remainingSec = (int) Math.ceil(remainingMs / 1000.0);
        for (int warn : warnGlobalSeconds) {
            if (remainingSec == warn && warnedAt.add(warn)) {
                String holderLabel = holderName != null ? holderName : "Someone";
                Player live = holderPlayer();
                if (live != null) {
                    holderLabel = live.getName();
                }
                broadcast(plugin.msg("warn-global")
                        .replace("{player}", holderLabel)
                        .replace("{seconds}", String.valueOf(warn)));
            }
        }
        Player holder = holderPlayer();
        for (int warn : warnHolderSeconds) {
            if (remainingSec == warn && warnedAt.add(warn) && holder != null) {
                holder.sendMessage(plugin.msg("warn")
                        .replace("{player}", holder.getName())
                        .replace("{seconds}", String.valueOf(warn)));
            }
        }
        if (holder != null) {
            applyEffects(holder);
        }
    }

    private void burnOut() {
        String name = holderName != null ? holderName : "Someone";
        burnOutWithMessage(plugin.msg("burned-out").replace("{player}", name));
    }

    /** Ends the chain with a custom broadcast line (still eligible for longest-record). */
    private void burnOutWithMessage(String endMessage) {
        String name = holderName != null ? holderName : "Someone";
        UUID lastHolderId = holderId;
        String sparked = sparkedByName != null ? sparkedByName : name;
        UUID sparkedId = sparkedById;
        long durationMs = chainStartedAtMs > 0 ? System.currentTimeMillis() - chainStartedAtMs : 0L;
        Player holder = holderPlayer();
        if (holder != null) {
            clearEffects(holder);
            clearTag(holder);
        }
        clearHolderLight();
        holderId = null;
        holderName = null;
        sparkedById = null;
        sparkedByName = null;
        chainStartedAtMs = 0L;
        warnedAt.clear();
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        stopLightTask();
        if (endMessage != null && !endMessage.isBlank()) {
            broadcast(endMessage);
        }
        if (durationMs > 0 && plugin.records() != null) {
            boolean record = plugin.records().tryRecord(durationMs, sparked, name);
            if (record) {
                broadcast(plugin.msg("new-record")
                        .replace("{player}", sparked)
                        .replace("{holder}", name)
                        .replace("{duration}", JointRecordStore.formatDuration(durationMs)));
                payRecordRewards(sparkedId, sparked, lastHolderId, name);
            }
        }
    }

    private void payRecordRewards(UUID starterId, String starterName, UUID holderId, String holderName) {
        if (!recordRewardEnabled) {
            return;
        }
        if (recordRewardStarterGold > 0) {
            String prefix = plugin.theme().isJoint() ? "joint-record" : "torch-record";
            payRecordReward(starterId, starterName, recordRewardStarterGold, prefix + ":starter", "record-reward-starter");
        }
        if (recordRewardHolderGold > 0) {
            String prefix = plugin.theme().isJoint() ? "joint-record" : "torch-record";
            payRecordReward(holderId, holderName, recordRewardHolderGold, prefix + ":holder", "record-reward-holder");
        }
    }

    private void payRecordReward(UUID uuid, String name, double amount, String reason, String msgKey) {
        if (uuid == null || amount <= 0) {
            return;
        }
        String display = name != null && !name.isBlank() ? name : "player";
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            plugin.getLogger().warning("Record reward unpaid — treasury unavailable for " + display);
            return;
        }
        boolean ok = treasury.grantToPlayer(
                uuid,
                display,
                amount,
                treasury.treasuryUuid(),
                treasury.treasuryUsername(),
                reason);
        if (!ok) {
            plugin.getLogger().warning("Record reward failed for " + display + " (" + reason + ")");
            return;
        }
        String msg = plugin.msg(msgKey)
                .replace("{player}", display)
                .replace("{amount}", format(amount));
        if (msg != null && !msg.isBlank() && !msgKey.equals(msg)) {
            broadcast(msg);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!isHolder(event.getPlayer())) {
            return;
        }
        burnOut();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMilk(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.MILK_BUCKET) {
            return;
        }
        Player player = event.getPlayer();
        if (!isHolder(player)) {
            return;
        }
        burnOutWithMessage(plugin.msg("milk").replace("{player}", player.getName()));
    }

    private void applyTag(Player player) {
        if (player == null) {
            return;
        }
        String tagged = chatTagColored() + player.getName();
        if (tagged.length() > 16) {
            tagged = tagged.substring(0, 16);
        }
        player.setPlayerListName(tagged);
    }

    private void clearTag(Player player) {
        if (player == null) {
            return;
        }
        player.setPlayerListName(null);
    }

    private void applyEffects(Player player) {
        if (player == null) {
            return;
        }
        int ticks = Math.max(40, (int) Math.ceil((burnOutAtMs - System.currentTimeMillis()) / 50.0) + 40);
        apply(player, PotionEffectType.REGENERATION, regenAmp, ticks);
        apply(player, PotionEffectType.NIGHT_VISION, nightVisionAmp, ticks);
        apply(player, PotionEffectType.HASTE, hasteAmp, ticks);
    }

    private void clearEffects(Player player) {
        if (player == null) {
            return;
        }
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.HASTE);
    }

    private static void apply(Player player, PotionEffectType type, int amplifier, int ticks) {
        player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true), true);
    }

    /** Invisible LIGHT block that follows the holder so nearby players get real world light. */
    private void updateHolderLight(Player player) {
        if (!holderLightEnabled || player == null || !player.isOnline()) {
            clearHolderLight();
            return;
        }
        Block spot = findLightSpot(player);
        if (spot == null) {
            clearHolderLight();
            return;
        }
        if (hasLightBlock
                && lightWorld == spot.getWorld()
                && lightX == spot.getX()
                && lightY == spot.getY()
                && lightZ == spot.getZ()
                && spot.getType() == Material.LIGHT) {
            return;
        }
        clearHolderLight();
        if (!isReplaceableForLight(spot.getType())) {
            return;
        }
        Light data = (Light) Material.LIGHT.createBlockData();
        data.setLevel(holderLightLevel);
        spot.setBlockData(data, false);
        lightWorld = spot.getWorld();
        lightX = spot.getX();
        lightY = spot.getY();
        lightZ = spot.getZ();
        hasLightBlock = true;
    }

    private void clearHolderLight() {
        if (!hasLightBlock || lightWorld == null) {
            hasLightBlock = false;
            lightWorld = null;
            return;
        }
        Block block = lightWorld.getBlockAt(lightX, lightY, lightZ);
        if (block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
        hasLightBlock = false;
        lightWorld = null;
    }

    private static Block findLightSpot(Player player) {
        Location eye = player.getEyeLocation();
        Block atEyes = eye.getBlock();
        if (isReplaceableForLight(atEyes.getType())) {
            return atEyes;
        }
        Block atFeet = player.getLocation().getBlock();
        if (isReplaceableForLight(atFeet.getType())) {
            return atFeet;
        }
        Block aboveFeet = atFeet.getRelative(0, 1, 0);
        if (isReplaceableForLight(aboveFeet.getType())) {
            return aboveFeet;
        }
        return null;
    }

    private static boolean isReplaceableForLight(Material type) {
        return type == Material.AIR
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR
                || type == Material.LIGHT;
    }

    private Player holderPlayer() {
        return holderId == null ? null : Bukkit.getPlayer(holderId);
    }

    private void broadcast(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Bukkit.broadcastMessage(message);
        if (!discordRelay) {
            return;
        }
        RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
        if (reachout != null) {
            reachout.relayGlobalBroadcast(message, plugin.theme().discordKind());
        }
    }

    private static String format(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 1e-9) {
            return String.valueOf((long) Math.rint(amount));
        }
        return String.format(Locale.US, "%.2f", amount);
    }
}
