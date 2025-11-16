package dev.badgersnacks.looteditor.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads optional per-pack export settings so Loot Editor can mirror its datapack output into custom
 * folders (e.g., loot-editor-b/export/datapack for the loader companion mod).
 */
public final class ExportSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportSettings.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path CONFIG_RELATIVE_PATH = Path.of("loot-editor-b", "export-settings.json");

    /**
     * Returns the override pack root configured for the given modpack, if any.
     *
     * <p>The JSON file must live at <modpack>/loot-editor-b/export-settings.json and include a
     * string property named {@code packRoot}. Relative paths are resolved against the modpack
     * directory; absolute paths are used as-is. Missing or malformed files fall back to the
     * built-in datapack path.
     */
    public Optional<Path> resolvePackRoot(Path modpackRoot) {
        Path configFile = modpackRoot.resolve(CONFIG_RELATIVE_PATH);
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }
        try {
            JsonNode node = MAPPER.readTree(configFile.toFile());
            String packRootText = textValue(node.get("packRoot"));
            if (packRootText == null || packRootText.isBlank()) {
                LOGGER.warn("packRoot missing or empty in {}", configFile);
                return Optional.empty();
            }
            Path resolvedPath = resolvePath(modpackRoot, packRootText.trim());
            LOGGER.info("Using custom export root {} from {}", resolvedPath, configFile);
            return Optional.of(resolvedPath);
        } catch (IOException e) {
            LOGGER.warn("Failed to load export settings from {}", configFile, e);
            return Optional.empty();
        }
    }

    private Path resolvePath(Path modpackRoot, String packRootText) {
        Path candidate = Paths.get(packRootText);
        if (!candidate.isAbsolute()) {
            candidate = modpackRoot.resolve(candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private static String textValue(JsonNode node) {
        return node != null ? node.asText(null) : null;
    }
}

