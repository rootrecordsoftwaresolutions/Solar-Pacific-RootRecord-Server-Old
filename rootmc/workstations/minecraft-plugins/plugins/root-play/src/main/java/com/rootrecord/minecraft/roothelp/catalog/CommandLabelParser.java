package com.rootrecord.minecraft.roothelp.catalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CommandLabelParser {

    private static final Pattern SLASH_WORD = Pattern.compile("/([a-zA-Z0-9_-]+)");
    private static final Pattern PAREN_ALIAS = Pattern.compile("\\(([^)]+)\\)");

    private CommandLabelParser() {}

    static String defaultTestKey(String cmd) {
        List<String> labels = labelsFrom(cmd);
        if (!labels.isEmpty()) {
            return labels.get(0).toLowerCase(Locale.ROOT);
        }
        return cmd.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    static List<String> labelsFrom(String cmd) {
        Set<String> out = new LinkedHashSet<>();
        if (cmd == null || cmd.isBlank()) {
            return List.of();
        }
        for (String segment : cmd.split("\\|")) {
            segment = segment.trim();
            Matcher slash = SLASH_WORD.matcher(segment);
            while (slash.find()) {
                out.add(slash.group(1).toLowerCase(Locale.ROOT));
            }
            Matcher parens = PAREN_ALIAS.matcher(segment);
            while (parens.find()) {
                for (String bit : parens.group(1).split("[,/]")) {
                    String alias = bit.trim().replaceFirst("^/", "");
                    if (!alias.isBlank()) {
                        out.add(alias.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }
}
