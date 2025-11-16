package dev.badgersnacks.looteditor.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Persists the most recently opened modpack roots to %USERPROFILE%/.loot-editor-b/packs.json.
 */
public final class RecentPackStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecentPackStorage.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ENTRIES = 5;

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private final Path storageFile;
    private List<RecentPackEntry> entries = new ArrayList<>();

    public RecentPackStorage() {
        this.storageFile = Path.of(System.getProperty("user.home"), ".loot-editor-b", "packs.json");
        load();
    }

    public synchronized List<RecentPackEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public synchronized void record(Path modpackRoot) {
        Objects.requireNonNull(modpackRoot, "modpackRoot");
        Path normalized = modpackRoot.toAbsolutePath().normalize();
        String pathString = normalized.toString();
        entries = entries.stream()
                .filter(entry -> !entry.path().equals(pathString))
                .collect(Collectors.toCollection(ArrayList::new));
        entries.add(0, new RecentPackEntry(pathString, Instant.now()));
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }
        save();
    }

    public synchronized void remove(Path modpackRoot) {
        Objects.requireNonNull(modpackRoot, "modpackRoot");
        String normalized = modpackRoot.toAbsolutePath().normalize().toString();
        boolean changed = entries.removeIf(entry -> entry.path().equals(normalized));
        if (changed) {
            save();
        }
    }

    private void load() {
        try {
            if (Files.exists(storageFile)) {
                RecentPackList wrapper = MAPPER.readValue(storageFile.toFile(), RecentPackList.class);
                entries = new ArrayList<>(wrapper.entries());
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read recent pack list from {}", storageFile, e);
            entries = new ArrayList<>();
        }
    }

    private void save() {
        try {
            Files.createDirectories(storageFile.getParent());
            RecentPackList wrapper = new RecentPackList(entries);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(storageFile.toFile(), wrapper);
        } catch (IOException e) {
            LOGGER.warn("Failed to write recent pack list to {}", storageFile, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RecentPackList(@JsonProperty("entries") List<RecentPackEntry> entries) {
        private RecentPackList {
            if (entries == null) {
                entries = List.of();
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecentPackEntry(@JsonProperty("path") String path,
                                  @JsonProperty("lastOpened") Instant lastOpened) {
        public Path toPath() {
            return Path.of(path);
        }
    }
}
