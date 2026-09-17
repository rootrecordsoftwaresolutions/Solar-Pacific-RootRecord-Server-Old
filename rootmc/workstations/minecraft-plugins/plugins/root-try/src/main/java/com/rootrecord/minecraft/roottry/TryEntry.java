package com.rootrecord.minecraft.roottry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** One tryout entry from catalog.yml. */
public final class TryEntry {

    private final String id;
    private final String title;
    private final String hint;
    private final String host;
    private final List<String> match;
    private final double rewardG;

    public TryEntry(
            String id,
            String title,
            String hint,
            String host,
            List<String> match,
            double rewardG) {
        this.id = id;
        this.title = title;
        this.hint = hint;
        this.host = host == null || host.isBlank() ? "both" : host.trim().toLowerCase(Locale.ROOT);
        List<String> m = new ArrayList<>();
        if (match != null) {
            for (String s : match) {
                if (s == null || s.isBlank()) {
                    continue;
                }
                String n = s.trim().toLowerCase(Locale.ROOT);
                if (!n.startsWith("/")) {
                    n = "/" + n;
                }
                m.add(n);
            }
        }
        this.match = Collections.unmodifiableList(m);
        this.rewardG = rewardG;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String hint() {
        return hint;
    }

    public String host() {
        return host;
    }

    public List<String> match() {
        return match;
    }

    public double rewardG() {
        return rewardG;
    }

    public boolean appliesToHost(String activeHost) {
        if ("both".equals(host)) {
            return true;
        }
        return host.equalsIgnoreCase(activeHost);
    }

    public boolean matchesCommandLine(String raw) {
        return longestMatchLength(raw) > 0;
    }

    /** Length of the best matching path, or 0 if none. */
    public int longestMatchLength(String raw) {
        if (raw == null || raw.isBlank() || match.isEmpty()) {
            return 0;
        }
        String line = raw.trim().toLowerCase(Locale.ROOT);
        if (!line.startsWith("/")) {
            line = "/" + line;
        }
        int best = 0;
        for (String path : match) {
            if (line.equals(path) || line.startsWith(path + " ")) {
                if (path.length() > best) {
                    best = path.length();
                }
            }
        }
        return best;
    }
}
