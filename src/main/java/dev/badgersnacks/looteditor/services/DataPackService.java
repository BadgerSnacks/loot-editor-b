package dev.badgersnacks.looteditor.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.badgersnacks.looteditor.persistence.ExportSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Handles creation and upkeep of the Loot Editor datapack.
 */
public class DataPackService {

    public static final String PACK_FOLDER = "loot_editor";
    private static final String PACK_DESCRIPTION = "Loot Editor datapack exports";
    private static final Logger LOGGER = LoggerFactory.getLogger(DataPackService.class);

    private final ObjectMapper mapper;
    private final ExportSettings exportSettings = new ExportSettings();

    public DataPackService(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path resolveLootTablePath(Path modpackRoot, String namespace, String tablePath) throws IOException {
        Path packRoot = ensurePackRoot(modpackRoot);
        String normalizedNamespace = normalize(namespace);
        String normalizedPath = normalizeTablePath(tablePath);
        Path lootDir = packRoot.resolve("data")
                .resolve(normalizedNamespace)
                .resolve("loot_table");
        Path target = lootDir.resolve(normalizedPath + ".json");
        Files.createDirectories(target.getParent());
        return target;
    }

    public Path ensurePackRoot(Path modpackRoot) throws IOException {
        Path packRoot = exportSettings.resolvePackRoot(modpackRoot)
                .orElseGet(() -> modpackRoot.resolve("datapacks").resolve(PACK_FOLDER));
        Path parent = packRoot.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createDirectories(packRoot);
        writePackMeta(packRoot, resolvePackFormat(modpackRoot));
        Files.createDirectories(packRoot.resolve("data"));
        return packRoot;
    }

    public List<Path> syncWorldDatapacks(Path modpackRoot) throws IOException {
        Path packRoot = ensurePackRoot(modpackRoot);
        Path savesDir = modpackRoot.resolve("saves");
        if (!Files.isDirectory(savesDir)) {
            return Collections.emptyList();
        }
        List<Path> updatedWorlds = new ArrayList<>();
        try (Stream<Path> worlds = Files.list(savesDir)) {
            worlds.filter(Files::isDirectory)
                    .filter(world -> Files.exists(world.resolve("level.dat")))
                    .forEach(world -> {
                        Path worldDatapackDir = world.resolve("datapacks").resolve(PACK_FOLDER);
                        try {
                            copyDirectory(packRoot, worldDatapackDir);
                            updatedWorlds.add(world);
                            LOGGER.info("Synced loot_editor datapack into world {}", world.getFileName());
                        } catch (IOException e) {
                            LOGGER.warn("Failed to sync datapack into {}", world, e);
                        }
                    });
        }
        return updatedWorlds;
    }

    private void writePackMeta(Path packRoot, int packFormat) throws IOException {
        Path packMeta = packRoot.resolve("pack.mcmeta");
        boolean needsWrite = true;
        if (Files.exists(packMeta)) {
            try {
                JsonNode existing = mapper.readTree(packMeta.toFile());
                int existingFormat = existing.path("pack").path("pack_format").asInt(-1);
                if (existingFormat == packFormat) {
                    needsWrite = false;
                }
            } catch (IOException ignored) {
                needsWrite = true;
            }
        }
        if (needsWrite) {
            ObjectNode pack = mapper.createObjectNode();
            pack.put("pack_format", packFormat);
            pack.put("description", PACK_DESCRIPTION);
            ObjectNode root = mapper.createObjectNode();
            root.set("pack", pack);
            mapper.writeValue(packMeta.toFile(), root);
        }
        Files.setLastModifiedTime(packMeta, FileTime.from(Instant.now()));
    }

    private int resolvePackFormat(Path modpackRoot) {
        Version version = detectMinecraftVersion(modpackRoot).orElse(null);
        if (version == null) {
            return PackFormatRule.DEFAULT_FORMAT;
        }
        return PackFormatRule.resolve(version);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (source.equals(target)) {
            return;
        }
        deleteDirectory(target);
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void deleteDirectory(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            walk.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private Optional<Version> detectMinecraftVersion(Path modpackRoot) {
        Path instanceFile = modpackRoot.resolve("minecraftinstance.json");
        if (!Files.exists(instanceFile)) {
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(instanceFile.toFile());
            JsonNode baseModLoader = root.path("baseModLoader");
            String version = null;
            if (baseModLoader.hasNonNull("minecraftVersion")) {
                version = baseModLoader.get("minecraftVersion").asText();
            }
            if ((version == null || version.isBlank()) && baseModLoader.hasNonNull("versionJson")) {
                String rawJson = baseModLoader.get("versionJson").asText();
                JsonNode nested = mapper.readTree(rawJson);
                if (nested.hasNonNull("inheritsFrom")) {
                    version = nested.get("inheritsFrom").asText();
                }
                if ((version == null || version.isBlank()) && nested.hasNonNull("id")) {
                    version = nested.get("id").asText();
                }
            }
            if ((version == null || version.isBlank()) && root.hasNonNull("minecraftVersion")) {
                version = root.get("minecraftVersion").asText();
            }
            return Version.parse(version);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String normalize(String namespace) throws IOException {
        if (namespace == null || namespace.isBlank()) {
            throw new IOException("Namespace is required");
        }
        return namespace.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeTablePath(String tablePath) throws IOException {
        if (tablePath == null || tablePath.isBlank()) {
            throw new IOException("Table path is required");
        }
        String normalized = tablePath.trim().replace('\\', '/');
        if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized;
    }

    private static final class PackFormatRule {
        private static final List<PackFormatRule> RULES = new ArrayList<>();
        private static final int DEFAULT_FORMAT = 71;

        static {
            RULES.add(new PackFormatRule(new Version(1, 21, 5), null, 71));
            RULES.add(new PackFormatRule(new Version(1, 21, 4), new Version(1, 21, 4), 61));
            RULES.add(new PackFormatRule(new Version(1, 21, 2), new Version(1, 21, 3, 99), 57));
            RULES.add(new PackFormatRule(new Version(1, 21, 0), new Version(1, 21, 1, 99), 48));
            RULES.add(new PackFormatRule(new Version(1, 20, 5), new Version(1, 20, 6, 99), 41));
            RULES.add(new PackFormatRule(new Version(1, 20, 3), new Version(1, 20, 4, 99), 32));
            RULES.add(new PackFormatRule(new Version(1, 20, 2), new Version(1, 20, 2, 99), 18));
            RULES.add(new PackFormatRule(new Version(1, 20, 0), new Version(1, 20, 1, 99), 15));
            RULES.add(new PackFormatRule(new Version(1, 19, 4), new Version(1, 19, 4, 99), 12));
            RULES.add(new PackFormatRule(new Version(1, 19, 0), new Version(1, 19, 3, 99), 10));
            RULES.add(new PackFormatRule(new Version(1, 18, 2), new Version(1, 18, 2, 99), 9));
            RULES.add(new PackFormatRule(new Version(1, 18, 0), new Version(1, 18, 1, 99), 8));
            RULES.add(new PackFormatRule(new Version(1, 17, 0), new Version(1, 17, 1, 99), 7));
            RULES.add(new PackFormatRule(new Version(1, 16, 2), new Version(1, 16, 5, 99), 6));
            RULES.add(new PackFormatRule(new Version(1, 15, 0), new Version(1, 16, 1, 99), 5));
            RULES.add(new PackFormatRule(new Version(1, 13, 0), new Version(1, 14, 4, 99), 4));
        }

        private final Version min;
        private final Version max;
        private final int format;

        private PackFormatRule(Version min, Version max, int format) {
            this.min = min;
            this.max = max;
            this.format = format;
        }

        private static int resolve(Version version) {
            for (PackFormatRule rule : RULES) {
                if (version.compareTo(rule.min) >= 0
                        && (rule.max == null || version.compareTo(rule.max) <= 0)) {
                    return rule.format;
                }
            }
            return DEFAULT_FORMAT;
        }
    }

    static final class Version implements Comparable<Version> {
        private final int major;
        private final int minor;
        private final int patch;
        private final int build;

        Version(int major, int minor, int patch) {
            this(major, minor, patch, 0);
        }

        Version(int major, int minor, int patch, int build) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.build = build;
        }

        static Optional<Version> parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String cleaned = raw.trim();
            StringBuilder digits = new StringBuilder();
            for (char c : cleaned.toCharArray()) {
                if (Character.isDigit(c) || c == '.') {
                    digits.append(c);
                } else {
                    break;
                }
            }
            if (digits.length() == 0) {
                return Optional.empty();
            }
            String[] parts = digits.toString().split("\\.");
            int major = parsePart(parts, 0);
            int minor = parsePart(parts, 1);
            int patch = parsePart(parts, 2);
            return Optional.of(new Version(major, minor, patch));
        }

        private static int parsePart(String[] parts, int index) {
            if (index >= parts.length) {
                return 0;
            }
            try {
                return Integer.parseInt(parts[index]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public int compareTo(Version other) {
            if (major != other.major) {
                return Integer.compare(major, other.major);
            }
            if (minor != other.minor) {
                return Integer.compare(minor, other.minor);
            }
            if (patch != other.patch) {
                return Integer.compare(patch, other.patch);
            }
            return Integer.compare(build, other.build);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch + (build > 0 ? "." + build : "");
        }
    }
}
