package com.rootrecord.minecraft.rootcore.comms;

import com.rootrecord.minecraft.common.RootDiscordApi;
import com.rootrecord.minecraft.rootcore.RootCorePlugin;

import java.io.File;

/** RootDiscordApi backed by Core-owned DiscordChatBridge. */
public final class RootCommsApiImpl implements RootDiscordApi {

    private final RootCorePlugin plugin;

    public RootCommsApiImpl(RootCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isReady() {
        DiscordChatBridge bridge = plugin.commsBridge();
        return bridge != null && bridge.isReady();
    }

    @Override
    public void postChatLine(String username, String message, String kind) {
        DiscordChatBridge bridge = plugin.commsBridge();
        if (bridge == null || !bridge.isReady()) {
            return;
        }
        bridge.postChatLine(username, message, kind);
    }

    @Override
    public void postReachout(String username, String uuidOrNull, String message, String kind) {
        DiscordChatBridge bridge = plugin.commsBridge();
        if (bridge == null || !bridge.isReady()) {
            return;
        }
        bridge.relayReachout(username, uuidOrNull == null ? "" : uuidOrNull, message, kind);
    }

    @Override
    public void uploadServerLog(File file, String caption) {
        DiscordChatBridge bridge = plugin.commsBridge();
        if (bridge == null) {
            return;
        }
        // Prefer Slack webhook path even when JDA is offline.
        bridge.uploadServerLog(file, caption);
    }

    @Override
    public void reload() {
        plugin.reloadComms();
    }
}
