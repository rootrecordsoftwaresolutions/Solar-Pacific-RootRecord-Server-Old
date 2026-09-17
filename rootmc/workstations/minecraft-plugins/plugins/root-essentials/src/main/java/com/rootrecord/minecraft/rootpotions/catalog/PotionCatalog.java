package com.rootrecord.minecraft.rootpotions.catalog;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PotionCatalog {

    private final List<PotionEntry> entries;
    private final Map<String, PotionEntry> byId;

    private PotionCatalog(List<PotionEntry> entries) {
        this.entries = List.copyOf(entries);
        Map<String, PotionEntry> map = new LinkedHashMap<>();
        for (PotionEntry entry : entries) {
            map.put(entry.id(), entry);
        }
        this.byId = Map.copyOf(map);
    }

    public static PotionCatalog load(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("potions");
        if (section == null) {
            return new PotionCatalog(List.of());
        }
        List<PotionEntry> loaded = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection potion = section.getConfigurationSection(id);
            if (potion == null) {
                continue;
            }
            loaded.add(PotionEntry.from(id, potion));
        }
        loaded.sort(Comparator.comparing(PotionEntry::name, String.CASE_INSENSITIVE_ORDER));
        return new PotionCatalog(loaded);
    }

    public List<PotionEntry> all() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public Optional<PotionEntry> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<PotionEntry> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return entries.stream()
                .map(entry -> Map.entry(entry, entry.matchScore(query)))
                .filter(pair -> pair.getValue() > 0)
                .sorted(Comparator.<Map.Entry<PotionEntry, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(pair -> pair.getKey().name(), String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getKey)
                .toList();
    }
}
