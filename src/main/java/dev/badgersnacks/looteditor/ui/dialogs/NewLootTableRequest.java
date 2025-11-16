package dev.badgersnacks.looteditor.ui.dialogs;

import dev.badgersnacks.looteditor.services.LootTableService.LootTableTemplate;

/**
 * Simple DTO representing the user selections when creating a loot table.
 */
public record NewLootTableRequest(String namespace, String tablePath, LootTableTemplate template) {
}
