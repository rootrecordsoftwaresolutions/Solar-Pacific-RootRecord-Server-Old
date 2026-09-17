package com.rootrecord.minecraft.rootrestart;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

/** Posts to Discord #ingame-chat after Paper finishes startup (not plugin reload). */
final class ServerStartupNotifier implements Listener {

    private static boolean notifiedThisJvm;

    private final RootRestartPlugin plugin;

    ServerStartupNotifier(RootRestartPlugin plugin) {
        this.plugin = plugin;
    }

    void schedule() {
        if (notifiedThisJvm) {
            return;
        }
        // Root-Restart enables mid-boot; delay until after "Done" (~15s typical).
        Bukkit.getScheduler().runTaskLater(plugin.host(), this::notifyIfReady, 300L);
    }

    private void notifyIfReady() {
        if (notifiedThisJvm || !plugin.isEnabled()) {
            return;
        }
        if (!plugin.config().discordStartupNotify()) {
            return;
        }
        notifiedThisJvm = true;
        RestartConfig cfg = plugin.config();
        String line = plugin.colorize(cfg.prefix() + cfg.startupCompleteMsg());
        RestartDiscordRelay.relay(plugin, line, "startup");
        plugin.getLogger().info("Startup complete — relayed to Discord #ingame-chat.");
    }
}
