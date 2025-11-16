package dev.badgersnacks.looteditor.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.badgersnacks.looteditor.model.LootTableDescriptor;
import dev.badgersnacks.looteditor.model.LootTableDescriptor.SourceType;
import dev.badgersnacks.looteditor.persistence.ExportSettings;
import dev.badgersnacks.looteditor.persistence.OverrideManifest;
import dev.badgersnacks.looteditor.persistence.OverrideManifest.OverrideEntry;
import dev.badgersnacks.looteditor.persistence.OverrideManifestService;
import dev.badgersnacks.looteditor.persistence.OverridePaths;
import dev.badgersnacks.looteditor.util.LootId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Walks the modpack folder and discovers every loot table it can find.
 */
public class ModpackScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModpackScanner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] LOOT_DIRECTORY_NAMES = {"loot_tables", "loot_table"};
    private final ExportSettings exportSettings = new ExportSettings();
    private final OverrideManifestService overrideManifestService = new OverrideManifestService();
    private final OverridePaths overridePaths = new OverridePaths();

    public List<LootTableDescriptor> scan(Path modpackRoot) throws IOException {
        Objects.requireNonNull(modpackRoot, "modpackRoot");
        if (!Files.isDirectory(modpackRoot)) {
            throw new IOException("Modpack root " + modpackRoot + " is not a directory");
        }

        List<LootTableDescriptor> descriptors = new ArrayList<>();
        Path datapacks = modpackRoot.resolve("datapacks");
        scanDatapacks(datapacks, "Datapack: ", SourceType.DATAPACK, descriptors);
        scanExportOverrides(modpackRoot, descriptors);

        Path savesDir = modpackRoot.resolve("saves");
        scanWorldDatapacks(savesDir, descriptors);

        Path kubeJsData = modpackRoot.resolve("kubejs").resolve("data");
        scanDataDirectory(kubeJsData, "KubeJS", SourceType.KUBEJS, true, descriptors);

        Path lootDumpData = kubeJsData.resolve("_loot_dump");
        if (Files.isDirectory(lootDumpData)) {
            scanDataDirectory(lootDumpData, "Loot Dump", SourceType.LOOT_DUMP, false, descriptors);
        }

        Path modsDir = modpackRoot.resolve("mods");
        scanModArchives(modsDir, descriptors);

        Path vanillaJar = locateMinecraftJar(modpackRoot);
        if (vanillaJar != null) {
            scanJar(vanillaJar, SourceType.VANILLA, "Minecraft", false, descriptors);
        }

        return descriptors.stream()
                .sorted(Comparator.comparing(LootTableDescriptor::sourceType)
                        .thenComparing(LootTableDescriptor::namespace)
                        .thenComparing(LootTableDescriptor::tablePath))
                .collect(Collectors.toUnmodifiableList());
    }

    private void scanExportOverrides(Path modpackRoot, List<LootTableDescriptor> sink) {
        exportSettings.resolvePackRoot(modpackRoot).ifPresent(exportRoot -> {
            Path dataDir = exportRoot.resolve("data");
            if (Files.isDirectory(dataDir)) {
                LOGGER.info("Scanning Loot Editor export datapack at {}", exportRoot);
                int startIndex = sink.size();
                scanDataDirectory(dataDir, "Loot Editor Export", SourceType.DATAPACK, true, sink);
                ListIterator<LootTableDescriptor> iterator = sink.listIterator(startIndex);
                while (iterator.hasNext()) {
                    LootTableDescriptor descriptor = iterator.next();
                    if (overridePaths.isReplacementPath(exportRoot, descriptor.containerPath())) {
                        iterator.remove();
                    }
                }
            }

            OverrideManifest manifest = overrideManifestService.load(exportRoot);
            for (OverrideEntry entry : manifest.overrides()) {
                LootId target = entry.targetId();
                Path replacementFile = overridePaths.replacementFile(exportRoot, target);
                if (!Files.isRegularFile(replacementFile)) {
                    LOGGER.warn("Override {} points to missing file {}", target, replacementFile);
                    continue;
                }
                sink.add(new LootTableDescriptor(
                        target.namespace(),
                        target.path(),
                        replacementFile,
                        null,
                        "Loot Editor Override",
                        SourceType.DATAPACK,
                        true));
            }
        });
    }

    private void scanDatapacks(Path datapacksDir,
                               String labelPrefix,
                               SourceType sourceType,
                               List<LootTableDescriptor> sink) {
        if (!Files.isDirectory(datapacksDir)) {
            return;
        }
        try (Stream<Path> packs = Files.list(datapacksDir)) {
            packs.forEach(pack -> {
                if (Files.isDirectory(pack)) {
                    Path dataDir = pack.resolve("data");
                    String label = (labelPrefix == null ? "" : labelPrefix) + pack.getFileName();
                    scanDataDirectory(dataDir, label, sourceType, true, sink);
                } else if (pack.toString().endsWith(".zip")) {
                    String label = (labelPrefix == null ? "" : labelPrefix) + pack.getFileName();
                    scanDatapackArchive(pack, label, sourceType, false, sink);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to list datapacks in {}", datapacksDir, e);
        }
    }

    private void scanDatapackArchive(Path archive,
                                     String label,
                                     SourceType sourceType,
                                     boolean editable,
                                     List<LootTableDescriptor> sink) {
        scanArchiveData(archive, label, sourceType, editable, sink);
    }

    private void scanWorldDatapacks(Path savesDir, List<LootTableDescriptor> sink) {
        if (!Files.isDirectory(savesDir)) {
            return;
        }
        try (Stream<Path> worlds = Files.list(savesDir)) {
            worlds.filter(Files::isDirectory).forEach(world -> {
                Path datapacksDir = world.resolve("datapacks");
                if (Files.isDirectory(datapacksDir)) {
                    String prefix = "World " + world.getFileName() + ": ";
                    scanDatapacks(datapacksDir, prefix, SourceType.DATAPACK, sink);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to enumerate worlds in {}", savesDir, e);
        }
    }

    private void scanDataDirectory(Path dataDir,
                                   String label,
                                   SourceType sourceType,
                                   boolean editable,
                                   List<LootTableDescriptor> sink) {
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(dataDir)) {
            namespaces.filter(Files::isDirectory).forEach(namespaceDir -> {
                String namespace = namespaceDir.getFileName().toString();
                lootDirectories(namespaceDir).forEach(lootDir -> {
                    try (Stream<Path> tables = Files.walk(lootDir)) {
                        tables.filter(path -> path.toString().endsWith(".json"))
                                .forEach(tablePath -> sink.add(descriptorFromPath(namespace, lootDir, tablePath,
                                        label, sourceType, editable)));
                    } catch (IOException e) {
                        LOGGER.warn("Failed to walk loot tables under {}", lootDir, e);
                    }
                });
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to read namespaces under {}", dataDir, e);
        }
    }

    private LootTableDescriptor descriptorFromPath(String namespace,
                                                   Path lootTablesRoot,
                                                   Path file,
                                                   String label,
                                                   SourceType sourceType,
                                                   boolean editable) {
        Path relative = lootTablesRoot.relativize(file);
        String tableId = relative.toString()
                .replace('\\', '/')
                .replace(".json", "");
        return new LootTableDescriptor(namespace, tableId, file, null, label, sourceType, editable);
    }

    private void scanModArchives(Path modsDir, List<LootTableDescriptor> sink) {
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        try (DirectoryStream<Path> jarFiles = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : jarFiles) {
                scanJar(jar, SourceType.MOD_JAR, "Mod Jar: " + jar.getFileName(), false, sink);
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to enumerate mod jars in {}", modsDir, e);
        }
    }

    private void scanJar(Path jarPath,
                         SourceType sourceType,
                         String label,
                         boolean editable,
                         List<LootTableDescriptor> sink) {
        try (FileSystem zipFs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            scanArchiveData(zipFs, jarPath, "data", label, sourceType, editable, sink);
            scanEmbeddedDatapacks(zipFs, jarPath, label, sink);
        } catch (IOException e) {
            LOGGER.debug("Skipping archive {} due to error: {}", jarPath, e.getMessage());
        }
    }

    private void scanArchiveData(Path archivePath,
                                 String label,
                                 SourceType sourceType,
                                 boolean editable,
                                 List<LootTableDescriptor> sink) {
        try (FileSystem zipFs = FileSystems.newFileSystem(archivePath, (ClassLoader) null)) {
            scanArchiveData(zipFs, archivePath, "data", label, sourceType, editable, sink);
        } catch (IOException e) {
            LOGGER.debug("Skipping archive {} due to error: {}", archivePath, e.getMessage());
        }
    }

    private void scanArchiveData(FileSystem zipFs,
                                 Path sourcePath,
                                 String dataRootName,
                                 String label,
                                 SourceType sourceType,
                                 boolean editable,
                                 List<LootTableDescriptor> sink) throws IOException {
        Path dataRoot = zipFs.getPath(dataRootName);
        if (!Files.exists(dataRoot)) {
            dataRoot = zipFs.getPath("/", dataRootName);
            if (!Files.exists(dataRoot)) {
                return;
            }
        }
        try (Stream<Path> namespaces = Files.list(dataRoot)) {
            namespaces.filter(Files::isDirectory).forEach(namespaceDir -> {
                String namespace = namespaceDir.getFileName().toString();
                lootDirectories(namespaceDir).forEach(lootDir -> {
                    String lootDirName = lootDir.getFileName().toString();
                    try (Stream<Path> tables = Files.walk(lootDir)) {
                        tables.filter(path -> path.toString().endsWith(".json"))
                                .forEach(tablePath -> {
                                    Path relative = lootDir.relativize(tablePath);
                                    String relativePath = relative.toString().replace('\\', '/');
                                    String tableId = relativePath.replace(".json", "");
                                    String archiveEntry = dataRootName + "/" + namespace + "/" + lootDirName + "/" + relativePath;
                                    sink.add(new LootTableDescriptor(
                                            namespace,
                                            tableId,
                                            editable ? tablePath : sourcePath,
                                            editable ? null : archiveEntry,
                                            label,
                                            sourceType,
                                            editable));
                                });
                    } catch (IOException e) {
                        LOGGER.warn("Failed to read loot tables under {} for {}", lootDir, sourcePath, e);
                    }
                });
            });
        }
    }

    private List<Path> lootDirectories(Path namespaceDir) {
        List<Path> lootDirs = new ArrayList<>();
        for (String dirName : LOOT_DIRECTORY_NAMES) {
            Path candidate = namespaceDir.resolve(dirName);
            if (Files.isDirectory(candidate)) {
                lootDirs.add(candidate);
            }
        }
        return lootDirs;
    }

    private void scanEmbeddedDatapacks(FileSystem zipFs,
                                       Path jarPath,
                                       String label,
                                       List<LootTableDescriptor> sink) {
        for (Path root : zipFs.getRootDirectories()) {
            try (Stream<Path> children = Files.list(root)) {
                children.filter(Files::isDirectory).forEach(entry -> {
                    Path packMeta = entry.resolve("pack.mcmeta");
                    Path dataDir = entry.resolve("data");
                    if (Files.exists(packMeta) && Files.isDirectory(dataDir)) {
                        String entryLabel = label + " (Datapack " + entry.getFileName() + ")";
                        scanDataDirectory(dataDir, entryLabel, SourceType.DATAPACK, false, sink);
                    }
                });
            } catch (IOException e) {
                LOGGER.debug("Unable to inspect embedded datapacks in {}", jarPath, e);
            }
        }
    }

    private Path locateMinecraftJar(Path modpackRoot) {
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
            if (Files.exists(jar)) {
                return jar;
            }
        } catch (IOException e) {
            LOGGER.debug("Unable to resolve minecraft jar for {}", modpackRoot, e);
        }
        return null;
    }

    private Path resolveInstallDir(Path modpackRoot) {
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
}
