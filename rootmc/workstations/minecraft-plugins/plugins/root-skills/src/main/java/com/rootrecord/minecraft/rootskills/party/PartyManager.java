package com.rootrecord.minecraft.rootskills.party;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.model.Party;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyManager {

    private final RootSkillsPlugin plugin;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> memberToParty = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> invites = new ConcurrentHashMap<>();

    public PartyManager(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("features.parties", true);
    }

    public double defaultXpShare() {
        return plugin.getConfig().getDouble("party.xp-share", 0.25);
    }

    public double shareRadius() {
        return plugin.getConfig().getDouble("party.share-radius", 50.0);
    }

    public Optional<Party> get(UUID partyId) {
        return Optional.ofNullable(parties.get(partyId));
    }

    public Optional<Party> partyOf(UUID playerId) {
        UUID partyId = memberToParty.get(playerId);
        return partyId == null ? Optional.empty() : get(partyId);
    }

    public Party create(UUID leaderId, String name) {
        Party party = new Party(UUID.randomUUID(), leaderId);
        party.setXpSharePercent(defaultXpShare());
        parties.put(party.id(), party);
        memberToParty.put(leaderId, party.id());
        PlayerSkillsProfile profile = plugin.repository().getOrCreate(leaderId);
        profile.setPartyId(party.id());
        plugin.repository().saveAsync(profile);
        persistParty(party, name);
        return party;
    }

    public boolean invite(UUID leaderId, UUID targetId) {
        Optional<Party> party = partyOf(leaderId);
        if (party.isEmpty() || !leaderId.equals(party.get().leaderId())) {
            return false;
        }
        invites.put(targetId, party.get().id());
        return true;
    }

    public boolean accept(UUID playerId) {
        UUID partyId = invites.remove(playerId);
        if (partyId == null) {
            return false;
        }
        Party party = parties.get(partyId);
        if (party == null || !party.add(playerId)) {
            return false;
        }
        memberToParty.put(playerId, partyId);
        plugin.repository().getOrCreate(playerId).setPartyId(partyId);
        plugin.repository().saveAsync(plugin.repository().getOrCreate(playerId));
        persistMember(partyId, playerId);
        return true;
    }

    public void leave(UUID playerId) {
        UUID partyId = memberToParty.remove(playerId);
        if (partyId == null) {
            return;
        }
        Party party = parties.get(partyId);
        if (party != null) {
            party.remove(playerId);
            if (party.members().isEmpty() || playerId.equals(party.leaderId())) {
                disband(partyId);
            }
        }
        PlayerSkillsProfile profile = plugin.repository().getOrCreate(playerId);
        profile.setPartyId(null);
        plugin.repository().saveAsync(profile);
    }

    public void disband(UUID partyId) {
        Party party = parties.remove(partyId);
        if (party == null) {
            return;
        }
        for (UUID member : party.members()) {
            memberToParty.remove(member, partyId);
            PlayerSkillsProfile profile = plugin.repository().getOrCreate(member);
            profile.setPartyId(null);
            plugin.repository().saveAsync(profile);
        }
    }

    public void shareXp(Player source, SkillId skill, long amount) {
        if (!enabled() || source == null || amount <= 0) {
            return;
        }
        Optional<Party> partyOpt = partyOf(source.getUniqueId());
        if (partyOpt.isEmpty()) {
            return;
        }
        Party party = partyOpt.get();
        double share = party.xpSharePercent() > 0 ? party.xpSharePercent() : defaultXpShare();
        long shared = Math.max(1L, (long) Math.floor(amount * share));
        double radiusSq = shareRadius() * shareRadius();
        for (UUID memberId : party.members()) {
            if (memberId.equals(source.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member == null || !member.getWorld().equals(source.getWorld())) {
                continue;
            }
            if (member.getLocation().distanceSquared(source.getLocation()) > radiusSq) {
                continue;
            }
            plugin.xpService().addXp(memberId, skill, shared, false);
        }
    }

    private void persistParty(Party party, String name) {
        if (plugin.database() == null || !plugin.database().available()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.database().connection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO " + plugin.schemaManager().table("parties")
                                 + " (party_id, leader_uuid, name, xp_share) VALUES (?,?,?,?)"
                                 + " ON DUPLICATE KEY UPDATE leader_uuid=VALUES(leader_uuid),"
                                 + " name=VALUES(name), xp_share=VALUES(xp_share)")) {
                ps.setString(1, party.id().toString());
                ps.setString(2, party.leaderId().toString());
                ps.setString(3, name);
                ps.setDouble(4, party.xpSharePercent());
                ps.executeUpdate();
                persistMember(party.id(), party.leaderId());
            } catch (Exception ex) {
                plugin.getLogger().warning("Party persist failed: " + ex.getMessage());
            }
        });
    }

    private void persistMember(UUID partyId, UUID playerId) {
        if (plugin.database() == null || !plugin.database().available()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.database().connection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT IGNORE INTO " + plugin.schemaManager().table("party_members")
                                 + " (party_id, uuid) VALUES (?,?)")) {
                ps.setString(1, partyId.toString());
                ps.setString(2, playerId.toString());
                ps.executeUpdate();
            } catch (Exception ex) {
                plugin.getLogger().warning("Party member persist failed: " + ex.getMessage());
            }
        });
    }
}
