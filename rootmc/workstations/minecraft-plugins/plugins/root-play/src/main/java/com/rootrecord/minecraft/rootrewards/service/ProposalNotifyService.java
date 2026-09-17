package com.rootrecord.minecraft.rootrewards.service;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootstat.RootStatBridge;
import com.rootrecord.minecraft.rootstat.cloud.CloudApiClient;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Remind online players when Council proposals are open to vote (+3 G each). */
public final class ProposalNotifyService {

    private static final long TIP_COOLDOWN_MS = 45L * 60L * 1000L;

    private final RootRewardsPlugin plugin;
    private final Map<UUID, Long> lastTipAt = new ConcurrentHashMap<>();

    public ProposalNotifyService(RootRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    public void remindOnlinePlayers() {
        Player[] online = Bukkit.getOnlinePlayers().toArray(Player[]::new);
        if (online.length == 0) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            CloudApiClient.OpenGovernancePolls polls = fetchPolls();
            if (polls == null || polls.count() <= 0) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Player player : online) {
                Long last = lastTipAt.get(player.getUniqueId());
                if (last != null && now - last < TIP_COOLDOWN_MS) {
                    continue;
                }
                lastTipAt.put(player.getUniqueId(), now);
                Bukkit.getScheduler().runTask(plugin.host(), () -> {
                    if (player.isOnline()) {
                        notifyPlayer(player, polls, false);
                    }
                });
            }
        });
    }

    public void notifyOnJoin(Player player) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin.host(), () -> {
            CloudApiClient.OpenGovernancePolls polls = fetchPolls();
            if (polls == null || polls.count() <= 0) {
                return;
            }
            lastTipAt.put(player.getUniqueId(), System.currentTimeMillis());
            Bukkit.getScheduler().runTask(plugin.host(), () -> {
                if (player.isOnline()) {
                    notifyPlayer(player, polls, true);
                }
            });
        }, 80L);
    }

    public void appendToVoteCommand(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            CloudApiClient.OpenGovernancePolls polls = fetchPolls();
            Bukkit.getScheduler().runTask(plugin.host(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (polls == null || polls.count() <= 0) {
                    return;
                }
                notifyPlayer(player, polls, false);
            });
        });
    }

    private void notifyPlayer(Player player, CloudApiClient.OpenGovernancePolls polls, boolean joinTone) {
        int n = polls.count();
        String reward = "3";
        ChatUi.entry(
                player,
                "Council",
                n + " proposal" + (n == 1 ? "" : "s") + " open · +" + reward + " G each vote",
                joinTone ? "open" : "done");
        List<CloudApiClient.OpenPoll> list = polls.polls();
        int shown = 0;
        for (CloudApiClient.OpenPoll poll : list) {
            if (poll == null || poll.title() == null) {
                continue;
            }
            String title = poll.title().length() > 48 ? poll.title().substring(0, 45) + "…" : poll.title();
            ChatUi.tip(player, title);
            if (++shown >= 3) {
                break;
            }
        }
        String url = "https://rootmc.net/council/";
        if (!list.isEmpty() && list.get(0).url() != null && !list.get(0).url().isBlank()) {
            url = list.get(0).url();
        }
        ChatUi.links(player, "Vote", url, "All polls", "https://rootmc.net/council/");
    }

    private CloudApiClient.OpenGovernancePolls fetchPolls() {
        Plugin rootmc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (!(rootmc instanceof RootStatBridge bridge) || !rootmc.isEnabled()) {
            return null;
        }
        try {
            if (!bridge.config().hasServerCredentials()) {
                return null;
            }
            return bridge.cloud().fetchOpenGovernancePolls();
        } catch (Exception ex) {
            return null;
        }
    }
}
