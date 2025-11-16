package dev.badgersnacks.looteditor.model;

import java.util.List;
import java.util.Objects;

/**
 * Persists how a loot table entry references a particular {@link EnchantmentPool}.
 * Stored separately so the editor can collapse/expand generated JSON entries when loading/saving.
 */
public record EnchantmentPoolLink(
        int orderIndex,
        String poolId,
        String itemId,
        String entryType,
        double weight,
        int minCount,
        int maxCount,
        List<LinkedEnchantment> enchantments
) {

    public EnchantmentPoolLink {
        Objects.requireNonNull(poolId, "poolId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(enchantments, "enchantments");
    }

    public record LinkedEnchantment(
            String enchantmentId,
            double weight,
            int minLevel,
            int maxLevel
    ) {
        public LinkedEnchantment {
            Objects.requireNonNull(enchantmentId, "enchantmentId");
        }
    }
}
