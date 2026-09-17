package com.rootrecord.minecraft.common;

import java.io.File;

/**
 * Paper Discord/Slack surface owned by Root-Core comms (single JDA session).
 * Resolve via {@link ShadedServiceBridge#resolveDiscord}; null means Discord sends are dropped.
 */
public interface RootDiscordApi {

    /** Bot connected and ready for outbound posts. */
    boolean isReady();

    /** Queue a chat-style line to the ingame-chat channel. */
    void postChatLine(String username, String message, String kind);

    /** Queue a reachout/broadcast line (grants, restart notices, etc.). */
    void postReachout(String username, String uuidOrNull, String message, String kind);

    /** Upload a server log file to the server-logs channel; no-op if not ready. */
    void uploadServerLog(File file, String caption);

    /** Reload config and restart the bot session. */
    void reload();
}
