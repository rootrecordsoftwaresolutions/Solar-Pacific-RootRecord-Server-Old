package com.rootrecord.minecraft.roothelp.catalog;

import com.rootrecord.minecraft.common.ChatLinks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/** Builds clickable Adventure components for /cmds help lines. */
public final class HelpCommandFormat {

    private HelpCommandFormat() {}

    public static Component line(String command, String description) {
        Component body = formatCommandText(command);
        if (body == Component.empty()) {
            body = Component.text(command, NamedTextColor.GRAY);
        }
        return Component.text(" ", NamedTextColor.GRAY)
                .append(body)
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(description, NamedTextColor.WHITE));
    }

    private static Component formatCommandText(String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.empty();
        }
        String[] segments = raw.split(" \\| ");
        Component out = Component.empty();
        boolean first = true;
        for (String segment : segments) {
            List<String> commands = extractCommands(segment.trim());
            if (commands.isEmpty()) {
                if (!first) {
                    out = out.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
                }
                out = out.append(Component.text(segment.trim(), NamedTextColor.GRAY));
                first = false;
                continue;
            }
            for (int i = 0; i < commands.size(); i++) {
                if (!first) {
                    out = out.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
                }
                out = out.append(clickableCommand(commands.get(i)));
                first = false;
            }
        }
        return out;
    }

    /** Pulls slash-commands from a segment, including a trailing parenthetical alias. */
    static List<String> extractCommands(String segment) {
        if (segment == null || !segment.contains("/")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        int aliasStart = segment.lastIndexOf(" (/");
        if (aliasStart >= 0 && segment.endsWith(")")) {
            out.add(segment.substring(0, aliasStart).trim());
            out.add(segment.substring(aliasStart + 2, segment.length() - 1).trim());
            return out;
        }
        if (segment.startsWith("/")) {
            out.add(segment);
        }
        return out;
    }

    private static Component clickableCommand(String cmd) {
        boolean suggest = cmd.contains("<") || cmd.contains("[") || cmd.contains("|");
        ClickEvent click = suggest
                ? ClickEvent.suggestCommand(cmd)
                : ClickEvent.runCommand(cmd);
        String hover = suggest
                ? "Click to fill chat: " + cmd
                : "Click to run: " + cmd;
        return Component.text(cmd, ChatLinks.LINK, TextDecoration.UNDERLINED)
                .clickEvent(click)
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }
}
