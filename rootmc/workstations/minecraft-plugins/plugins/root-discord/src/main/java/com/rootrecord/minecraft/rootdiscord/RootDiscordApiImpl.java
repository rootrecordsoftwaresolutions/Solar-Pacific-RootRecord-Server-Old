package com.rootrecord.minecraft.rootdiscord;

import com.rootrecord.minecraft.common.RootDiscordApi;

import java.io.File;

public final class RootDiscordApiImpl implements RootDiscordApi {

    private final RootDiscordPlugin plugin;

    public RootDiscordApiImpl(RootDiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isReady() {
        DiscordChatBridge bridge = plugin.bridge();
        return bridge != null && bridge.isReady();
    }

    @Override
    public void postChatLine(String username, String message, String kind) {
        DiscordChatBridge bridge = plugin.bridge();
        if (bridge == null || !bridge.isReady()) {
            return;
        }
        bridge.postChatLine(username, message, kind);
    }

    @Override
    public void postReachout(String username, String uuidOrNull, String message, String kind) {
        DiscordChatBridge bridge = plugin.bridge();
        if (bridge == null || !bridge.isReady()) {
            return;
        }
        bridge.relayReachout(username, uuidOrNull == null ? "" : uuidOrNull, message, kind);
    }

    @Override
    public void uploadServerLog(File file, String caption) {
        DiscordChatBridge bridge = plugin.bridge();
        if (bridge == null || !bridge.isReady()) {
            return;
        }
        bridge.uploadServerLog(file, caption);
    }

    @Override
    public void reload() {
        plugin.reloadDiscord();
    }
}
