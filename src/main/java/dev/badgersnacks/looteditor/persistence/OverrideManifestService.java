package dev.badgersnacks.looteditor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the override manifest that tracks which loot tables are replaced via GLMs.
 */
public final class OverrideManifestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverrideManifestService.class);
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final OverridePaths overridePaths = new OverridePaths();

    public OverrideManifest load(Path packRoot) {
        Path file = overridePaths.manifestFile(packRoot);
        if (!Files.isRegularFile(file)) {
            return new OverrideManifest(null);
        }
        try {
            return mapper.readValue(file.toFile(), OverrideManifest.class);
        } catch (IOException e) {
            LOGGER.warn("Failed to read override manifest at {}. Starting fresh.", file, e);
            return new OverrideManifest(null);
        }
    }

    public void save(Path packRoot, OverrideManifest manifest) throws IOException {
        Path file = overridePaths.manifestFile(packRoot);
        Files.createDirectories(file.getParent());
        mapper.writeValue(file.toFile(), manifest);
    }
}

