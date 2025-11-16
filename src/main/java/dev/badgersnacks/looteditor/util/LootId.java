package dev.badgersnacks.looteditor.util;

import java.util.Objects;

/**
 * Simple namespace:path identifier used to avoid depending on Minecraft classes in the desktop app.
 */
public record LootId(String namespace, String path) {
    public LootId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
    }

    public static LootId of(String namespace, String path) {
        return new LootId(namespace, path);
    }

    public static LootId parse(String id) {
        Objects.requireNonNull(id, "id");
        int idx = id.indexOf(':');
        if (idx <= 0 || idx == id.length() - 1) {
            throw new IllegalArgumentException("Invalid loot id: " + id);
        }
        return new LootId(id.substring(0, idx), id.substring(idx + 1));
    }

    public String asString() {
        return namespace + ":" + path;
    }
}

