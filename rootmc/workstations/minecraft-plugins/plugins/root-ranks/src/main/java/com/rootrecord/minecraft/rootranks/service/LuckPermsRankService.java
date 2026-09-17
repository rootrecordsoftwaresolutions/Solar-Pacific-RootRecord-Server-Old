package com.rootrecord.minecraft.rootranks.service;

import com.rootrecord.minecraft.rootranks.config.RankTier;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Applies player-track LuckPerms groups. */
public final class LuckPermsRankService {

    private final LuckPerms luckPerms;

    public LuckPermsRankService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public int highestOwnedIndex(List<RankTier> tiers, UUID playerId) {
        int highest = -1;
        for (int i = 0; i < tiers.size(); i++) {
            if (hasGroup(playerId, tiers.get(i).id())) {
                highest = i;
            }
        }
        return highest;
    }

    public boolean hasGroup(UUID playerId, String groupId) {
        User user = luckPerms.getUserManager().getUser(playerId);
        if (user == null) {
            return false;
        }
        if (user.getNodes().stream()
                .filter(InheritanceNode.class::isInstance)
                .map(InheritanceNode.class::cast)
                .anyMatch(node -> node.getGroupName().equalsIgnoreCase(groupId))) {
            return true;
        }
        return user.getInheritedGroups(QueryOptions.nonContextual()).stream()
                .anyMatch(group -> group.getName().equalsIgnoreCase(groupId));
    }

    public boolean grantGroup(Player player, RankTier tier) {
        UUID uuid = player.getUniqueId();
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).get();
            boolean already = user.getNodes().stream()
                    .filter(InheritanceNode.class::isInstance)
                    .map(InheritanceNode.class::cast)
                    .anyMatch(node -> node.getGroupName().equalsIgnoreCase(tier.id()));
            if (already) {
                return true;
            }
            user.data().add(InheritanceNode.builder(tier.id()).build());
            luckPerms.getUserManager().saveUser(user);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
