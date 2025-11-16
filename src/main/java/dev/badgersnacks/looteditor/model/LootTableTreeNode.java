package dev.badgersnacks.looteditor.model;

import java.util.Objects;

/**
 * Wrapper model so TreeView nodes can either be grouping labels or real descriptors.
 */
public final class LootTableTreeNode {

    private final String label;
    private final LootTableDescriptor descriptor;

    public LootTableTreeNode(String label) {
        this(label, null);
    }

    public LootTableTreeNode(String label, LootTableDescriptor descriptor) {
        this.label = Objects.requireNonNull(label, "label");
        this.descriptor = descriptor;
    }

    public String label() {
        return label;
    }

    public LootTableDescriptor descriptor() {
        return descriptor;
    }

    public boolean isLeaf() {
        return descriptor != null;
    }

    @Override
    public String toString() {
        return label;
    }
}
