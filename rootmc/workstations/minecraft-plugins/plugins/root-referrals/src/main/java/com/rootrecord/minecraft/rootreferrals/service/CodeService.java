package com.rootrecord.minecraft.rootreferrals.service;

import com.rootrecord.minecraft.rootreferrals.ReferralsConfig;
import com.rootrecord.minecraft.rootreferrals.store.ReferralStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.UUID;

/** Generates unique referral codes (excludes 0, O, 1, I, L). */
public final class CodeService {

    private static final String CHARSET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int MAX_ATTEMPTS = 64;

    private final JavaPlugin plugin;
    private final ReferralStore store;
    private final ReferralsConfig config;
    private final SecureRandom random = new SecureRandom();

    public CodeService(JavaPlugin plugin, ReferralStore store, ReferralsConfig config) {
        this.plugin = plugin;
        this.store = store;
        this.config = config;
    }

    public String generateUniqueCode() {
        int len = config.codeLength();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode(len);
            if (!store.codeExists(code)) {
                return code;
            }
        }
        plugin.getLogger().warning("Failed to generate unique referral code after " + MAX_ATTEMPTS + " attempts.");
        return randomCode(len);
    }

    public ReferralStore.ReferralPlayer ensureCode(UUID uuid, String displayName, String discordId) {
        ReferralStore.ReferralPlayer existing = store.findPlayer(uuid).orElse(null);
        if (existing != null) {
            store.updateDisplayName(uuid, displayName);
            if (discordId != null && !discordId.isBlank()) {
                store.updateDiscordId(uuid, discordId);
            }
            return store.findPlayer(uuid).orElse(existing);
        }
        String code = generateUniqueCode();
        return store.ensurePlayer(uuid, displayName, discordId, code);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}
