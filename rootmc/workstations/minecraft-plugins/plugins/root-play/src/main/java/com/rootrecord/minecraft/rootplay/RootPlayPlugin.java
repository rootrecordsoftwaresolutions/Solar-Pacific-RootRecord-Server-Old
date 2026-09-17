package com.rootrecord.minecraft.rootplay;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import com.rootrecord.minecraft.common.RootMcVotePerk;
import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import com.rootrecord.minecraft.rootranks.RootRanksPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

/** Root-Play: Ranks + Rewards + Help. Root-Road lives in Root-Claims. */
public final class RootPlayPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRanksPlugin ranks;
    private RootRewardsPlugin rewards;
    private RootHelpPlugin help;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        if (getServer().getPluginManager().getPlugin("Root-Core") == null) {
            var repair = RootMcCoreConnection.ensureAndRepair(this);
            getLogger().warning(
                    "Root-Core not present — used RootMcCoreConnection fallback (databaseOk="
                            + repair.databaseOk()
                            + ", cloudOk="
                            + repair.cloudOk()
                            + ").");
        }

        ranks = new RootRanksPlugin(this);
        ranks.enable();

        rewards = new RootRewardsPlugin(this);
        rewards.enable();

        help = new RootHelpPlugin(this);
        help.enable();

        getLogger().info("Root-Play enabled (Ranks + Rewards + Help).");
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (help != null) {
            help.disable();
            help = null;
        }
        if (rewards != null) {
            rewards.disable();
            rewards = null;
        }
        if (ranks != null) {
            ranks.disable();
            ranks = null;
        }
    }

    /** Called by Root-Claims via reflection when owned claim count changes. */
    public void onOwnedClaimCountChanged(Player player, int ownedCount) {
        if (ranks == null) {
            return;
        }
        try {
            ranks.getClass()
                    .getMethod("onOwnedClaimCountChanged", Player.class, int.class)
                    .invoke(ranks, player, ownedCount);
        } catch (NoSuchMethodException ignored) {
            // Ranks may not implement claim hooks yet
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("Rank claim notify failed: " + ex.getMessage());
        }
    }

    public RootRanksPlugin ranks() {
        return ranks;
    }

    public RootRewardsPlugin rewards() {
        return rewards;
    }

    /** Shaded bridge for Root-Economy / RootMC: 5 votes / 24h → half tax + half death loss. */
    public RootMcVotePerk votePerk() {
        return rewards == null ? null : rewards.votePerk();
    }

    public RootHelpPlugin help() {
        return help;
    }
}
