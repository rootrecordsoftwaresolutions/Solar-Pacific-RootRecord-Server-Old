package com.rootrecord.minecraft.rootmemberships;

import com.rootrecord.minecraft.rootstat.RootStatBridge;
import com.rootrecord.minecraft.rootstat.cloud.CloudApiClient;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Reads cloud Pro / life / top-active flags via RootMC ({@link RootStatBridge}).
 */
public final class CloudMembershipFlags {

    public record Flags(boolean proUnlocked, boolean lifeMember, boolean topActivePlayer, boolean linked) {
        static Flags unlinked() {
            return new Flags(false, false, false, false);
        }
    }

    private CloudMembershipFlags() {}

    public static Flags fetch(Player player) {
        if (player == null) {
            return Flags.unlinked();
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("RootMC");
        if (!(plugin instanceof RootStatBridge bridge)) {
            return Flags.unlinked();
        }
        try {
            CloudApiClient.LinkStatus status = bridge.cloud().linkStatus(player.getUniqueId().toString());
            if (!status.linked()) {
                return Flags.unlinked();
            }
            return new Flags(
                    status.proUnlocked(),
                    status.lifeMember(),
                    status.topActivePlayer(),
                    true);
        } catch (Exception e) {
            return Flags.unlinked();
        }
    }

    public static boolean qualifies(MembershipsConfig.MembershipRule rule, Flags flags) {
        if (!rule.enabled()) {
            return false;
        }
        if (rule.requireTopActive()) {
            return flags.topActivePlayer();
        }
        if (rule.requireLife() && !flags.lifeMember()) {
            return false;
        }
        if (rule.requireCloudPro()) {
            boolean pro = flags.proUnlocked();
            if (rule.lifeCountsAsPro() && flags.lifeMember()) {
                pro = true;
            }
            return pro;
        }
        if (rule.requireLife()) {
            return flags.lifeMember();
        }
        return flags.proUnlocked() || flags.lifeMember();
    }
}
