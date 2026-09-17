package com.rootrecord.minecraft.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Consistent clickable links and action buttons for in-game chat. */
public final class ChatLinks {

    public static final NamedTextColor LINK = NamedTextColor.GREEN;
    public static final NamedTextColor ACTION = NamedTextColor.GREEN;
    public static final NamedTextColor MUTED = NamedTextColor.GRAY;

    private ChatLinks() {}

    public static Component url(String url) {
        return url(url, url);
    }

    public static Component url(String text, String url) {
        return Component.text(text, LINK, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, MUTED)));
    }

    public static Component action(String label, String command) {
        return Component.text(label, ACTION, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command, MUTED)));
    }

    /** Red stop / deny button. */
    public static Component danger(String label, String command) {
        return Component.text(label, NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command, MUTED)));
    }

    public static Component actionUrl(String label, String url) {
        return Component.text(label, ACTION, TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, MUTED)));
    }

    public static Component labeledUrl(String label, String url) {
        return Component.text()
                .append(Component.text(label, MUTED))
                .append(url(url))
                .build();
    }

    public static Component labelDashUrl(String label, String url) {
        return Component.text()
                .append(actionUrl(label, url))
                .append(Component.text(" — ", MUTED))
                .append(url(url))
                .build();
    }

    public static Component nameDashUrl(String name, String url) {
        return nameButtonUrl(name, url);
    }

    /** Site or label name with a clickable [Button] — URL only on hover. */
    public static Component nameButtonUrl(String name, String url) {
        return voteSiteLine(name, url, true, null);
    }

    /**
     * Vote site row: aqua + [Button] when available; red name + remaining wait (no button) when not.
     */
    public static Component voteSiteLine(String name, String url, boolean available, String remaining) {
        NamedTextColor nameColor = available ? NamedTextColor.AQUA : NamedTextColor.RED;
        var line = Component.text()
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(name, nameColor));
        if (available) {
            line.append(Component.text("  ", MUTED)).append(actionUrl("[Button]", url));
        } else if (remaining != null && !remaining.isBlank()) {
            line.append(Component.text("  ", MUTED)).append(Component.text(remaining, MUTED));
        }
        return line.build();
    }

    /** Muted label with clickable [Button] — URL only on hover. */
    public static Component labelButtonUrl(String label, String url) {
        return Component.text()
                .append(Component.text(label, MUTED))
                .append(Component.text("  ", MUTED))
                .append(actionUrl("[Button]", url))
                .build();
    }

    public static Component confirmCancel(String confirmCommand, String cancelCommand) {
        return Component.text()
                .append(action("[Confirm]", confirmCommand))
                .append(Component.text("  ", MUTED))
                .append(Component.text("[Cancel]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand(cancelCommand)))
                .build();
    }
}
