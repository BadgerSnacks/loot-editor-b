package dev.badgersnacks.looteditor.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DataPackServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void writesPackMetaUsingInstanceVersion() throws IOException {
        String embeddedJson = """
                {"baseModLoader":{"versionJson":"{\\"inheritsFrom\\":\\"1.21.1\\"}"}}
                """;
        Files.writeString(tempDir.resolve("minecraftinstance.json"), embeddedJson);
        DataPackService service = new DataPackService(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
        Path lootPath = service.resolveLootTablePath(tempDir, "minecraft", "chests/ancient_city");
        assertTrue(Files.exists(lootPath.getParent()));
        Path packMeta = tempDir.resolve("datapacks")
                .resolve(DataPackService.PACK_FOLDER)
                .resolve("pack.mcmeta");
        String metaContent = Files.readString(packMeta);
        assertTrue(metaContent.contains("\"pack_format\" : 48"), "Expected pack_format 48 for 1.21.1");
    }

    @Test
    void usesFallbackFormatWhenInstanceMissing() throws IOException {
        DataPackService service = new DataPackService(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
        service.resolveLootTablePath(tempDir, "minecraft", "chests/test");
        Path packMeta = tempDir.resolve("datapacks")
                .resolve(DataPackService.PACK_FOLDER)
                .resolve("pack.mcmeta");
        String metaContent = Files.readString(packMeta);
        assertTrue(metaContent.contains("\"pack_format\" : 71"), "Expected default pack_format fallback");
    }
}
