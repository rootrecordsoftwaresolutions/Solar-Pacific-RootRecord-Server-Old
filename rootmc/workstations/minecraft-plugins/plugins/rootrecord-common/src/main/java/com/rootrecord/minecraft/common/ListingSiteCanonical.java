package com.rootrecord.minecraft.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonical listing-site ids for Votifier service names (governance total votes + rewards audit).
 * Matches root-rewards.yml vote links — one site = one vote point.
 */
public final class ListingSiteCanonical {

    private static final SiteRule[] RULES = {
            new SiteRule("minecraft-mp", Pattern.compile("minecraft-?mp", Pattern.CASE_INSENSITIVE)),
            new SiteRule("minecraftservers.org", Pattern.compile("minecraftservers\\.org", Pattern.CASE_INSENSITIVE)),
            new SiteRule("minecraft-server-list", Pattern.compile("mcsl|minecraft[- ]?server[- ]?list", Pattern.CASE_INSENSITIVE)),
            new SiteRule("minecraftlist.org", Pattern.compile("^(minecraftserverslist|minecraftlist\\.org)$", Pattern.CASE_INSENSITIVE)),
            new SiteRule("minecraft.buzz", Pattern.compile(
                    "minecraft[\\s._-]*buzz|mc[\\s._-]*buzz", Pattern.CASE_INSENSITIVE)),
            new SiteRule("topminecraftservers", Pattern.compile("topminecraftservers", Pattern.CASE_INSENSITIVE)),
            new SiteRule("minerank", Pattern.compile("minerank", Pattern.CASE_INSENSITIVE)),
            new SiteRule("planetminecraft", Pattern.compile("planet\\s*minecraft|planetminecraft", Pattern.CASE_INSENSITIVE)),
    };

    private ListingSiteCanonical() {
    }

    public static String canonicalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("default".equals(s)) {
            return "unknown";
        }
        for (SiteRule rule : RULES) {
            if (rule.pattern.matcher(s).find()) {
                return rule.id;
            }
        }
        return s;
    }

    private record SiteRule(String id, Pattern pattern) {
    }
}
