package com.rootrecord.minecraft.rootreferrals.command;

import com.rootrecord.minecraft.rootreferrals.RootReferralsPlugin;
import com.rootrecord.minecraft.rootreferrals.service.ClaimService;
import com.rootrecord.minecraft.rootreferrals.service.RewardService;
import com.rootrecord.minecraft.rootreferrals.store.ReferralStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class RootCommand implements CommandExecutor, TabCompleter {

    private final RootReferralsPlugin plugin;

    public RootCommand(RootReferralsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase(Locale.ROOT);
        if ("rootreferrals".equals(cmdName)) {
            return handleRootReferralsAdmin(sender, args);
        }
        if (args.length == 0) {
            return sendUsage(sender);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "code" -> handleCode(sender);
            case "claim" -> handleClaim(sender, args);
            case "info" -> handleInfo(sender, args);
            case "tree" -> handleTree(sender);
            case "top" -> handleTop(sender);
            case "admin" -> handleAdmin(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        };
    }

    private boolean handleRootReferralsAdmin(CommandSender sender, String[] args) {
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            return handleReload(sender);
        }
        sender.sendMessage(plugin.colorize("&eUsage: /rootreferrals reload"));
        return true;
    }

    private boolean handleCode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!sender.hasPermission("rootreferrals.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        ReferralStore.ReferralPlayer existing = plugin.store().findPlayer(player.getUniqueId()).orElse(null);
        ReferralStore.ReferralPlayer profile = plugin.claimService().showOrCreateCode(player);
        if (profile == null) {
            sender.sendMessage(plugin.colorize("&cCould not create referral profile."));
            return true;
        }
        String key = existing == null ? "code-created" : "code";
        sender.sendMessage(plugin.msg(key).replace("{code}", profile.code()));
        return true;
    }

    private boolean handleClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!sender.hasPermission("rootreferrals.claim")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("usage-claim"));
            return true;
        }
        ClaimService.ClaimResult result = plugin.claimService().claim(player, args[1]);
        switch (result) {
            case SUCCESS -> {
                long remaining = Math.max(0, plugin.referralsConfig().playtimeSeconds()
                        - plugin.playtimeHook().totalSeconds(player.getUniqueId()));
                long hours = TimeUnit.SECONDS.toHours(remaining);
                sender.sendMessage(plugin.msg("claim-success").replace("{hours}", String.valueOf(hours)));
            }
            case SELF -> sender.sendMessage(plugin.msg("claim-self"));
            case ALREADY -> sender.sendMessage(plugin.msg("claim-already"));
            case UNKNOWN -> sender.sendMessage(plugin.msg("claim-unknown"));
            case WINDOW -> sender.sendMessage(plugin.msg("claim-window")
                    .replace("{hours}", String.valueOf(plugin.referralsConfig().claimWithinHours())));
            case NOT_LINKED -> sender.sendMessage(plugin.msg("claim-not-linked"));
            default -> sender.sendMessage(plugin.colorize("&cCould not claim referral."));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootreferrals.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        UUID targetUuid;
        String targetName;
        if (args.length >= 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            targetUuid = target.getUniqueId();
            targetName = target.getName() != null ? target.getName() : args[1];
        } else if (sender instanceof Player player) {
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        } else {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        Optional<ReferralStore.ReferralPlayer> profileOpt = plugin.store().findPlayer(targetUuid);
        if (profileOpt.isEmpty()) {
            sender.sendMessage(plugin.msg("info-none"));
            return true;
        }
        ReferralStore.ReferralPlayer profile = profileOpt.get();
        int pending = plugin.store().countPendingByReferrer(targetUuid);
        sender.sendMessage(plugin.msg("info-header").replace("{player}", targetName));
        sender.sendMessage(plugin.msg("info-code").replace("{code}", profile.code()));
        sender.sendMessage(plugin.msg("info-qualified").replace("{qualified}", String.valueOf(profile.totalQualified())));
        sender.sendMessage(plugin.msg("info-earned").replace("{earned}", RewardService.formatGold(profile.totalEarnedG())));
        sender.sendMessage(plugin.msg("info-pending").replace("{pending}", String.valueOf(pending)));
        return true;
    }

    private boolean handleTree(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!sender.hasPermission("rootreferrals.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        int depth = plugin.referralsConfig().maxDepth();
        sender.sendMessage(plugin.msg("tree-header").replace("{depth}", String.valueOf(depth)));
        List<ReferralStore.ReferralEdge> edges = plugin.store().edgesByReferrer(player.getUniqueId(), null);
        if (edges.isEmpty()) {
            sender.sendMessage(plugin.msg("tree-empty"));
            return true;
        }
        for (ReferralStore.ReferralEdge edge : edges) {
            String recruitName = displayName(edge.recruitUuid());
            sender.sendMessage(plugin.msg("tree-line")
                    .replace("{player}", recruitName)
                    .replace("{status}", edge.status()));
        }
        return true;
    }

    private boolean handleTop(CommandSender sender) {
        if (!sender.hasPermission("rootreferrals.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        sender.sendMessage(plugin.msg("top-header"));
        List<ReferralStore.TopReferrerRow> rows = plugin.store().topReferrers(10);
        if (rows.isEmpty()) {
            sender.sendMessage(plugin.msg("top-empty"));
            return true;
        }
        int rank = 1;
        for (ReferralStore.TopReferrerRow row : rows) {
            sender.sendMessage(plugin.msg("top-line")
                    .replace("{rank}", String.valueOf(rank++))
                    .replace("{player}", row.displayName())
                    .replace("{qualified}", String.valueOf(row.totalQualified()))
                    .replace("{earned}", RewardService.formatGold(row.totalEarnedG())));
        }
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rootreferrals.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&eUsage: /root admin <inspect|unlink|purge|qualify|reload> ..."));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "unlink" -> handleUnlink(sender, args);
            case "purge" -> handlePurge(sender, args);
            case "qualify" -> handleQualify(sender, args);
            default -> {
                sender.sendMessage(plugin.colorize("&eUsage: /root admin <inspect|unlink|purge|qualify|reload> ..."));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rootreferrals.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(plugin.msg("admin-reloaded"));
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.colorize("&eUsage: /root admin inspect <player>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        Optional<ReferralStore.ReferralPlayer> profileOpt = plugin.store().findPlayer(target.getUniqueId());
        if (profileOpt.isEmpty()) {
            sender.sendMessage(plugin.msg("info-none"));
            return true;
        }
        ReferralStore.ReferralPlayer profile = profileOpt.get();
        int pending = plugin.store().countPendingByReferrer(target.getUniqueId());
        sender.sendMessage(plugin.msg("admin-inspect")
                .replace("{player}", displayName(target.getUniqueId()))
                .replace("{code}", profile.code())
                .replace("{qualified}", String.valueOf(profile.totalQualified()))
                .replace("{earned}", RewardService.formatGold(profile.totalEarnedG()))
                .replace("{pending}", String.valueOf(pending)));
        return true;
    }

    private boolean handleUnlink(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.colorize("&eUsage: /root admin unlink <player>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        boolean ok = plugin.store().unlinkEdge(target.getUniqueId());
        if (ok) {
            sender.sendMessage(plugin.msg("admin-unlinked").replace("{player}", displayName(target.getUniqueId())));
        } else {
            sender.sendMessage(plugin.colorize("&cNo recruit edge found."));
        }
        return true;
    }

    private boolean handlePurge(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.colorize("&eUsage: /root admin purge <player>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        plugin.store().purgePlayer(target.getUniqueId());
        sender.sendMessage(plugin.msg("admin-purged").replace("{player}", displayName(target.getUniqueId())));
        return true;
    }

    private boolean handleQualify(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.colorize("&eUsage: /root admin qualify <player>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        boolean ok = plugin.qualifyService().forceQualify(target.getUniqueId());
        if (ok) {
            sender.sendMessage(plugin.msg("admin-qualified").replace("{player}", displayName(target.getUniqueId())));
        } else {
            sender.sendMessage(plugin.colorize("&cCould not qualify recruit edge."));
        }
        return true;
    }

    private boolean sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.colorize("&e/root code &7| &eclaim <code> &7| &einfo &7| &etree &7| &etop"));
        return true;
    }

    private String displayName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return uuid.toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String cmdName = command.getName().toLowerCase(Locale.ROOT);
        if ("rootreferrals".equals(cmdName)) {
            if (args.length == 1) {
                out.add("reload");
            }
            return out;
        }
        if (args.length == 1) {
            out.add("code");
            out.add("claim");
            out.add("info");
            out.add("tree");
            out.add("top");
            if (sender.hasPermission("rootreferrals.admin")) {
                out.add("admin");
                out.add("reload");
            }
            return out;
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0]) && sender.hasPermission("rootreferrals.admin")) {
            out.add("inspect");
            out.add("unlink");
            out.add("purge");
            out.add("qualify");
            out.add("reload");
            return out;
        }
        return out;
    }
}
