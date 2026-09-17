package com.rootrecord.minecraft.rootrestart;

import com.rootrecord.minecraft.common.RootMcPublicReachout;
import com.rootrecord.minecraft.common.ShadedServiceBridge;

/** Relays restart lines to Discord #ingame-chat via RootMC public reachout. */
final class RestartDiscordRelay {

    private RestartDiscordRelay() {}

    static void relay(RootRestartPlugin plugin, String coloredLine) {
        relay(plugin, coloredLine, "restart");
    }

    static void relay(RootRestartPlugin plugin, String coloredLine, String kind) {
        if (!plugin.config().discordRelay() || coloredLine == null || coloredLine.isBlank()) {
            return;
        }
        RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
        if (reachout != null) {
            reachout.relayGlobalBroadcast(coloredLine, kind == null ? "restart" : kind);
        }
    }
}
