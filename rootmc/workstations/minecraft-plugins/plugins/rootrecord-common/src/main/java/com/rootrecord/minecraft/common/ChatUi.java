package com.rootrecord.minecraft.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * RootMC chat panels — catalog lines with resource-pack glyph, not essay walls.
 * Pattern: {@code ◆ Tag » title · trailing} (one fact per line).
 */
public final class ChatUi {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ChatUi() {}

    public static void banner(CommandSender sender, String title) {
        FancyHeadlines.sendBanner(sender, title);
    }

    public static void section(CommandSender sender, String title) {
        FancyHeadlines.sendSection(sender, title);
    }

    public static void alert(CommandSender sender, String title) {
        FancyHeadlines.sendAlert(sender, title);
    }

    /**
     * Catalog entry: pack glyph + gold tag + {@code »} + white body + optional muted trailing.
     * Example: {@code ◆ Notes » 654.697 G}
     */
    public static void entry(CommandSender sender, String tag, String body) {
        entry(sender, tag, body, null);
    }

    public static void entry(CommandSender sender, String tag, String body, String trailing) {
        sender.sendMessage(entryComponent(tag, body, trailing));
    }

    /**
     * Gold amount entry: amount is green when ≥ 0, red when negative.
     * Example: {@code ◆ Notes » 654.697 G}
     */
    public static void gold(CommandSender sender, String tag, String amount, String currency) {
        sender.sendMessage(goldComponent(tag, amount, currency));
    }

    /** Ranked money line: {@code ◆ #1 » name · 133.000 G} with green/red amount. */
    public static void rank(CommandSender sender, int rank, String name, String amount, String currency) {
        sender.sendMessage(rankComponent(rank, name, amount, currency));
    }

    /** Done / open style trailing in green or yellow. */
    public static void entryStatus(CommandSender sender, String tag, String body, boolean done) {
        entryStatus(sender, tag, body, done, null);
    }

    /**
     * @param incompleteTrailing shown when not done (yellow). Null/blank → muted ellipsis.
     */
    public static void entryStatus(
            CommandSender sender, String tag, String body, boolean done, String incompleteTrailing) {
        if (done) {
            entry(sender, tag, body, "ok");
            return;
        }
        String open = incompleteTrailing == null || incompleteTrailing.isBlank()
                ? "…"
                : incompleteTrailing.trim();
        if ("open".equalsIgnoreCase(open) || "todo".equalsIgnoreCase(open)) {
            open = "…";
        }
        sender.sendMessage(entryComponentTone(tag, body, open, "yellow"));
    }

    private static Component entryComponentTone(String tag, String body, String trailing, String color) {
        String safeTag = escape(tag == null || tag.isBlank() ? "—" : tag.trim());
        String safeBody = escape(body == null ? "" : body.trim());
        StringBuilder mm = new StringBuilder();
        appendGlyph(mm);
        mm.append("<gradient:#d4a853:#f5e6a8>").append(safeTag).append("</gradient>");
        mm.append("<dark_gray> » </dark_gray>");
        mm.append("<white>").append(safeBody).append("</white>");
        if (trailing != null && !trailing.isBlank()) {
            mm.append("<dark_gray> · </dark_gray><").append(color).append(">")
                    .append(escape(trailing.trim())).append("</").append(color).append(">");
        }
        return MM.deserialize(mm.toString());
    }

    public static Component entryComponent(String tag, String body, String trailing) {
        String safeTag = escape(tag == null || tag.isBlank() ? "—" : tag.trim());
        String safeBody = escape(body == null ? "" : body.trim());
        StringBuilder mm = new StringBuilder();
        appendGlyph(mm);
        mm.append("<gradient:#d4a853:#f5e6a8>").append(safeTag).append("</gradient>");
        mm.append("<dark_gray> » </dark_gray>");
        mm.append("<white>").append(safeBody).append("</white>");
        appendTrailing(mm, trailing);
        return MM.deserialize(mm.toString());
    }

    public static Component goldComponent(String tag, String amount, String currency) {
        String safeTag = escape(tag == null || tag.isBlank() ? "—" : tag.trim());
        String safeAmount = escape(amount == null ? "0" : amount.trim());
        String safeCur = escape(currency == null || currency.isBlank() ? "G" : currency.trim());
        String color = amountColor(amount);
        StringBuilder mm = new StringBuilder();
        appendGlyph(mm);
        mm.append("<gradient:#d4a853:#f5e6a8>").append(safeTag).append("</gradient>");
        mm.append("<dark_gray> » </dark_gray>");
        mm.append("<").append(color).append(">").append(safeAmount).append(" ").append(safeCur)
                .append("</").append(color).append(">");
        return MM.deserialize(mm.toString());
    }

    public static Component rankComponent(int rank, String name, String amount, String currency) {
        String safeName = escape(name == null || name.isBlank() ? "—" : name.trim());
        String safeAmount = escape(amount == null ? "0" : amount.trim());
        String safeCur = escape(currency == null || currency.isBlank() ? "G" : currency.trim());
        String color = amountColor(amount);
        StringBuilder mm = new StringBuilder();
        appendGlyph(mm);
        mm.append("<gradient:#d4a853:#f5e6a8>#").append(Math.max(1, rank)).append("</gradient>");
        mm.append("<dark_gray> » </dark_gray>");
        mm.append("<white>").append(safeName).append("</white>");
        mm.append("<dark_gray> · </dark_gray>");
        mm.append("<").append(color).append(">").append(safeAmount).append(" ").append(safeCur)
                .append("</").append(color).append(">");
        return MM.deserialize(mm.toString());
    }

    /** @deprecated Prefer {@link #entry}; kept for callers mid-migration. */
    public static void row(CommandSender sender, String label, String value) {
        entry(sender, label, value);
    }

    /** @deprecated Prefer {@link #entry}; color is ignored — use status trailing instead. */
    public static void row(CommandSender sender, String label, String value, NamedTextColor valueColor) {
        if (valueColor == NamedTextColor.RED) {
            entry(sender, label, value, "alert");
        } else if (valueColor == NamedTextColor.GREEN) {
            entry(sender, label, value, "ok");
        } else if (valueColor == NamedTextColor.YELLOW) {
            entry(sender, label, value, "open");
        } else {
            entry(sender, label, value);
        }
    }

    public static void tip(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text == null ? "" : text, NamedTextColor.DARK_GRAY));
    }

    public static void blank(CommandSender sender) {
        sender.sendMessage(Component.empty());
    }

    /** One compact row of clickable [buttons] (URL on hover only). */
    public static void links(CommandSender sender, String... labelUrlPairs) {
        if (labelUrlPairs == null || labelUrlPairs.length < 2) {
            return;
        }
        var line = Component.text();
        boolean first = true;
        for (int i = 0; i + 1 < labelUrlPairs.length; i += 2) {
            String label = labelUrlPairs[i];
            String url = labelUrlPairs[i + 1];
            if (label == null || url == null || label.isBlank() || url.isBlank()) {
                continue;
            }
            if (!first) {
                line.append(Component.text("  ", NamedTextColor.DARK_GRAY));
            }
            first = false;
            if (sender instanceof Player) {
                line.append(ChatLinks.actionUrl("[" + stripBrackets(label) + "]", url));
            } else {
                line.append(Component.text(stripBrackets(label) + ": " + url, NamedTextColor.GRAY));
            }
        }
        sender.sendMessage(line.build());
    }

    /** Green for zero/positive (and {@code +} prefix); red for negative. */
    public static String amountColor(String amount) {
        if (amount == null) {
            return "green";
        }
        String t = amount.trim();
        if (t.startsWith("-") || t.startsWith("−") || t.startsWith("–")) {
            return "red";
        }
        return "green";
    }

    private static void appendGlyph(StringBuilder mm) {
        if (FancyHeadlines.isEnabled() && FancyHeadlines.useGlyph()) {
            mm.append("<dark_gray>| </dark_gray>");
        }
    }

    private static void appendTrailing(StringBuilder mm, String trailing) {
        if (trailing == null || trailing.isBlank()) {
            return;
        }
        String t = trailing.trim();
        String tone = t.toLowerCase();
        // Tone-only tokens: short glyph or omit — never print the raw word (avoids "openYYYY…" glitches).
        if ("open".equals(tone) || "todo".equals(tone)) {
            return;
        }
        String color = switch (tone) {
            case "done", "ok", "yes" -> "green";
            case "alert", "warn" -> "red";
            default -> "gray";
        };
        String label = switch (tone) {
            case "alert", "warn" -> "!";
            case "ok", "yes", "done" -> "ok";
            default -> t;
        };
        mm.append("<dark_gray> · </dark_gray><").append(color).append(">")
                .append(escape(label)).append("</").append(color).append(">");
    }

    private static String escape(String raw) {
        return MM.escapeTags(raw == null ? "" : raw);
    }

    private static String stripBrackets(String label) {
        String s = label.trim();
        if (s.startsWith("[") && s.endsWith("]") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
