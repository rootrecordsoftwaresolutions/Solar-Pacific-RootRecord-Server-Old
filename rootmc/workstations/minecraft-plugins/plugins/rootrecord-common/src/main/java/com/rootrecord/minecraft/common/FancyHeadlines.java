package com.rootrecord.minecraft.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Gradient / glyph headlines for major command and broadcast headers only.
 * Toggle via {@link FancyUiConfig} ({@code plugins/RootMC/rootmc-ui.yml}).
 */
public final class FancyHeadlines {

    /** Catalog / header mark — plain {@code | } (no resource-pack private-use glyph). */
    public static final String GLYPH = "| ";

    /** Rule above / below every command header. */
    public static final String RULE = "----------------------------------";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private static volatile boolean enabled = true;
    /** Kept for config compat; when true, prefixes lines with {@link #GLYPH}. */
    private static volatile boolean useGlyph = true;

    private FancyHeadlines() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setUseGlyph(boolean value) {
        useGlyph = value;
    }

    public static boolean useGlyph() {
        return useGlyph;
    }

    /** Brand banner — gold → green gradient. */
    public static Component banner(String title) {
        return render(title, "banner",
                "<gradient:#f5b942:#7ecf7e><bold>", "</bold></gradient>");
    }

    /** Section header — softer gold. */
    public static Component section(String title) {
        return render(title, "section",
                "<gradient:#d4a853:#f5e6a8><bold>", "</bold></gradient>");
    }

    /** Restart / warning — red → orange. */
    public static Component alert(String title) {
        return render(title, "alert",
                "<gradient:#ef5b5b:#f5b942><bold>", "</bold></gradient>");
    }

    /** Muted top rule. */
    public static Component ruleTop() {
        return MM.deserialize("<dark_gray>" + RULE + "</dark_gray>");
    }

    /** Colored bottom rule matching the headline style. */
    public static Component ruleBottom(String style) {
        String mm = switch (style == null ? "banner" : style) {
            case "alert" -> "<gradient:#ef5b5b:#f5b942>" + RULE + "</gradient>";
            case "section" -> "<gradient:#d4a853:#f5e6a8>" + RULE + "</gradient>";
            default -> "<gradient:#f5b942:#7ecf7e>" + RULE + "</gradient>";
        };
        return MM.deserialize(mm);
    }

    /** Send headline framed by dashed rules (top muted, bottom colored). */
    public static void sendBanner(CommandSender sender, String title) {
        sendFramed(sender, title, "banner");
    }

    public static void sendSection(CommandSender sender, String title) {
        sendFramed(sender, title, "section");
    }

    public static void sendAlert(CommandSender sender, String title) {
        sendFramed(sender, title, "alert");
    }

    private static void sendFramed(CommandSender sender, String title, String style) {
        sender.sendMessage(ruleTop());
        sender.sendMessage(switch (style) {
            case "alert" -> alert(title);
            case "section" -> section(title);
            default -> banner(title);
        });
        sender.sendMessage(ruleBottom(style));
    }

    /** Legacy {@code &}-code string for APIs that still take String (Discord relay, etc.). */
    public static String asLegacy(Component component) {
        return LEGACY.serialize(component);
    }

    private static Component render(String title, String plainFallbackStyle, String open, String close) {
        String safe = title == null ? "" : title.trim();
        if (safe.isEmpty()) {
            safe = "RootMC";
        }
        if (!enabled) {
            return switch (plainFallbackStyle) {
                case "alert" -> Component.text(safe).color(net.kyori.adventure.text.format.NamedTextColor.RED)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
                case "section" -> Component.text(safe).color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
                default -> Component.text(safe).color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
            };
        }
        String escaped = MM.escapeTags(safe);
        String glyphPrefix = useGlyph ? "<dark_gray>| </dark_gray>" : "";
        return MM.deserialize(glyphPrefix + open + escaped + close);
    }
}
