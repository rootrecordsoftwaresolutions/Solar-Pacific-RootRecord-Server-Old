package com.rootrecord.minecraft.rootterritories.model;

import java.util.Set;

/** Town and nation wilderness rings containing a surface block. */
public record WildernessAt(Set<String> townNames, Set<String> nationNames) {

    public static WildernessAt empty() {
        return new WildernessAt(Set.of(), Set.of());
    }

    public boolean isEmpty() {
        return townNames.isEmpty() && nationNames.isEmpty();
    }
}
