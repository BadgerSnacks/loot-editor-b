package dev.badgersnacks.looteditor.model;

import java.util.Objects;

/**
 * Represents a single weighted enchantment option inside an {@link EnchantmentPool}.
 */
public record EnchantmentPoolEntry(
        String enchantmentId,
        double weight,
        int minLevel,
        int maxLevel
) {

    public EnchantmentPoolEntry {
        Objects.requireNonNull(enchantmentId, "enchantmentId");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be > 0");
        }
        if (minLevel < 1) {
            minLevel = 1;
        }
        if (maxLevel < minLevel) {
            maxLevel = minLevel;
        }
    }

    public EnchantmentPoolEntry withLevels(int min, int max) {
        return new EnchantmentPoolEntry(enchantmentId, weight, min, max);
    }

    public EnchantmentPoolEntry withWeight(double updatedWeight) {
        return new EnchantmentPoolEntry(enchantmentId, updatedWeight, minLevel, maxLevel);
    }
}
