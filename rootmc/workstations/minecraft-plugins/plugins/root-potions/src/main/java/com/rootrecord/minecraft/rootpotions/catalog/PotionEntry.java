package com.rootrecord.minecraft.rootpotions.catalog;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record PotionEntry(
        String id,
        String name,
        String ingredient,
        List<String> aliases,
        List<String> lines) {

    public static PotionEntry from(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String ingredient = section.getString("ingredient", "");
        List<String> aliases = section.getStringList("aliases");
        List<String> lines = new ArrayList<>();
        for (String line : section.getStringList("lines")) {
            if (line != null && !line.isBlank()) {
                lines.add(line);
            }
        }
        return new PotionEntry(id.toLowerCase(Locale.ROOT), name, ingredient, List.copyOf(aliases), List.copyOf(lines));
    }

    public int matchScore(String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (id.equals(q)) {
            return 100;
        }
        String nameLower = name.toLowerCase(Locale.ROOT);
        if (nameLower.equals(q)) {
            return 95;
        }
        if (nameLower.contains(q) || id.contains(q)) {
            return 80;
        }
        String ing = ingredient.toLowerCase(Locale.ROOT);
        if (ing.contains(q)) {
            return 70;
        }
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            String a = alias.toLowerCase(Locale.ROOT);
            if (a.equals(q)) {
                return 90;
            }
            if (a.contains(q) || q.contains(a)) {
                return 60;
            }
        }
        for (String line : lines) {
            if (line.toLowerCase(Locale.ROOT).contains(q)) {
                return 40;
            }
        }
        return 0;
    }
}
