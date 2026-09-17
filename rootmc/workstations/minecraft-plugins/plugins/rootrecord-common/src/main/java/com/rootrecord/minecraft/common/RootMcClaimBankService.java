package com.rootrecord.minecraft.common;

import java.util.List;
import java.util.UUID;

/** Cross-plugin bridge for RootClaims bank accounts that should auto-bond like town/nation banks. */
public interface RootMcClaimBankService {

    List<ClaimBank> activeClaimBanks();

    ClaimBank findClaimBank(UUID accountUuid);

    record ClaimBank(
            UUID accountUuid,
            String accountName,
            String displayName,
            UUID ownerUuid,
            String ownerName,
            double balanceG) {}
}
