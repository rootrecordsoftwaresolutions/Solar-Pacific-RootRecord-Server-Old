package com.rootrecord.minecraft.rootrewards.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootstat.RootStatBridge;
import com.rootrecord.minecraft.rootstat.cloud.CloudApiClient;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * /proposal &lt;message&gt; — 64 G to Server Reserve; queues for Ava to publish as Council proposal.
 */
public final class ProposalCommand implements CommandExecutor {

    public static final double FEE_G = 64.0;

    private final RootRewardsPlugin plugin;

    public ProposalCommand(RootRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            ChatUi.tip(player, "Usage: /proposal <your idea>");
            ChatUi.tip(player, "Any linked player can propose — " + (int) FEE_G + " G → Server Reserve.");
            ChatUi.tip(player, "Ava publishes when online. Council voters (Vote Shards) cast the votes.");
            ChatUi.tip(player, "Verified players can discuss in the Discord thread after it posts.");
            ChatUi.tip(player, "Voters earn +3 G once per proposal when their vote is recorded.");
            ChatUi.links(player, "Council", "https://rootmc.net/council/", "Terms", "https://rootmc.net/terms/");
            return true;
        }
        String message = String.join(" ", args).trim();
        if (message.length() < 12) {
            ChatUi.entry(player, "Proposal", "message too short", "open");
            return true;
        }
        if (message.length() > 3500) {
            ChatUi.entry(player, "Proposal", "message too long (max 3500)", "open");
            return true;
        }

        Plugin rootmc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (!(rootmc instanceof RootStatBridge bridge) || !rootmc.isEnabled()) {
            ChatUi.entry(player, "Proposal", "RootMC cloud unavailable", "open");
            return true;
        }
        if (!bridge.config().hasServerCredentials()) {
            ChatUi.entry(player, "Proposal", "server not linked to API", "open");
            return true;
        }

        ChatUi.entry(player, "Proposal", "charging " + (int) FEE_G + " G · queueing for Ava…", "open");
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            boolean charged = chargeReserve(player.getUniqueId(), player.getName());
            if (!charged) {
                Bukkit.getScheduler().runTask(plugin.host(), () -> {
                    if (player.isOnline()) {
                        ChatUi.entry(player, "Proposal", "need " + (int) FEE_G + " G in wallet", "open");
                    }
                });
                return;
            }
            try {
                CloudApiClient.IngameProposalResult result =
                        bridge.cloud().submitIngameProposal(
                                player.getUniqueId().toString(), player.getName(), message);
                if (!result.ok()) {
                    if (result.refund()) {
                        refundReserve(player.getUniqueId(), player.getName());
                    }
                    Bukkit.getScheduler().runTask(plugin.host(), () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        String detail = result.detail() != null && !result.detail().isBlank()
                                ? result.detail().replace("**", "")
                                : "submission failed";
                        ChatUi.entry(player, "Proposal", detail, "open");
                        if (result.refund()) {
                            ChatUi.tip(player, (int) FEE_G + " G refunded to your wallet.");
                        }
                    });
                    return;
                }
                Bukkit.getScheduler().runTask(plugin.host(), () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    ChatUi.entry(player, "Proposal", "queued · −" + (int) FEE_G + " G to reserve", "done");
                    if (result.ideaId() != null && !result.ideaId().isBlank()) {
                        ChatUi.tip(player, result.ideaId() + " — Ava will publish the formal proposal");
                    }
                    if (result.avaNote() != null && !result.avaNote().isBlank()) {
                        ChatUi.tip(player, "Ava: " + result.avaNote());
                    } else {
                        ChatUi.tip(player, "If Ava is offline, she picks this up from the official list when she's back.");
                    }
                    ChatUi.links(player, "Council", "https://rootmc.net/council/", "Terms", "https://rootmc.net/terms/");
                    ChatUi.tip(player, "After it posts: verified players can talk in the Discord thread.");
                });
            } catch (Exception ex) {
                refundReserve(player.getUniqueId(), player.getName());
                Bukkit.getScheduler().runTask(plugin.host(), () -> {
                    if (player.isOnline()) {
                        ChatUi.entry(player, "Proposal", "cloud error — " + (int) FEE_G + " G refunded", "open");
                        plugin.getLogger().warning("In-game proposal failed: " + ex.getMessage());
                    }
                });
            }
        });
        return true;
    }

    private boolean chargeReserve(UUID uuid, String name) {
        try {
            Plugin eco = Bukkit.getPluginManager().getPlugin("Root-Economy");
            if (eco != null && eco.isEnabled()) {
                Object ok = eco.getClass()
                        .getMethod("donateToReserve", UUID.class, String.class, double.class)
                        .invoke(eco, uuid, name, FEE_G);
                if (Boolean.TRUE.equals(ok)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("donateToReserve failed: " + ex.getMessage());
        }
        // Fallback: withdraw + creditTreasury DONATION-style via settleClosedLoop if available
        try {
            var economy = RootMcEconomyResolver.resolve(plugin.host());
            RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin.host());
            if (economy == null || treasury == null) {
                return false;
            }
            if (economy.balance(uuid) + 1e-9 < FEE_G) {
                return false;
            }
            if (!economy.withdraw(uuid, FEE_G)) {
                return false;
            }
            treasury.settleClosedLoopPayment(uuid, name, FEE_G, "ingame_proposal");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Proposal fee fallback failed: " + ex.getMessage());
            return false;
        }
    }

    private void refundReserve(UUID uuid, String name) {
        try {
            RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin.host());
            if (treasury == null) {
                return;
            }
            treasury.grantToPlayer(
                    uuid,
                    name,
                    FEE_G,
                    treasury.treasuryUuid(),
                    treasury.treasuryUsername(),
                    "ingame_proposal_refund");
        } catch (Exception ex) {
            plugin.getLogger().warning("Proposal refund failed for " + name + ": " + ex.getMessage());
        }
    }
}
