package dev.badgersnacks.looteditor.services;

import com.fasterxml.jackson.databind.JsonNode;
import dev.badgersnacks.looteditor.model.LootPoolEntryModel;
import dev.badgersnacks.looteditor.model.LootTableDescriptor;
import dev.badgersnacks.looteditor.model.LootTableDescriptor.SourceType;
import dev.badgersnacks.looteditor.services.LootTableService.LootTableTemplate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTableServiceTest {

    private final LootTableService service = new LootTableService();

    @Test
    void rebuildTableUsesSelectedEntries() {
        JsonNode template = null;
        LootPoolEntryModel diamond = new LootPoolEntryModel("minecraft:diamond", 3.0, "minecraft:item", 1, 3, null);
        LootPoolEntryModel apple = new LootPoolEntryModel("minecraft:apple", 1.0, "minecraft:item", 2, 5, null);
        JsonNode rebuilt = service.rebuildTable(template, List.of(diamond, apple));
        var entries = service.extractEntries(rebuilt);
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.itemId().equals("minecraft:diamond") && e.minCount() == 1 && e.maxCount() == 3));
        assertTrue(entries.stream().anyMatch(e -> e.itemId().equals("minecraft:apple") && e.minCount() == 2 && e.maxCount() == 5));
    }

    @Test
    void createTableWritesTemplate() throws IOException {
        Path root = Files.createTempDirectory("loot-editor-test");
        try {
            LootTableDescriptor descriptor = service.createTable(root, "dbb", "entities/test_mob", LootTableTemplate.ENTITY_DROPS);
            assertTrue(Files.exists(descriptor.containerPath()));
            JsonNode loaded = service.load(descriptor);
            assertEquals("minecraft:entity", loaded.path("type").asText());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void forkCopiesReadOnlyDescriptor() throws IOException {
        Path root = Files.createTempDirectory("loot-editor-test");
        try {
            Path sourceFile = root.resolve("external.json");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, """
                    {
                      "type": "minecraft:block",
                      "pools": [
                        {
                          "rolls": 1,
                          "entries": [
                            { "type": "minecraft:item", "name": "minecraft:stone" }
                          ]
                        }
                      ]
                    }
                    """);
            LootTableDescriptor descriptor = new LootTableDescriptor(
                    "test",
                    "blocks/test_block",
                    sourceFile,
                    null,
                    "Test Source",
                    SourceType.MOD_JAR,
                    false);
            LootTableDescriptor forked = service.forkToKubeJs(root, descriptor);
            assertTrue(Files.exists(forked.containerPath()));
            JsonNode forkedNode = service.load(forked);
            assertEquals("minecraft:block", forkedNode.path("type").asText());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
    }
}
