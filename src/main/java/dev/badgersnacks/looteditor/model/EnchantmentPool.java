package dev.badgersnacks.looteditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Represents a reusable collection of weighted enchantments that can be attached to loot entries.
 */
public final class EnchantmentPool {

    private final String namespace;
    private final String name;
    private final String displayName;
    private final boolean treasureAllowed;
    private final List<EnchantmentPoolEntry> entries;

    public EnchantmentPool(String namespace,
                           String name,
                           String displayName,
                           boolean treasureAllowed,
                           List<EnchantmentPoolEntry> entries) {
        this.namespace = normalize(namespace);
        this.name = normalizeName(name);
        this.displayName = (displayName == null || displayName.isBlank())
                ? this.name
                : displayName.trim();
        this.treasureAllowed = treasureAllowed;
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
    }

    private String normalize(String ns) {
        if (ns == null || ns.isBlank()) {
            return "loot_editor";
        }
        return ns.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Pool name is required");
        }
        String normalized = rawName.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_');
        if (normalized.contains(":") || normalized.contains("/")) {
            throw new IllegalArgumentException("Pool name must not contain namespace separators or slashes");
        }
        return normalized;
    }

    public String namespace() {
        return namespace;
    }

    public String name() {
        return name;
    }

    public String id() {
        return namespace + ":" + name;
    }

    public String displayName() {
        return displayName;
    }

    public boolean treasureAllowed() {
        return treasureAllowed;
    }

    public List<EnchantmentPoolEntry> entries() {
        return entries;
    }

    public EnchantmentPool withEntries(List<EnchantmentPoolEntry> updated) {
        return new EnchantmentPool(namespace, name, displayName, treasureAllowed, updated);
    }

    public EnchantmentPool withTreasureAllowed(boolean allowed) {
        return new EnchantmentPool(namespace, name, displayName, allowed, entries);
    }

    public EnchantmentPool withDisplayName(String label) {
        return new EnchantmentPool(namespace, name, label, treasureAllowed, entries);
    }
}
