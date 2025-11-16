package dev.badgersnacks.looteditor.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the merged loot manifest JSON produced by {@code LootManifestMerger}.
 */
public final class MergedManifestLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Optional<MergedManifest> load(Path manifestPath) throws IOException {
        if (!Files.exists(manifestPath)) {
            return Optional.empty();
        }
        JsonNode root = MAPPER.readTree(manifestPath.toFile());
        if (!root.isObject()) {
            throw new IOException("Manifest " + manifestPath + " is not a JSON object");
        }

        String packRootText = textValue(root.get("packRoot"));
        Path packRoot = packRootText != null ? Paths.get(packRootText).toAbsolutePath().normalize() : null;
        List<String> ids = new ArrayList<>();
        JsonNode tablesNode = root.get("tables");
        if (tablesNode != null && tablesNode.isArray()) {
            for (JsonNode table : tablesNode) {
                String id = textValue(table.get("id"));
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }

        return Optional.of(new MergedManifest(manifestPath.toAbsolutePath().normalize(), packRoot, ids));
    }

    private static String textValue(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    public record MergedManifest(Path manifestPath, Path packRoot, List<String> tableIds) {
    }
}
