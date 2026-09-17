package com.rootrecord.minecraft.rootterritories.nation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NationFoundingProposal {

    public enum Status {
        PENDING,
        APPROVED,
        EXECUTED,
        CANCELLED
    }

    private final String id;
    private final String nationName;
    private final String capitalTown;
    private final List<String> partnerTowns;
    private Status status;
    private final long createdAtEpochMs;
    private final String createdByUuid;
    private final Map<String, Signature> signatures;

    public NationFoundingProposal(
            String id,
            String nationName,
            String capitalTown,
            List<String> partnerTowns,
            Status status,
            long createdAtEpochMs,
            String createdByUuid,
            Map<String, Signature> signatures) {
        this.id = id;
        this.nationName = nationName;
        this.capitalTown = capitalTown;
        this.partnerTowns = List.copyOf(partnerTowns);
        this.status = status;
        this.createdAtEpochMs = createdAtEpochMs;
        this.createdByUuid = createdByUuid;
        this.signatures = new LinkedHashMap<>(signatures);
    }

    public record Signature(String mayorUuid, String mayorName, long signedAtEpochMs) {}

    public String id() { return id; }
    public String nationName() { return nationName; }
    public String capitalTown() { return capitalTown; }
    public List<String> partnerTowns() { return partnerTowns; }
    public Status status() { return status; }
    public long createdAtEpochMs() { return createdAtEpochMs; }
    public String createdByUuid() { return createdByUuid; }
    public Map<String, Signature> signatures() { return Map.copyOf(signatures); }

    public Set<String> allTowns() {
        LinkedHashSet<String> towns = new LinkedHashSet<>();
        towns.add(capitalTown);
        towns.addAll(partnerTowns);
        return Set.copyOf(towns);
    }

    public boolean isSigned(String townName) {
        return signatures.containsKey(normalizeTown(townName));
    }

    public void sign(String townName, Signature signature) {
        signatures.put(normalizeTown(townName), signature);
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int signedCount() {
        return signatures.size();
    }

    public int requiredTownCount() {
        return 1 + partnerTowns.size();
    }

    public boolean isFullySigned() {
        return signedCount() >= requiredTownCount();
    }

    public static String normalizeTown(String town) {
        return town == null ? "" : town.trim();
    }

    public static String normalizeNation(String nation) {
        return nation == null ? "" : nation.trim();
    }
}
