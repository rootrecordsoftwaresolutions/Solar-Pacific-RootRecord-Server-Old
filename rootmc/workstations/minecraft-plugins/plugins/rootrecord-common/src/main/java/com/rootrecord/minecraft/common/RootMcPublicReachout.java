package com.rootrecord.minecraft.common;

import java.util.UUID;

/**
 * Public treasury reachouts — hourly aggregates for the announcer and optional
 * in-game + #ingame-chat Discord relay (not personal /pay or shop trades).
 */
public interface RootMcPublicReachout {

    /**
     * Record a server-treasury outflow. When {@code notifyImmediately} is true, also
     * broadcasts in-game and relays to Discord #ingame-chat when enabled.
     *
     * @param category e.g. discord_activity, discord_link, map_return_grant, vote, grant
     */
    void recordTreasuryOutflow(
            String category,
            String playerName,
            UUID playerUuid,
            double gold,
            boolean notifyImmediately);

    /** Relay an existing global broadcast line (staff broadcast, vote milestone, etc.). */
    void relayGlobalBroadcast(String coloredOrPlainMessage, String kind);

    /** Merged hourly summary for root-announcer rotation; empty when nothing recorded this hour. */
    String hourlyAnnouncerLine();
}
