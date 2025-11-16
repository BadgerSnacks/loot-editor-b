package dev.badgersnacks.looteditor.agents;

import dev.badgersnacks.looteditor.catalog.ItemCatalog;
import dev.badgersnacks.looteditor.catalog.ItemCatalogService;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Builds the icon + item catalog for a modpack in the background.
 */
public class ItemCatalogAgentTask implements AgentTask<ItemCatalog> {

    private final Path modpackRoot;
    private final ItemCatalogService catalogService;

    public ItemCatalogAgentTask(Path modpackRoot, ItemCatalogService catalogService) {
        this.modpackRoot = Objects.requireNonNull(modpackRoot, "modpackRoot");
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
    }

    @Override
    public String name() {
        return "item-catalog";
    }

    @Override
    public ItemCatalog run() throws Exception {
        return catalogService.buildCatalog(modpackRoot);
    }
}
