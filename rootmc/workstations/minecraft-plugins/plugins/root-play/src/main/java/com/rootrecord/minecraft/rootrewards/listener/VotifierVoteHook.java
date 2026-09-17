package com.rootrecord.minecraft.rootrewards.listener;

import com.rootrecord.minecraft.rootrewards.RootRewardsPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

/** NuVotifier/VotifierPlus vote events via reflection (no compile dependency). */
public final class VotifierVoteHook implements Listener {

    private final RootRewardsPlugin plugin;

    public VotifierVoteHook(RootRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean register() {
        Class<? extends Event> eventClass = eventClass("com.vexsoftware.votifier.model.VotifierEvent");
        if (eventClass == null) {
            return false;
        }
        EventExecutor executor = (listener, event) -> handleVote(event);
        plugin.getServer().getPluginManager().registerEvent(
                eventClass, this, EventPriority.NORMAL, executor, plugin.host(), false);
        return true;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> eventClass(String className) {
        try {
            Class<?> type = Class.forName(className);
            if (Event.class.isAssignableFrom(type)) {
                return (Class<? extends Event>) type;
            }
        } catch (ClassNotFoundException ignored) {
            // NuVotifier not on classpath
        }
        return null;
    }

    private void handleVote(Event event) {
        try {
            Object vote = invokeNoArg(event, "getVote");
            if (vote == null) {
                return;
            }
            Object usernameObj = invokeNoArg(vote, "getUsername");
            Object serviceObj = invokeNoArg(vote, "getServiceName");
            if (!(usernameObj instanceof String username) || username.isBlank()) {
                return;
            }
            String service = serviceObj instanceof String s && !s.isBlank() ? s : "default";
            plugin.getLogger().info("Votifier vote received — player=" + username + " service=" + service);
            plugin.voteRewards().handleVote(username, service);
        } catch (Exception ex) {
            plugin.getLogger().warning("Vote event handling failed: " + ex.getMessage());
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }
}
