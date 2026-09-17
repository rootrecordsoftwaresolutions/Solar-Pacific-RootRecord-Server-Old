package com.rootrecord.minecraft.rootcore.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal parser for rootmc.net/plugins/manifest.json (no JSON library). */
public final class PluginManifest {

    public record Entry(String id, String version, String filename, String url) {}

    private PluginManifest() {}

    public static Map<String, Entry> parse(String json) {
        Map<String, Entry> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        String s = json.trim();
        // PowerShell Set-Content often writes a UTF-8 BOM; strip so "{" is first char.
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1).trim();
        }
        if (!s.startsWith("{")) {
            return out;
        }
        int i = 1;
        while (i < s.length()) {
            i = skipWs(s, i);
            if (i >= s.length() || s.charAt(i) == '}') {
                break;
            }
            if (s.charAt(i) == ',') {
                i++;
                continue;
            }
            StringParse key = readString(s, i);
            if (key == null) {
                break;
            }
            i = skipWs(s, key.end);
            if (i >= s.length() || s.charAt(i) != ':') {
                break;
            }
            i = skipWs(s, i + 1);
            if (i >= s.length() || s.charAt(i) != '{') {
                // skip non-object values
                i = skipValue(s, i);
                continue;
            }
            ObjectParse obj = readObjectFields(s, i);
            if (obj == null) {
                break;
            }
            String version = obj.fields.getOrDefault("version", "");
            String filename = obj.fields.getOrDefault("filename", "");
            String url = obj.fields.getOrDefault("url", "");
            if (!key.value.isBlank() && !version.isBlank() && !filename.isBlank() && !url.isBlank()) {
                out.put(key.value, new Entry(key.value, version, filename, url));
            }
            i = obj.end;
        }
        return out;
    }

    /** Compare dotted versions; returns &gt;0 if a newer than b. */
    public static int compareVersions(String a, String b) {
        int[] aa = segments(a);
        int[] bb = segments(b);
        int n = Math.max(aa.length, bb.length);
        for (int i = 0; i < n; i++) {
            int x = i < aa.length ? aa[i] : 0;
            int y = i < bb.length ? bb[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int[] segments(String version) {
        if (version == null || version.isBlank()) {
            return new int[] {0};
        }
        String cleaned = version.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("v")) {
            cleaned = cleaned.substring(1);
        }
        // drop -SNAPSHOT / +build
        int cut = cleaned.indexOf('-');
        if (cut >= 0) {
            cleaned = cleaned.substring(0, cut);
        }
        cut = cleaned.indexOf('+');
        if (cut >= 0) {
            cleaned = cleaned.substring(0, cut);
        }
        String[] parts = cleaned.split("\\.");
        List<Integer> nums = new ArrayList<>(parts.length);
        for (String part : parts) {
            String digits = part.replaceAll("[^0-9].*$", "");
            if (digits.isEmpty()) {
                nums.add(0);
            } else {
                try {
                    nums.add(Integer.parseInt(digits));
                } catch (NumberFormatException e) {
                    nums.add(0);
                }
            }
        }
        int[] out = new int[nums.size()];
        for (int i = 0; i < nums.size(); i++) {
            out[i] = nums.get(i);
        }
        return out;
    }

    private record StringParse(String value, int end) {}

    private record ObjectParse(Map<String, String> fields, int end) {}

    private static StringParse readString(String s, int start) {
        int i = skipWs(s, start);
        if (i >= s.length() || s.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '\\' && i < s.length()) {
                sb.append(s.charAt(i++));
                continue;
            }
            if (c == '"') {
                return new StringParse(sb.toString(), i);
            }
            sb.append(c);
        }
        return null;
    }

    private static ObjectParse readObjectFields(String s, int start) {
        int i = skipWs(s, start);
        if (i >= s.length() || s.charAt(i) != '{') {
            return null;
        }
        i++;
        Map<String, String> fields = new LinkedHashMap<>();
        while (i < s.length()) {
            i = skipWs(s, i);
            if (i < s.length() && s.charAt(i) == '}') {
                return new ObjectParse(fields, i + 1);
            }
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            StringParse key = readString(s, i);
            if (key == null) {
                return null;
            }
            i = skipWs(s, key.end);
            if (i >= s.length() || s.charAt(i) != ':') {
                return null;
            }
            i = skipWs(s, i + 1);
            if (i < s.length() && s.charAt(i) == '"') {
                StringParse val = readString(s, i);
                if (val == null) {
                    return null;
                }
                fields.put(key.value, val.value);
                i = val.end;
            } else {
                i = skipValue(s, i);
            }
        }
        return null;
    }

    private static int skipValue(String s, int start) {
        int i = skipWs(s, start);
        if (i >= s.length()) {
            return i;
        }
        char c = s.charAt(i);
        if (c == '"') {
            StringParse sp = readString(s, i);
            return sp != null ? sp.end : s.length();
        }
        if (c == '{') {
            ObjectParse op = readObjectFields(s, i);
            return op != null ? op.end : s.length();
        }
        if (c == '[') {
            int depth = 1;
            i++;
            while (i < s.length() && depth > 0) {
                char ch = s.charAt(i++);
                if (ch == '"') {
                    StringParse sp = readString(s, i - 1);
                    i = sp != null ? sp.end : s.length();
                } else if (ch == '[') {
                    depth++;
                } else if (ch == ']') {
                    depth--;
                }
            }
            return i;
        }
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == ',' || ch == '}' || ch == ']') {
                break;
            }
            i++;
        }
        return i;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }
}
