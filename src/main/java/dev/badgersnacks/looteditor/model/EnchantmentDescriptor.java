package dev.badgersnacks.looteditor.model;

/**
 * Lightweight view model describing a single enchantment entry for palette browsing.
 */
public record EnchantmentDescriptor(
        String id,
        String namespace,
        String displayName
) {
}
