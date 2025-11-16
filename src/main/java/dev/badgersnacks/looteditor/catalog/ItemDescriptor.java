package dev.badgersnacks.looteditor.catalog;

import java.util.Objects;

/**
 * Describes an item or block entry that can be inserted into a loot table along with its icon bytes.
 */
public record ItemDescriptor(
        String namespace,
        String path,
        ItemType type,
        String displayName,
        byte[] iconData,
        String sourceLabel
) {

    public ItemDescriptor {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(sourceLabel, "sourceLabel");
    }

    public String qualifiedId() {
        return namespace + ":" + path;
    }

    public enum ItemType {
        ITEM,
        BLOCK
    }
}
