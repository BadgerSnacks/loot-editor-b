package dev.badgersnacks.looteditor.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Copies loot tables from mod jars (and the vanilla jar) into kubejs/data based on a merged manifest.
 */
public final class ManifestLootImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestLootImporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] LOOT_DIRECTORY_NAMES = {"loot_tables", "loot_table"};

    private ManifestLootImporter() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("""
                    Usage: ManifestLootImporter <packRoot> [--manifest=path] [--patterns=chests/,entities/] [--namespaces=minecraft,modid] [--dry-run]

                    <packRoot>  Root of the modpack instance (contains mods/, kubejs/, etc.)
                    """);
            System.exit(1);
        }

        Path packRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path manifestPath = packRoot.resolve("loot-editor-b/import/loot_tables_merged.json");
        Set<String> patternFilters = defaultPatterns();
        Set<String> namespaceFilters = new HashSet<>();
        boolean dryRun = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--manifest=")) {
                manifestPath = Paths.get(arg.substring("--manifest=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--patterns=")) {
                patternFilters = parseCsv(arg.substring("--patterns=".length()));
            } else if (arg.startsWith("--namespaces=")) {
                namespaceFilters = parseCsv(arg.substring("--namespaces=".length()));
            } else if ("--dry-run".equals(arg)) {
                dryRun = true;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (!Files.exists(manifestPath)) {
            throw new IOException("Manifest not found: " + manifestPath);
        }

        List<ManifestEntry> manifestEntries = loadManifest(manifestPath);
        Path kubeRoot = packRoot.resolve("kubejs").resolve("data");
        final Set<String> namespaceFiltersFinal = namespaceFilters;
        final Set<String> patternFiltersFinal = patternFilters;
        Set<String> targetIds = manifestEntries.stream()
                .filter(entry -> matchesNamespace(entry, namespaceFiltersFinal))
                .filter(entry -> matchesPatterns(entry.path(), patternFiltersFinal))
                .filter(entry -> !Files.exists(resolveKubePath(kubeRoot, entry)))
                .map(ManifestEntry::id)
                .collect(Collectors.toCollection(HashSet::new));

        System.out.printf("Found %d manifest entries matching filters; %d already exist in kubejs/data.%n",
                manifestEntries.size(), manifestEntries.size() - targetIds.size());

        if (targetIds.isEmpty()) {
            System.out.println("Nothing to import.");
            return;
        }

        AtomicInteger copied = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();

        List<Path> jars = listJarFiles(packRoot.resolve("mods"));
        Path vanillaJar = locateMinecraftJar(packRoot);
        if (vanillaJar != null) {
            jars.add(vanillaJar);
        }

        final boolean dryRunFlag = dryRun;
        for (Path jar : jars) {
            try (FileSystem zipFs = FileSystems.newFileSystem(jar, (ClassLoader) null)) {
                Path dataRoot = resolveDataRoot(zipFs);
                if (dataRoot == null) {
                    continue;
                }
                try (Stream<Path> namespaces = Files.list(dataRoot)) {
                    namespaces.filter(Files::isDirectory).forEach(namespaceDir -> {
                        String namespace = namespaceDir.getFileName().toString();
                        lootDirectories(namespaceDir).forEach(lootRoot -> {
                            try (Stream<Path> tables = Files.walk(lootRoot)) {
                                tables.filter(path -> path.toString().endsWith(".json"))
                                        .forEach(path -> {
                                            String rel = lootRoot.relativize(path).toString().replace('\\', '/');
                                            String tablePath = rel.substring(0, rel.length() - ".json".length());
                                            String id = namespace + ":" + tablePath;
                                            if (!targetIds.contains(id)) {
                                                return;
                                            }
                                            try {
                                                Path target = kubeRoot.resolve(namespace)
                                                        .resolve("loot_table")
                                                        .resolve(rel);
                                                if (Files.exists(target)) {
                                                    targetIds.remove(id);
                                                    skipped.incrementAndGet();
                                                    return;
                                                }
                                                if (!dryRunFlag) {
                                                    Files.createDirectories(target.getParent());
                                                    Files.copy(path, target);
                                                }
                                                targetIds.remove(id);
                                                copied.incrementAndGet();
                                                System.out.printf("%s %s -> %s%n", dryRunFlag ? "[dry]" : "[copy]", jar, target);
                                            } catch (IOException e) {
                                                LOGGER.warn("Failed to copy {} from {}", id, jar, e);
                                            }
                                        });
                            } catch (IOException e) {
                                LOGGER.warn("Unable to walk loot tables in {}", lootRoot, e);
                            }
                        });
                    });
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read jar {}", jar, e);
            }
        }

        if (!targetIds.isEmpty()) {
            System.out.printf("Unable to locate %d entries:%n", targetIds.size());
            targetIds.stream().limit(20).forEach(id -> System.out.println("  - " + id));
            if (targetIds.size() > 20) {
                System.out.println("  ...");
            }
        }

        System.out.printf("Import complete. Copied=%d SkippedExisting=%d Missing=%d%n",
                copied.get(), skipped.get(), targetIds.size());
        if (dryRun) {
            System.out.println("Dry-run was enabled; no files were written.");
        }
    }

    private static Path resolveDataRoot(FileSystem zipFs) {
        Path root = zipFs.getPath("data");
        if (Files.exists(root)) {
            return root;
        }
        Path slashRoot = zipFs.getPath("/", "data");
        return Files.exists(slashRoot) ? slashRoot : null;
    }

    private static Set<String> parseCsv(String csv) {
        Set<String> set = new HashSet<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private static Set<String> defaultPatterns() {
        Set<String> defaults = new HashSet<>();
        defaults.add("chests/");
        defaults.add("entities/");
        return defaults;
    }

    private static boolean matchesNamespace(ManifestEntry entry, Set<String> namespaces) {
        return namespaces.isEmpty() || namespaces.contains(entry.namespace());
    }

    private static boolean matchesPatterns(String path, Set<String> patterns) {
        if (patterns.isEmpty()) {
            return true;
        }
        for (String pattern : patterns) {
            if (path.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveKubePath(Path kubeRoot, ManifestEntry entry) {
        return kubeRoot.resolve(entry.namespace())
                .resolve("loot_table")
                .resolve(entry.path() + ".json");
    }

    private static List<Path> lootDirectories(Path namespaceDir) {
        List<Path> lootDirs = new ArrayList<>();
        for (String dirName : LOOT_DIRECTORY_NAMES) {
            Path candidate = namespaceDir.resolve(dirName);
            if (Files.isDirectory(candidate)) {
                lootDirs.add(candidate);
            }
        }
        return lootDirs;
    }

    private static List<Path> listJarFiles(Path modsDir) throws IOException {
        List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            return jars;
        }
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(modsDir, "*.jar")) {
            dir.forEach(jars::add);
        }
        return jars;
    }

    private static List<ManifestEntry> loadManifest(Path manifestPath) throws IOException {
        JsonNode root = MAPPER.readTree(manifestPath.toFile());
        List<ManifestEntry> entries = new ArrayList<>();
        JsonNode tables = root.path("tables");
        if (!tables.isArray()) {
            return entries;
        }
        for (JsonNode table : tables) {
            String id = table.path("id").asText(null);
            String namespace = table.path("namespace").asText(null);
            String path = table.path("path").asText(null);
            if (id == null) {
                continue;
            }
            if (namespace == null || path == null) {
                int idx = id.indexOf(':');
                if (idx >= 0) {
                    namespace = namespace == null ? id.substring(0, idx) : namespace;
                    path = path == null ? id.substring(idx + 1) : path;
                } else {
                    continue;
                }
            }
            entries.add(new ManifestEntry(id, namespace, path));
        }
        return entries;
    }

    private static Path locateMinecraftJar(Path modpackRoot) {
        try {
            Path instanceJson = modpackRoot.resolve("minecraftinstance.json");
            if (!Files.exists(instanceJson)) {
                return null;
            }

            JsonNode root = MAPPER.readTree(instanceJson.toFile());
            String inherits = null;
            JsonNode baseLoader = root.path("baseModLoader");
            if (baseLoader.hasNonNull("versionJson")) {
                String versionJson = baseLoader.path("versionJson").asText("");
                if (!versionJson.isBlank()) {
                    JsonNode parsed = MAPPER.readTree(versionJson);
                    inherits = parsed.path("inheritsFrom").asText(null);
                }
            }
            if (inherits == null || inherits.isBlank()) {
                inherits = root.path("gameVersion").asText(null);
            }
            if (inherits == null || inherits.isBlank()) {
                return null;
            }
            Path installDir = resolveInstallDir(modpackRoot);
            if (installDir == null) {
                return null;
            }
            Path jar = installDir.resolve("versions").resolve(inherits).resolve(inherits + ".jar");
            return Files.exists(jar) ? jar : null;
        } catch (IOException e) {
            LOGGER.debug("Unable to resolve vanilla jar for {}", modpackRoot, e);
            return null;
        }
    }

    private static Path resolveInstallDir(Path modpackRoot) {
        Path instancesDir = modpackRoot.getParent();
        if (instancesDir == null) {
            return null;
        }
        Path minecraftDir = instancesDir.getParent();
        if (minecraftDir == null) {
            return null;
        }
        Path installDir = minecraftDir.resolve("Install");
        return Files.isDirectory(installDir) ? installDir : null;
    }

    private record ManifestEntry(String id, String namespace, String path) {
    }
}
