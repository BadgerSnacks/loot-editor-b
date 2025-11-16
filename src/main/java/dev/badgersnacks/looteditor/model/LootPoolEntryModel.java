package dev.badgersnacks.looteditor.model;

import java.util.Objects;

/**
 * Lightweight view model used by the drag-and-drop editor.
 */
public record LootPoolEntryModel(
        String itemId,
        double weight,
        String entryType,
        int minCount,
        int maxCount,
        String enchantmentPoolId
) {

    public LootPoolEntryModel {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(entryType, "entryType");
        if (minCount < 1) {
            minCount = 1;
        }
        if (maxCount < minCount) {
            maxCount = minCount;
        }
    }

    public LootPoolEntryModel withWeight(double weight) {
        return new LootPoolEntryModel(itemId, weight, entryType, minCount, maxCount, enchantmentPoolId);
    }

    public LootPoolEntryModel withCounts(int min, int max) {
        return new LootPoolEntryModel(itemId, weight, entryType, min, max, enchantmentPoolId);
    }

    public LootPoolEntryModel withEnchantmentPool(String poolId) {
        return new LootPoolEntryModel(itemId, weight, entryType, minCount, maxCount, poolId);
    }
}
