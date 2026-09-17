package com.rootrecord.minecraft.rootclaims.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcMapUrls;
import com.rootrecord.minecraft.rootclaims.ClaimKey;
import com.rootrecord.minecraft.rootclaims.ClaimRecord;
import com.rootrecord.minecraft.rootclaims.ClaimBankService;
import com.rootrecord.minecraft.rootclaims.ClaimService;
import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class ClaimCommand implements CommandExecutor, TabCompleter {

    private static final DecimalFormat GOLD = new DecimalFormat("0.###");

    private final RootClaimsPlugin plugin;
    private final ClaimService claims;
    private final Map<UUID, PendingAction> pendingActions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spawnCooldownUntil = new ConcurrentHashMap<>();

    public ClaimCommand(RootClaimsPlugin plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return handleConsole(sender, args);
        }
        if (!player.hasPermission("rootclaims.use") && !player.hasPermission("rootclaims.admin")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            if (plugin.transferOnClaimsCommand()
                    && "claims".equalsIgnoreCase(label)) {
                player.sendMessage(plugin.colorize(plugin.rawMsg("prefix")
                        + "&eReturning to &fplay.rootmc.net&e…"));
                if (!player.performCommand("goto " + plugin.transferOnClaimsPeer())) {
                    player.sendMessage(plugin.colorize(plugin.rawMsg("prefix")
                            + "&cTransfer unavailable. Use &f/goto claims &cor &f/toclaims&c."));
                }
                return true;
            }
            plugin.dashboard().open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(player);
            case "new" -> queueFoundArea(player, args.length >= 2 ? args[1] : null);
            case "claim", "place", "expand" -> queueExpandArea(player);
            case "menu", "gui", "dashboard" -> plugin.dashboard().open(player);
            case "info", "inspect" -> sendInfo(player);
            case "list" -> sendList(player);
            case "lines", "outline", "show" -> toggleLines(player);
            case "spawn", "home" -> handleSpawn(player, args);
            case "set" -> handleSet(player, args);
            case "bank" -> handleBank(player, args);
            case "deposit" -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            case "balance", "bal" -> sendBankInfoForCurrentClaim(player);
            case "toggle" -> handleToggle(player, args);
            case "confirm" -> confirmPending(player);
            case "cancel" -> cancelPending(player);
            case "unclaim", "remove" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
                    queueUnclaimAll(player);
                } else {
                    queueUnclaim(player);
                }
            }
            case "add", "friend", "trust" -> handleTrust(player, args, true);
            case "untrust", "distrust" -> handleTrust(player, args, false);
            case "chest", "community" -> openChest(player);
            case "reload" -> {
                if (!player.hasPermission("rootclaims.admin")) {
                    player.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.reloadAll();
                plugin.ensureAvaSpawnClaim();
                player.sendMessage(plugin.msg("reloaded"));
            }
            case "grant" -> {
                if (!player.hasPermission("rootclaims.admin")) {
                    player.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                handleGrant(player, args, player.getLocation());
            }
            case "status" -> sendStatus(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private boolean handleConsole(CommandSender sender, String[] args) {
        if (args.length == 0
                || args[0].equalsIgnoreCase("help")
                || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            sender.sendMessage(plugin.msg("console-help"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.ensureAvaSpawnClaim();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            case "grant" -> {
                if (Bukkit.getWorlds().isEmpty()) {
                    sender.sendMessage(plugin.msg("claim-spawn-missing-world"));
                    return true;
                }
                handleGrant(sender, args, Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            case "add", "friend", "trust" -> handleConsoleTrust(sender, args, true);
            case "untrust", "distrust" -> handleConsoleTrust(sender, args, false);
            default -> sender.sendMessage(plugin.msg("console-help"));
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        int loaded = claims.allClaims().size();
        int avaAreas = plugin.avaUuid() == null ? 0 : claims.rootsOwnedBy(plugin.avaUuid()).size();
        sender.sendMessage("Root-Claims "
                + plugin.getDescription().getVersion()
                + " · claims " + loaded
                + " · Ava areas " + avaAreas
                + (avaAreas > 0 ? " · /c spawn Ava_Ivy" : " · auto-grant pending"));
    }

    private void handleConsoleTrust(CommandSender sender, String[] args, boolean trust) {
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("console-trust-usage"));
            return;
        }
        UUID ownerId = resolveOfflineId(args[1]);
        String ownerName = args[1].trim();
        if (ownerId == null && plugin.isAvaName(ownerName)) {
            ownerId = plugin.avaUuid();
            ownerName = plugin.avaName();
        }
        if (ownerId == null) {
            sender.sendMessage(plugin.msg("grant-unknown").replace("{player}", args[1]));
            return;
        }
        UUID memberId = resolveOfflineId(args[2]);
        String memberName = args[2].trim();
        if (memberId == null) {
            sender.sendMessage(plugin.msg("grant-unknown").replace("{player}", args[2]));
            return;
        }
        Player onlineMember = Bukkit.getPlayer(memberId);
        if (onlineMember != null) {
            memberName = onlineMember.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(memberId);
            if (off.getName() != null) {
                memberName = off.getName();
            }
        }
        ClaimService.Result result = trust
                ? claims.adminTrust(ownerId, memberId, memberName)
                : claims.adminUntrust(ownerId, memberId);
        if (result.status() == ClaimService.Status.NOT_CLAIMED) {
            sender.sendMessage(plugin.msg("unclaim-none"));
            return;
        }
        sender.sendMessage(plugin.msg(trust ? "trusted" : "untrusted").replace("{player}", memberName));
        sender.sendMessage("Area owner: " + ownerName);
    }

    private UUID resolveOfflineId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim();
        Player online = Bukkit.getPlayerExact(name);
        if (online == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(name)) {
                    return p.getUniqueId();
                }
            }
        } else {
            return online.getUniqueId();
        }
        if (plugin.isAvaName(name) && plugin.avaUuid() != null) {
            return plugin.avaUuid();
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (off.hasPlayedBefore() || off.isOnline()) {
            return off.getUniqueId();
        }
        return off.getUniqueId();
    }

    private void handleGrant(CommandSender sender, String[] args, Location at) {
        if (args.length < 2 || args[1] == null || args[1].isBlank()) {
            sender.sendMessage(plugin.msg("grant-usage"));
            return;
        }
        boolean pub = args.length >= 3 && args[2] != null && args[2].equalsIgnoreCase("public");
        UUID ownerId = null;
        String ownerName = args[1].trim();
        Player online = Bukkit.getPlayerExact(ownerName);
        if (online == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(ownerName)) {
                    online = p;
                    break;
                }
            }
        }
        if (online != null) {
            ownerId = online.getUniqueId();
            ownerName = online.getName();
        } else if (plugin.isAvaName(ownerName)) {
            ownerId = plugin.avaUuid();
            ownerName = plugin.avaName();
            pub = pub || plugin.avaSpawnAutoPublic();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerName);
            if (offline.hasPlayedBefore() || offline.isOnline()) {
                ownerId = offline.getUniqueId();
                if (offline.getName() != null) {
                    ownerName = offline.getName();
                }
            }
        }
        if (ownerId == null) {
            sender.sendMessage(plugin.msg("grant-unknown").replace("{player}", args[1]));
            return;
        }
        if (plugin.isAvaOwner(ownerId) && plugin.avaSpawnAutoPublic()) {
            pub = true;
        }
        if (!claims.rootsOwnedBy(ownerId).isEmpty()) {
            sender.sendMessage(plugin.msg("grant-already").replace("{owner}", ownerName));
            return;
        }
        if (at == null || at.getWorld() == null) {
            sender.sendMessage(plugin.msg("claim-spawn-missing-world"));
            return;
        }
        String areaName = plugin.isAvaOwner(ownerId) ? plugin.avaAreaName() : ownerName;
        ClaimService.Result result = claims.grantArea(ownerId, ownerName, at, areaName, pub, true);
        if (result.status() == ClaimService.Status.OK && result.claim() != null) {
            ClaimRecord claim = result.claim();
            sender.sendMessage(plugin.msg("grant-ok")
                    .replace("{area}", claim.areaNameOrOwner())
                    .replace("{owner}", ownerName)
                    .replace("{x}", String.valueOf(claim.key().x()))
                    .replace("{y}", String.valueOf(claim.key().y()))
                    .replace("{z}", String.valueOf(claim.key().z()))
                    .replace("{public}", pub ? "yes" : "no"));
            return;
        }
        sender.sendMessage(plugin.msg("grant-failed").replace("{status}", String.valueOf(result.status())));
    }

    public void beginClaimFromGui(Player player) {
        if (claims.rootsOwnedBy(player.getUniqueId()).isEmpty()) {
            queueFoundArea(player, null);
        } else {
            queueExpandArea(player);
        }
    }

    public void beginLinesFromGui(Player player) {
        toggleLines(player);
    }

    public void beginSpawnFromGui(Player player) {
        handleSpawn(player, new String[] {"spawn"});
    }

    public void beginHelpFromGui(Player player) {
        sendHelp(player);
    }

    public void beginChestFromGui(Player player) {
        openChest(player);
    }

    private void openChest(Player player) {
        if (plugin.claimChests() == null) {
            player.sendMessage(plugin.msg("disabled"));
            return;
        }
        plugin.claimChests().openFor(player);
    }

    private void queueFoundArea(Player player, String requestedName) {
        ClaimService.Result preview = claims.previewFoundArea(player, requestedName);
        if (!sendPreviewFailure(player, preview)) {
            return;
        }
        double price = preview.amount() > 0 ? preview.amount() : claims.nextAreaFoundingPrice(player.getUniqueId());
        final String name = requestedName;
        queue(player, () -> sendFoundResult(player, claims.foundArea(player, name)));
        String areaLabel = name == null || name.isBlank() ? "auto" : name.trim();
        String confirmKey = price <= 0 ? "confirm-area-found-free" : "confirm-area-found";
        sendConfirmPrompt(player, plugin.msg(confirmKey)
                .replace("{price}", GOLD.format(price))
                .replace("{name}", areaLabel)
                .replace("{seconds}", String.valueOf(plugin.confirmTtlMillis() / 1000L)));
    }

    private void queueExpandArea(Player player) {
        ClaimService.Result preview = claims.previewExpandArea(player);
        if (!sendPreviewFailure(player, preview)) {
            return;
        }
        double price = preview.amount() > 0 ? preview.amount() : 0;
        queue(player, () -> sendClaimResult(player, claims.expandArea(player)));
        sendConfirmPrompt(player, plugin.msg("confirm-claim-expansion")
                .replace("{price}", GOLD.format(price))
                .replace("{seconds}", String.valueOf(plugin.confirmTtlMillis() / 1000L)));
    }

    private boolean sendPreviewFailure(Player player, ClaimService.Result preview) {
        switch (preview.status()) {
            case NEEDS_EDGE -> {
                player.sendMessage(plugin.msg("claim-needs-edge")
                        .replace("{tolerance}", String.valueOf(plugin.edgeToleranceBlocks())));
                return false;
            }
            case NEEDS_AREA -> {
                player.sendMessage(plugin.msg("claim-needs-area"));
                return false;
            }
            case OVERLAPS_OTHER -> {
                player.sendMessage(replace(plugin.msg("claim-overlaps-other"), preview.claim()));
                return false;
            }
            case FOREIGN_TERRITORY -> {
                player.sendMessage(replace(plugin.msg("claim-foreign-territory"), preview.claim())
                        .replace("{buffer}", String.valueOf(plugin.territoryBufferBlocks())));
                return false;
            }
            case BELOW_MINING_FLOOR -> {
                player.sendMessage(plugin.msg("claim-below-mining-floor"));
                return false;
            }
            case AREA_LIMIT, LIMIT_REACHED -> {
                player.sendMessage(plugin.msg("area-limit")
                        .replace("{count}", String.valueOf(preview.count()))
                        .replace("{limit}", String.valueOf(preview.limit())));
                return false;
            }
            case AREA_LOCKED -> {
                player.sendMessage(plugin.msg("area-locked")
                        .replace("{have}", String.valueOf(preview.count()))
                        .replace("{need}", String.valueOf(preview.limit()))
                        .replace("{max}", String.valueOf(plugin.maxLevelPerArea())));
                return false;
            }
            case AREA_LEVEL_MAX -> {
                player.sendMessage(plugin.msg("area-level-max")
                        .replace("{level}", String.valueOf(preview.count()))
                        .replace("{max}", String.valueOf(preview.limit())));
                return false;
            }
            case NAME_TAKEN -> {
                player.sendMessage(plugin.msg("area-name-taken"));
                return false;
            }
            case DISABLED -> {
                player.sendMessage(plugin.msg("disabled"));
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    private void queueClaim(Player player) {
        queueExpandArea(player);
    }

    private void queueUnclaim(Player player) {
        ClaimRecord claim = claims.claimAt(player);
        if (claim == null) {
            player.sendMessage(plugin.msg("unclaim-none"));
            return;
        }
        if (!claims.canManage(player, claim, false)) {
            player.sendMessage(replace(plugin.msg("not-owner"), claim));
            return;
        }
        double refund = claim.paidGold() * (plugin.unclaimRefundPercent() / 100.0);
        queue(player, () -> sendUnclaimResult(player, claims.unclaim(player)));
        sendConfirmPrompt(player, replace(plugin.msg("confirm-unclaim"), claim)
                .replace("{refund}", GOLD.format(refund))
                .replace("{refund_pct}", GOLD.format(plugin.unclaimRefundPercent()))
                .replace("{seconds}", String.valueOf(plugin.confirmTtlMillis() / 1000L)));
    }

    private void queueUnclaimAll(Player player) {
        List<ClaimRecord> owned = claims.ownedBy(player.getUniqueId());
        if (owned.isEmpty()) {
            player.sendMessage(plugin.msg("unclaim-none"));
            return;
        }
        double landRefund = 0;
        for (ClaimRecord claim : owned) {
            landRefund += Math.max(0, claim.paidGold()) * (plugin.unclaimRefundPercent() / 100.0);
        }
        double bankTotal = 0;
        for (ClaimRecord root : claims.rootsOwnedBy(player.getUniqueId())) {
            bankTotal += Math.max(0, claims.bankBalance(root));
        }
        final int count = owned.size();
        queue(player, () -> sendUnclaimAllResult(player, claims.unclaimAll(player)));
        sendConfirmPrompt(player, plugin.msg("confirm-unclaim-all")
                .replace("{count}", String.valueOf(count))
                .replace("{refund}", GOLD.format(landRefund))
                .replace("{bank}", GOLD.format(bankTotal))
                .replace("{refund_pct}", GOLD.format(plugin.unclaimRefundPercent()))
                .replace("{seed}", GOLD.format(plugin.firstClaimBankSeedGold()))
                .replace("{seconds}", String.valueOf(plugin.confirmTtlMillis() / 1000L)));
    }

    private void handleSpawn(Player player, String[] args) {
        long now = System.currentTimeMillis();
        long cooldownUntil = spawnCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (cooldownUntil > now) {
            long seconds = Math.max(1L, (cooldownUntil - now + 999L) / 1000L);
            player.sendMessage(plugin.msg("claim-spawn-cooldown").replace("{seconds}", String.valueOf(seconds)));
            return;
        }
        ClaimRecord target;
        boolean visiting = args.length >= 2 && args[1] != null && !args[1].isBlank();
        if (visiting) {
            UUID ownerId = resolveOwnerUuid(args[1]);
            if (ownerId == null) {
                player.sendMessage(plugin.msg("claim-spawn-player-unknown").replace("{player}", args[1]));
                return;
            }
            String ownerLabel = displayNameFor(ownerId, args[1]);
            if (ownerId.equals(player.getUniqueId())) {
                target = claims.ownedSpawnClaim(player);
            } else {
                target = claims.resolveSpawnClaim(ownerId, player.getUniqueId());
                if (target == null) {
                    if (claims.ownedBy(ownerId).isEmpty()) {
                        player.sendMessage(plugin.msg("claim-spawn-player-none").replace("{player}", ownerLabel));
                    } else {
                        player.sendMessage(plugin.msg("claim-spawn-private").replace("{owner}", ownerLabel));
                    }
                    return;
                }
            }
        } else {
            target = claims.ownedSpawnClaim(player);
        }
        if (target == null) {
            player.sendMessage(plugin.msg("claim-spawn-none"));
            return;
        }
        if (teleportToClaimSpawn(player, target)) {
            spawnCooldownUntil.put(player.getUniqueId(), now + plugin.spawnCooldownMillis());
        }
    }

    private UUID resolveOwnerUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(raw);
        if (online != null) {
            return online.getUniqueId();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(raw)) {
                return p.getUniqueId();
            }
        }
        ClaimRecord named = claims.findOwnerByName(raw);
        if (named != null) {
            return named.ownerId();
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(raw);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        return null;
    }

    private String displayNameFor(UUID ownerId, String fallback) {
        Player online = Bukkit.getPlayer(ownerId);
        if (online != null) {
            return online.getName();
        }
        for (ClaimRecord claim : claims.ownedBy(ownerId)) {
            if (claim.ownerName() != null && !claim.ownerName().isBlank()) {
                return claim.ownerName();
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerId);
        if (offline.getName() != null) {
            return offline.getName();
        }
        return fallback;
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("spawn")) {
            player.sendMessage(plugin.msg("set-usage"));
            return;
        }
        ClaimService.Result result = claims.setSpawn(player);
        switch (result.status()) {
            case OK -> player.sendMessage(replace(plugin.msg("claim-spawn-set"), result.claim()));
            case NOT_CLAIMED -> player.sendMessage(plugin.msg("unclaim-none"));
            case NOT_OWNER -> player.sendMessage(replace(plugin.msg("not-owner"), result.claim()));
            case OUTSIDE_CLAIM -> player.sendMessage(plugin.msg("claim-spawn-outside"));
            default -> player.sendMessage(plugin.msg("set-usage"));
        }
    }

    private boolean teleportToClaimSpawn(Player player, ClaimRecord claim) {
        Location target = claims.spawnLocation(claim);
        if (target == null) {
            player.sendMessage(plugin.msg("claim-spawn-missing-world"));
            return false;
        }
        player.teleport(target);
        String key = claim.ownerId().equals(player.getUniqueId())
                ? "claim-spawn-success"
                : "claim-spawn-success-other";
        player.sendMessage(replace(plugin.msg(key), claim));
        return true;
    }

    private void handleTrust(Player player, String[] args, boolean trust) {
        if (args.length < 2) {
            player.sendMessage(plugin.msg("trust-usage"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        ClaimRecord claim = claims.claimAt(player);
        if (claim == null) {
            player.sendMessage(plugin.msg("unclaim-none"));
            return;
        }
        if (!claims.canManage(player, claim, false)) {
            player.sendMessage(replace(plugin.msg("not-owner"), claim));
            return;
        }
        String name = target.getName() != null ? target.getName() : args[1];
        queue(player, () -> sendTrustResult(player, target, trust, name));
        sendConfirmPrompt(player, replace(plugin.msg(trust ? "confirm-trust" : "confirm-untrust"), claim)
                .replace("{player}", name)
                .replace("{seconds}", String.valueOf(plugin.confirmTtlMillis() / 1000L)));
    }

    private void sendConfirmPrompt(Player player, String body) {
        player.sendMessage(body);
        player.sendMessage(ChatLinks.confirmCancel("/c confirm", "/c cancel"));
    }

    private void sendTrustResult(Player player, OfflinePlayer target, boolean trust, String name) {
        ClaimService.Result result = trust ? claims.trust(player, target) : claims.untrust(player, target);
        if (result.status() == ClaimService.Status.NOT_CLAIMED) {
            player.sendMessage(plugin.msg("unclaim-none"));
            return;
        }
        if (result.status() == ClaimService.Status.NOT_OWNER) {
            player.sendMessage(replace(plugin.msg("not-owner"), result.claim()));
            return;
        }
        player.sendMessage(plugin.msg(trust ? "trusted" : "untrusted").replace("{player}", name));
    }

    private void handleToggle(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.msg("toggle-usage"));
            return;
        }
        ClaimRecord claim = claims.claimAt(player);
        if (claim == null) {
            player.sendMessage(plugin.msg("unclaim-none"));
            return;
        }
        if (!claims.canManage(player, claim, false)) {
            player.sendMessage(replace(plugin.msg("not-owner"), claim));
            return;
        }
        String setting = args[1].toLowerCase(Locale.ROOT);
        Boolean enabled = parseOnOff(args[2]);
        if (enabled == null) {
            player.sendMessage(plugin.msg("toggle-usage"));
            return;
        }
        if (setting.equals("mobs")) {
            claim.mobsAllowed(enabled);
            claims.save();
            claims.recordSettingToggle(player, claim, "mobs", enabled);
            if (!enabled) {
                int removed = claims.despawnHostiles(claim);
                player.sendMessage(plugin.msg("toggle-mobs-off")
                        .replace("{count}", String.valueOf(removed))
                        .replace("{buffer}", String.valueOf(plugin.territoryBufferBlocks())));
            } else {
                player.sendMessage(plugin.msg("toggle-mobs-on"));
            }
            return;
        }
        if (setting.equals("spawn") || setting.equals("public") || setting.equals("public-spawn")) {
            ClaimRecord root = claims.areaRoot(claim);
            root.spawnPublic(enabled);
            claims.save();
            claims.recordSettingToggle(player, root, "public-spawn", enabled);
            player.sendMessage(plugin.msg(enabled ? "toggle-spawn-public" : "toggle-spawn-private"));
            return;
        }
        player.sendMessage(plugin.msg("toggle-usage"));
    }

    private void handleBank(Player player, String[] args) {
        ClaimRecord claim = bankTarget(player);
        if (claim == null) {
            player.sendMessage(plugin.msg("bank-no-claim"));
            return;
        }
        if (args.length == 1
                || args[1].equalsIgnoreCase("info")
                || args[1].equalsIgnoreCase("balance")
                || args[1].equalsIgnoreCase("bal")) {
            sendBankInfo(player, claim);
            return;
        }
        if (args[1].equalsIgnoreCase("withdraw")) {
            if (args.length < 3) {
                player.sendMessage(plugin.msg("bank-usage"));
                return;
            }
            runWithdraw(player, claim, args[2]);
            return;
        }
        if (!args[1].equalsIgnoreCase("deposit") || args.length < 3) {
            player.sendMessage(plugin.msg("bank-usage"));
            return;
        }
        runDeposit(player, claim, args[2]);
    }

    /** `/c withdraw <amount|all>` — same as `/c bank withdraw …`. */
    private void handleWithdraw(Player player, String[] args) {
        ClaimRecord claim = bankTarget(player);
        if (claim == null) {
            player.sendMessage(plugin.msg("bank-no-claim"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("bank-usage"));
            return;
        }
        runWithdraw(player, claim, args[1]);
    }

    private void runWithdraw(Player player, ClaimRecord claim, String amountRaw) {
        Double amount = resolveWithdrawAmount(player, claim, amountRaw);
        if (amount == null) {
            player.sendMessage(plugin.msg("bank-usage"));
            return;
        }
        if (amount < GoldMoney.MIN_AMOUNT) {
            player.sendMessage(plugin.msg("bank-withdraw-empty"));
            return;
        }
        ClaimBankService.Result result = claims.withdrawFromBank(player, claim, amount);
        switch (result.status()) {
            case OK -> player.sendMessage(replace(plugin.msg("bank-withdraw-success"), claim)
                    .replace("{amount}", GOLD.format(result.amountG()))
                    .replace("{balance}", GOLD.format(result.balanceG())));
            case CANNOT_AFFORD -> player.sendMessage(plugin.msg("bank-cannot-withdraw")
                    .replace("{amount}", GOLD.format(result.amountG())));
            case NO_ECONOMY, FAILED -> player.sendMessage(plugin.msg("bank-withdraw-failed"));
        }
    }

    /** Parses a positive amount, or claim-bank balance when {@code all}. Null = bad input. */
    private Double resolveWithdrawAmount(Player player, ClaimRecord claim, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("all".equalsIgnoreCase(raw.trim())) {
            return GoldMoney.round(claims.bankBalance(claim));
        }
        return parseAmount(raw);
    }

    /** `/c deposit <amount|all>` — same as `/c bank deposit …`. */
    private void handleDeposit(Player player, String[] args) {
        ClaimRecord claim = bankTarget(player);
        if (claim == null) {
            player.sendMessage(plugin.msg("bank-no-claim"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("bank-usage"));
            return;
        }
        runDeposit(player, claim, args[1]);
    }

    private void runDeposit(Player player, ClaimRecord claim, String amountRaw) {
        Double amount = resolveDepositAmount(player, amountRaw);
        if (amount == null) {
            player.sendMessage(plugin.msg("bank-usage"));
            return;
        }
        if (amount < GoldMoney.MIN_AMOUNT) {
            player.sendMessage(plugin.msg("bank-deposit-empty"));
            return;
        }
        ClaimBankService.Result result = claims.depositToBank(player, claim, amount);
        switch (result.status()) {
            case OK -> player.sendMessage(replace(plugin.msg("bank-deposit-success"), claim)
                    .replace("{amount}", GOLD.format(result.amountG()))
                    .replace("{balance}", GOLD.format(result.balanceG())));
            case CANNOT_AFFORD -> player.sendMessage(plugin.msg("bank-cannot-afford")
                    .replace("{amount}", GOLD.format(result.amountG())));
            case NO_ECONOMY, FAILED -> player.sendMessage(plugin.msg("bank-deposit-failed"));
        }
    }

    /** Parses a positive amount, or wallet balance when {@code all}. Null = bad input. */
    private Double resolveDepositAmount(Player player, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("all".equalsIgnoreCase(raw.trim())) {
            RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
            if (economy == null) {
                return 0d;
            }
            return GoldMoney.round(economy.balance(player.getUniqueId()));
        }
        return parseAmount(raw);
    }

    private void sendBankInfoForCurrentClaim(Player player) {
        ClaimRecord claim = bankTarget(player);
        if (claim == null) {
            player.sendMessage(plugin.msg("bank-no-claim"));
            return;
        }
        sendBankInfo(player, claim);
    }

    private void queue(Player player, Runnable action) {
        pendingActions.put(player.getUniqueId(), PendingAction.of(player, action, plugin.confirmTtlMillis()));
    }

    private void confirmPending(Player player) {
        PendingAction pending = pendingActions.remove(player.getUniqueId());
        if (pending == null) {
            // Double-click /Confirm after success - stay quiet.
            return;
        }
        if (pending.expired()) {
            player.sendMessage(plugin.msg("confirm-expired"));
            return;
        }
        if (!pending.sameBlock(player)) {
            player.sendMessage(plugin.msg("confirm-moved"));
            return;
        }
        pending.action().run();
    }

    private void cancelPending(Player player) {
        if (pendingActions.remove(player.getUniqueId()) == null) {
            return;
        }
        player.sendMessage(plugin.msg("confirm-cancelled"));
    }

    private void sendFoundResult(Player player, ClaimService.Result result) {
        switch (result.status()) {
            case OK -> {
                String key = result.amount() > 0 ? "area-created-paid" : "area-created";
                player.sendMessage(replace(plugin.msg(key), result.claim())
                        .replace("{price}", GOLD.format(result.amount()))
                        .replace("{area}", result.claim() == null ? "" : result.claim().areaNameOrOwner()));
                sendClaimMapLink(player, result.claim());
                sendNextPrice(player);
            }
            case AREA_LIMIT, LIMIT_REACHED -> player.sendMessage(plugin.msg("area-limit")
                    .replace("{count}", String.valueOf(result.count()))
                    .replace("{limit}", String.valueOf(result.limit())));
            case AREA_LOCKED -> player.sendMessage(plugin.msg("area-locked")
                    .replace("{have}", String.valueOf(result.count()))
                    .replace("{need}", String.valueOf(result.limit()))
                    .replace("{max}", String.valueOf(plugin.maxLevelPerArea())));
            case NAME_TAKEN -> player.sendMessage(plugin.msg("area-name-taken"));
            case DISABLED -> player.sendMessage(plugin.msg("disabled"));
            case ALREADY_CLAIMED -> player.sendMessage(replace(plugin.msg("claim-already-owned"), result.claim()));
            case OVERLAPS_OTHER -> player.sendMessage(replace(plugin.msg("claim-overlaps-other"), result.claim()));
            case FOREIGN_TERRITORY -> player.sendMessage(replace(plugin.msg("claim-foreign-territory"), result.claim())
                    .replace("{buffer}", String.valueOf(plugin.territoryBufferBlocks())));
            case BELOW_MINING_FLOOR -> player.sendMessage(plugin.msg("claim-below-mining-floor"));
            case NO_ECONOMY, CHARGE_FAILED -> player.sendMessage(plugin.msg("no-economy"));
            case CANNOT_AFFORD -> player.sendMessage(plugin.msg("cannot-afford")
                    .replace("{price}", GOLD.format(result.amount())));
            default -> sendHelp(player);
        }
    }

    private void sendClaimResult(Player player, ClaimService.Result result) {
        switch (result.status()) {
            case OK -> {
                String key = result.amount() > 0 ? "claim-created-paid" : "claim-created";
                player.sendMessage(replace(plugin.msg(key), result.claim())
                        .replace("{price}", GOLD.format(result.amount()))
                        .replace("{level}", String.valueOf(result.count())));
                sendClaimMapLink(player, result.claim());
                sendNextPrice(player);
            }
            case DISABLED -> player.sendMessage(plugin.msg("disabled"));
            case NEEDS_AREA -> player.sendMessage(plugin.msg("claim-needs-area"));
            case ALREADY_CLAIMED -> player.sendMessage(replace(plugin.msg("claim-already-owned"), result.claim()));
            case NEEDS_EDGE -> player.sendMessage(plugin.msg("claim-needs-edge")
                    .replace("{tolerance}", String.valueOf(plugin.edgeToleranceBlocks())));
            case OVERLAPS_OTHER -> player.sendMessage(replace(plugin.msg("claim-overlaps-other"), result.claim()));
            case FOREIGN_TERRITORY -> player.sendMessage(replace(plugin.msg("claim-foreign-territory"), result.claim())
                    .replace("{buffer}", String.valueOf(plugin.territoryBufferBlocks())));
            case BELOW_MINING_FLOOR -> player.sendMessage(plugin.msg("claim-below-mining-floor"));
            case AREA_LEVEL_MAX -> player.sendMessage(plugin.msg("area-level-max")
                    .replace("{level}", String.valueOf(result.count()))
                    .replace("{max}", String.valueOf(result.limit())));
            case LIMIT_REACHED, AREA_LIMIT -> player.sendMessage(plugin.msg("area-limit")
                    .replace("{count}", String.valueOf(result.count()))
                    .replace("{limit}", String.valueOf(result.limit())));
            case NO_ECONOMY -> player.sendMessage(plugin.msg("no-economy"));
            case CANNOT_AFFORD -> player.sendMessage(plugin.msg("cannot-afford")
                    .replace("{price}", GOLD.format(result.amount())));
            case CHARGE_FAILED -> player.sendMessage(plugin.msg("no-economy"));
            case CLAIM_BANK_CANNOT_AFFORD -> player.sendMessage(replace(plugin.msg("claim-bank-cannot-afford"), result.claim())
                    .replace("{price}", GOLD.format(result.amount())));
            case CLAIM_BANK_CHARGE_FAILED -> player.sendMessage(plugin.msg("claim-bank-charge-failed"));
            default -> sendHelp(player);
        }
    }

    private void sendUnclaimResult(Player player, ClaimService.Result result) {
        switch (result.status()) {
            case OK, OK_REFUND_FAILED, OK_REFUND_BANK -> {
                player.sendMessage(plugin.msg("unclaim-success"));
                if (result.amount() > 0 && result.status() == ClaimService.Status.OK) {
                    player.sendMessage(plugin.msg("unclaim-refund").replace("{amount}", GOLD.format(result.amount())));
                }
                if (result.amount() > 0 && result.status() == ClaimService.Status.OK_REFUND_BANK) {
                    player.sendMessage(plugin.msg("unclaim-bank-refund").replace("{amount}", GOLD.format(result.amount())));
                }
            }
            case NOT_CLAIMED -> player.sendMessage(plugin.msg("unclaim-none"));
            case NOT_OWNER -> player.sendMessage(replace(plugin.msg("not-owner"), result.claim()));
            case HAS_CHILDREN -> player.sendMessage(plugin.msg("unclaim-has-children"));
            default -> sendHelp(player);
        }
    }

    private void sendUnclaimAllResult(Player player, ClaimService.Result result) {
        switch (result.status()) {
            case OK, OK_REFUND_FAILED -> {
                player.sendMessage(plugin.msg("unclaim-all-success")
                        .replace("{count}", String.valueOf(result.count())));
                if (result.amount() > 0) {
                    player.sendMessage(plugin.msg("unclaim-all-land-refund")
                            .replace("{amount}", GOLD.format(result.amount()))
                            .replace("{refund_pct}", GOLD.format(plugin.unclaimRefundPercent()))
                            .replace("{seed}", GOLD.format(plugin.firstClaimBankSeedGold())));
                }
                if (result.secondaryAmount() > 0) {
                    player.sendMessage(plugin.msg("unclaim-all-bank-return")
                            .replace("{amount}", GOLD.format(result.secondaryAmount())));
                }
                if (result.status() == ClaimService.Status.OK_REFUND_FAILED) {
                    player.sendMessage(plugin.msg("unclaim-all-refund-failed"));
                }
                sendNextPrice(player);
            }
            case NOT_CLAIMED -> player.sendMessage(plugin.msg("unclaim-none"));
            case CLAIM_BANK_CHARGE_FAILED -> player.sendMessage(plugin.msg("claim-bank-charge-failed"));
            default -> sendHelp(player);
        }
    }

    private void sendClaimMapLink(Player player, ClaimRecord claim) {
        if (claim == null) {
            return;
        }
        ClaimKey key = claim.key();
        String base = RootMcMapUrls.resolveBaseUrl(plugin);
        String mapId = RootMcMapUrls.bluemapMapId(plugin, player.getWorld());
        String url = RootMcMapUrls.withCoordsAnchor(base, mapId, key.x(), key.y(), key.z());
        String header = plugin.msg("claim-map-link");
        if (header != null && !header.isBlank()) {
            player.sendMessage(header
                    .replace("{x}", String.valueOf(key.x()))
                    .replace("{y}", String.valueOf(key.y()))
                    .replace("{z}", String.valueOf(key.z()))
                    .replace("{url}", url));
        }
        player.sendMessage(ChatLinks.labelDashUrl("[View claim on map]", url));
    }

    private void sendInfo(Player player) {
        if (player.getLocation().getBlockY() < ClaimRecord.MINING_FLOOR_Y) {
            player.sendMessage(plugin.msg("info-underworld"));
            return;
        }
        ClaimRecord claim = claims.claimAt(player);
        if (claim != null) {
            player.sendMessage(replace(plugin.msg("info-claimed"), claim)
                    .replace("{area}", claims.areaRoot(claim).areaNameOrOwner())
                    .replace("{level}", String.valueOf(claims.areaLevel(claim)))
                    .replace("{max}", String.valueOf(plugin.maxLevelPerArea()))
                    .replace("{radius}", String.valueOf(claim.radiusBlocks()))
                    .replace("{buffer}", String.valueOf(plugin.territoryBufferBlocks()))
                    .replace("{mobs}", claim.mobsAllowed() ? "on" : "off")
                    .replace("{spawn}", claims.areaRoot(claim).spawnPublic() ? "public" : "private")
                    .replace("{bank}", GOLD.format(claims.bankBalance(claim)))
                    .replace("{trusted}", trustedList(claims.areaRoot(claim))));
            return;
        }
        ClaimRecord territory = claims.territoryAt(player);
        if (territory != null) {
            player.sendMessage(replace(plugin.msg("info-territory"), territory)
                    .replace("{buffer}", String.valueOf(plugin.territoryBufferBlocks())));
            return;
        }
        player.sendMessage(plugin.msg("info-wild"));
    }

    private void sendBankInfo(Player player, ClaimRecord claim) {
        player.sendMessage(replace(plugin.msg("bank-info"), claim)
                .replace("{balance}", GOLD.format(claims.bankBalance(claim)))
                .replace("{price}", GOLD.format(claims.nextClaimPrice(player.getUniqueId())))
                .replace("{account}", claim.bankAccountName())
                .replace("{claim}", claims.bankDisplayName(claim)));
    }

    private void sendList(Player player) {
        List<ClaimRecord> roots = claims.rootsOwnedBy(player.getUniqueId());
        if (roots.isEmpty()) {
            player.sendMessage(plugin.msg("list-empty"));
            sendNextPrice(player);
            return;
        }
        player.sendMessage(plugin.msg("list-header").replace("{count}", String.valueOf(roots.size())));
        for (ClaimRecord root : roots) {
            int level = claims.areaLevel(root);
            player.sendMessage(plugin.colorize(plugin.rawMsg("list-line")
                    .replace("{area}", root.areaNameOrOwner())
                    .replace("{level}", String.valueOf(level))
                    .replace("{max}", String.valueOf(plugin.maxLevelPerArea()))
                    .replace("{world}", root.key().world())
                    .replace("{x}", String.valueOf(root.key().x()))
                    .replace("{y}", String.valueOf(root.key().y()))
                    .replace("{z}", String.valueOf(root.key().z()))
                    .replace("{radius}", String.valueOf(root.radiusBlocks()))
                    .replace("{bank}", GOLD.format(claims.bankBalance(root)))));
        }
        sendNextPrice(player);
    }

    private void sendHelp(Player player) {
        String buffer = String.valueOf(plugin.territoryBufferBlocks());
        for (String line : plugin.rawList("help")) {
            player.sendMessage(plugin.colorize(line.replace("{buffer}", buffer)));
        }
        sendNextPrice(player);
    }

    private void toggleLines(Player player) {
        boolean enabled = plugin.toggleOutline(player);
        player.sendMessage(plugin.msg(enabled ? "lines-on" : "lines-off"));
    }

    private static Boolean parseOnOff(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "on", "true", "yes", "allow", "allowed", "public", "open" -> Boolean.TRUE;
            case "off", "false", "no", "deny", "denied", "private", "closed" -> Boolean.FALSE;
            default -> null;
        };
    }

    private ClaimRecord bankTarget(Player player) {
        ClaimRecord current = claims.claimAt(player);
        if (current != null && claims.canManage(player, current, true)) {
            return claims.areaRoot(current);
        }
        return claims.ownedSpawnClaim(player);
    }

    private static Double parseAmount(String raw) {
        try {
            double amount = Double.parseDouble(raw);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void sendNextPrice(Player player) {
        player.sendMessage(plugin.msg("next-price").replace("{price}", GOLD.format(claims.nextClaimPrice(player.getUniqueId()))));
    }

    private static String replace(String raw, ClaimRecord claim) {
        String owner = claim == null ? "unknown" : claim.ownerName();
        return raw.replace("{owner}", owner);
    }

    private static String trustedList(ClaimRecord claim) {
        if (claim.trusted().isEmpty()) {
            return "none";
        }
        return String.join(", ", claim.trusted().values());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Stream.of("claim", "expand", "new", "menu", "confirm", "cancel", "spawn", "set", "bank", "deposit", "withdraw", "balance", "bal", "chest", "toggle", "info", "list", "lines", "unclaim", "add", "trust", "untrust", "help", "reload", "grant", "status").toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return filter(args[1], List.of("spawn"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("unclaim") || args[0].equalsIgnoreCase("remove"))) {
            return filter(args[1], List.of("all"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("deposit")) {
            return filter(args[1], List.of("all"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("withdraw")) {
            return filter(args[1], List.of("all"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bank")) {
            return filter(args[1], List.of("info", "balance", "bal", "deposit", "withdraw"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bank")
                && (args[1].equalsIgnoreCase("deposit") || args[1].equalsIgnoreCase("withdraw"))) {
            return filter(args[2], List.of("all"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return filter(args[1], List.of("mobs", "spawn", "public"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("toggle")
                && (args[1].equalsIgnoreCase("mobs")
                        || args[1].equalsIgnoreCase("spawn")
                        || args[1].equalsIgnoreCase("public")
                        || args[1].equalsIgnoreCase("public-spawn"))) {
            return filter(args[2], List.of("on", "off"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("grant")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            if (!names.contains("Ava_Ivy")) {
                names.add("Ava_Ivy");
            }
            return filter(args[1], names);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("grant")) {
            return filter(args[2], List.of("public"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("home"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(args[1], names);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("friend") || args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(args[1], names);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private record PendingAction(String world, int x, int y, int z, long expiresAtMillis, Runnable action) {
        static PendingAction of(Player player, Runnable action, long ttlMillis) {
            return new PendingAction(
                    player.getWorld().getName(),
                    player.getLocation().getBlockX(),
                    player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ(),
                    System.currentTimeMillis() + ttlMillis,
                    action);
        }

        boolean expired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }

        boolean sameBlock(Player player) {
            return player.getWorld().getName().equals(world)
                    && player.getLocation().getBlockX() == x
                    && player.getLocation().getBlockY() == y
                    && player.getLocation().getBlockZ() == z;
        }
    }
}
