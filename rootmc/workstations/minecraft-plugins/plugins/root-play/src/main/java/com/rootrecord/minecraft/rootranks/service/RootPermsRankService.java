package com.rootrecord.minecraft.rootranks.service;

import com.rootrecord.minecraft.common.RootMcPermsService;
import com.rootrecord.minecraft.rootranks.config.RankTier;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Applies player-track groups via Root-Perms. */
public final class RootPermsRankService {

    private final RootMcPermsService perms;

    public RootPermsRankService(RootMcPermsService perms) {
        this.perms = perms;
    }

    public int highestOwnedIndex(List<RankTier> tiers, UUID playerId) {
        List<String> track = new ArrayList<>(tiers.size());
        for (RankTier tier : tiers) {
            track.add(tier.id().toLowerCase(Locale.ROOT));
        }
        return perms.highestTrackIndex(playerId, track);
    }

    public boolean hasGroup(UUID playerId, String groupId) {
        return perms.hasGroup(playerId, groupId);
    }

    public boolean grantGroup(Player player, RankTier tier) {
        return perms.grantGroup(player.getUniqueId(), tier.id());
    }
}
