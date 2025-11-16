package dev.badgersnacks.looteditor.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.badgersnacks.looteditor.catalog.ItemDescriptor.ItemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Scans the modpack for item/block models plus their referenced textures to build an icon catalog.
 */
public class ItemCatalogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemCatalogService.class);
    private static final TypeReference<Map<String, String>> LANG_REF = new TypeReference<>() {};

    private final ObjectMapper mapper = new ObjectMapper();

    public ItemCatalog buildCatalog(Path modpackRoot) throws IOException {
        Objects.requireNonNull(modpackRoot, "modpackRoot");
        Map<String, ItemDescriptor> descriptors = new LinkedHashMap<>();

        scanMinecraftAssets(modpackRoot, descriptors);
        scanModJars(modpackRoot.resolve("mods"), descriptors);
        scanAssetDirectory(modpackRoot.resolve("kubejs").resolve("assets"), "KubeJS Assets", descriptors);
        scanResourcePacks(modpackRoot.resolve("resourcepacks"), descriptors);

        return new ItemCatalog(new ArrayList<>(descriptors.values()));
    }

    private void scanResourcePacks(Path packsDir, Map<String, ItemDescriptor> sink) {
        if (!Files.isDirectory(packsDir)) {
            return;
        }
        try (Stream<Path> packs = Files.list(packsDir)) {
            packs.forEach(pack -> {
                if (Files.isDirectory(pack)) {
                    Path assets = pack.resolve("assets");
                    if (Files.isDirectory(assets)) {
                        scanAssetDirectory(assets, "Resource Pack: " + pack.getFileName(), sink);
                    }
                } else if (pack.toString().endsWith(".zip")) {
                    scanResourcePackArchive(pack, sink);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Unable to enumerate resource packs in {}", packsDir, e);
        }
    }

    private void scanResourcePackArchive(Path archive, Map<String, ItemDescriptor> sink) {
        try (FileSystem zipFs = FileSystems.newFileSystem(archive, (ClassLoader) null)) {
            Path assets = zipFs.getPath("assets");
            if (Files.exists(assets)) {
                scanAssetDirectory(assets, "Resource Pack Zip: " + archive.getFileName(), sink);
            }
        } catch (IOException e) {
            LOGGER.debug("Unable to read resource pack zip {}", archive, e);
        }
    }

    private void scanAssetDirectory(Path assetsRoot, String label, Map<String, ItemDescriptor> sink) {
        if (!Files.isDirectory(assetsRoot)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(assetsRoot)) {
            namespaces.filter(Files::isDirectory).forEach(namespaceDir -> processNamespace(namespaceDir, assetsRoot, label, sink));
        } catch (IOException e) {
            LOGGER.warn("Failed to scan assets root {}", assetsRoot, e);
        }
    }

    private void processNamespace(Path namespaceDir, Path assetsRoot, String label, Map<String, ItemDescriptor> sink) {
        String namespace = namespaceDir.getFileName().toString();
        Map<String, String> lang = loadLang(namespaceDir);
        Path models = namespaceDir.resolve("models");
        if (!Files.isDirectory(models)) {
            return;
        }
        handleModelDir(namespace, models.resolve("item"), ItemType.ITEM, label, lang, assetsRoot, sink);
        handleModelDir(namespace, models.resolve("block"), ItemType.BLOCK, label, lang, assetsRoot, sink);
    }

    private Map<String, String> loadLang(Path namespaceDir) {
        Path langFile = namespaceDir.resolve("lang").resolve("en_us.json");
        if (!Files.isRegularFile(langFile)) {
            return Collections.emptyMap();
        }
        try (InputStream in = Files.newInputStream(langFile)) {
            return mapper.readValue(in, LANG_REF);
        } catch (IOException e) {
            LOGGER.debug("Unable to parse lang file {}", langFile, e);
            return Collections.emptyMap();
        }
    }

    private void handleModelDir(String namespace,
                                Path modelDir,
                                ItemType type,
                                String label,
                                Map<String, String> lang,
                                Path assetsRoot,
                                Map<String, ItemDescriptor> sink) {
        if (!Files.isDirectory(modelDir)) {
            return;
        }
        try (Stream<Path> models = Files.walk(modelDir)) {
            models.filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> !isAnimationFrameModel(path))
                    .forEach(modelPath -> {
                        String relative = modelDir.relativize(modelPath).toString().replace('\\', '/');
                        String itemId = relative.substring(0, relative.length() - ".json".length());
                        try {
                            createDescriptor(namespace, itemId, type, label, lang, assetsRoot, modelPath)
                                    .ifPresent(descriptor -> sink.put(descriptor.qualifiedId(), descriptor));
                        } catch (IOException e) {
                            LOGGER.debug("Failed parsing model {}", modelPath, e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Unable to walk models under {}", modelDir, e);
        }
    }

    private boolean isAnimationFrameModel(Path modelPath) {
        String fileName = modelPath.getFileName().toString();
        // Skip clock animation frames (clock_00.json through clock_63.json)
        if (fileName.matches("clock_\\d{2}\\.json")) {
            return true;
        }
        // Skip compass animation frames (compass_00.json through compass_31.json)
        if (fileName.matches("compass_\\d{2}\\.json")) {
            return true;
        }
        // Skip recovery compass animation frames (recovery_compass_00.json through recovery_compass_31.json)
        if (fileName.matches("recovery_compass_\\d{2}\\.json")) {
            return true;
        }
        return false;
    }

    private Optional<ItemDescriptor> createDescriptor(String namespace,
                                                      String itemId,
                                                      ItemType type,
                                                      String label,
                                                      Map<String, String> lang,
                                                      Path assetsRoot,
                                                      Path modelPath) throws IOException {
        JsonNode model;
        try (InputStream in = Files.newInputStream(modelPath)) {
            model = mapper.readTree(in);
        }
        String textureRef = resolvePrimaryTexture(assetsRoot, namespace, model);
        byte[] iconData = loadTextureBytes(assetsRoot, namespace, textureRef);
        if ((iconData == null || iconData.length == 0) && textureRef != null) {
            LOGGER.debug("Missing texture bytes for {}:{} ({})", namespace, itemId, textureRef);
        }
        String displayName = resolveDisplayName(namespace, itemId, type, lang);
        return Optional.of(new ItemDescriptor(namespace, itemId, type, displayName, iconData, label));
    }

    private String resolveDisplayName(String namespace,
                                      String itemId,
                                      ItemType type,
                                      Map<String, String> lang) {
        String keyPrefix = type == ItemType.BLOCK ? "block." : "item.";
        String translationKey = keyPrefix + namespace + "." + itemId.replace('/', '.');
        return lang.getOrDefault(translationKey, namespace + ":" + itemId);
    }

    private String resolvePrimaryTexture(Path assetsRoot, String namespace, JsonNode model) throws IOException {
        LinkedHashMap<String, String> textures = new LinkedHashMap<>();
        resolveTexturesRecursive(assetsRoot, namespace, model, new java.util.HashSet<>(), textures);
        String layer0 = resolveTextureAlias(textures, "layer0");
        if (layer0 != null) {
            return layer0;
        }
        for (String value : textures.values()) {
            String resolved = resolveAliasChain(textures, value);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private void resolveTexturesRecursive(Path assetsRoot,
                                          String namespace,
                                          JsonNode model,
                                          java.util.Set<ModelAddress> visited,
                                          LinkedHashMap<String, String> textures) throws IOException {
        JsonNode textureNode = model.path("textures");
        if (textureNode.isObject()) {
            textureNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    textures.putIfAbsent(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        String parentRef = model.path("parent").asText(null);
        if (parentRef == null || parentRef.startsWith("builtin/")) {
            return;
        }
        ModelAddress parent = ModelAddress.from(parentRef, namespace);
        if (!visited.add(parent)) {
            return;
        }
        JsonNode parentNode = loadModelNode(assetsRoot, parent);
        if (parentNode != null) {
            resolveTexturesRecursive(assetsRoot, parent.namespace(), parentNode, visited, textures);
        }
    }

    private JsonNode loadModelNode(Path assetsRoot, ModelAddress address) throws IOException {
        Path modelPath = assetsRoot.resolve(address.namespace())
                .resolve("models")
                .resolve(address.path() + ".json");
        if (!Files.isRegularFile(modelPath)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(modelPath)) {
            return mapper.readTree(in);
        }
    }

    private String resolveTextureAlias(LinkedHashMap<String, String> textures, String key) {
        if (!textures.containsKey(key)) {
            return null;
        }
        return resolveAliasChain(textures, textures.get(key));
    }

    private String resolveAliasChain(LinkedHashMap<String, String> textures, String value) {
        String current = value;
        int guard = 0;
        while (current != null && current.startsWith("#") && guard++ < 10) {
            current = textures.get(current.substring(1));
        }
        return current;
    }

    private byte[] loadTextureBytes(Path assetsRoot, String defaultNamespace, String textureRef) {
        if (textureRef == null || textureRef.isBlank()) {
            return null;
        }
        TextureAddress address = TextureAddress.fromReference(defaultNamespace, textureRef);
        Path texturePath = assetsRoot.resolve(address.namespace())
                .resolve("textures")
                .resolve(address.relativePath() + ".png");
        if (!Files.isRegularFile(texturePath)) {
            return null;
        }
        try {
            return Files.readAllBytes(texturePath);
        } catch (IOException e) {
            LOGGER.debug("Unable to read texture {}", texturePath, e);
            return null;
        }
    }

    private void scanModJars(Path modsDir, Map<String, ItemDescriptor> sink) {
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : jars) {
                try (FileSystem zipFs = FileSystems.newFileSystem(jar, (ClassLoader) null)) {
                    Path assetsRoot = zipFs.getPath("assets");
                    if (Files.exists(assetsRoot)) {
                        scanAssetDirectory(assetsRoot, "Mod Jar: " + jar.getFileName(), sink);
                    }
                } catch (IOException e) {
                    LOGGER.debug("Skipping jar {} due to {}", jar, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to read mods folder {}", modsDir, e);
        }
    }

    private void scanMinecraftAssets(Path modpackRoot, Map<String, ItemDescriptor> sink) {
        locateMinecraftJar(modpackRoot).ifPresent(jar -> {
            try (FileSystem zipFs = FileSystems.newFileSystem(jar, (ClassLoader) null)) {
                Path assetsRoot = zipFs.getPath("assets");
                if (Files.exists(assetsRoot)) {
                    scanAssetDirectory(assetsRoot, "Minecraft", sink);
                }
            } catch (IOException e) {
                LOGGER.debug("Unable to scan Minecraft assets inside {}", jar, e);
            }
        });
    }

    private record TextureAddress(String namespace, String relativePath) {
        static TextureAddress fromReference(String fallbackNamespace, String reference) {
            String ns = fallbackNamespace;
            String path = reference;
            if (reference.contains(":")) {
                String[] parts = reference.split(":", 2);
                ns = parts[0];
                path = parts[1];
            }
            return new TextureAddress(ns, path);
        }
    }

    private record ModelAddress(String namespace, String path) {
        static ModelAddress from(String reference, String fallbackNamespace) {
            String ns = fallbackNamespace;
            String modelPath = reference;
            if (reference.contains(":")) {
                String[] parts = reference.split(":", 2);
                ns = parts[0];
                modelPath = parts[1];
            }
            return new ModelAddress(ns, modelPath);
        }
    }

    private java.util.Optional<Path> locateMinecraftJar(Path modpackRoot) {
        try {
            Path instanceJson = modpackRoot.resolve("minecraftinstance.json");
            if (!Files.exists(instanceJson)) {
                return java.util.Optional.empty();
            }
            JsonNode root = mapper.readTree(instanceJson.toFile());
            String inherits = null;
            JsonNode baseLoader = root.path("baseModLoader");
            if (baseLoader.hasNonNull("versionJson")) {
                String versionJson = baseLoader.path("versionJson").asText("");
                if (!versionJson.isBlank()) {
                    JsonNode parsed = mapper.readTree(versionJson);
                    inherits = parsed.path("inheritsFrom").asText(null);
                }
            }
            if (inherits == null || inherits.isBlank()) {
                inherits = root.path("gameVersion").asText(null);
            }
            if (inherits == null || inherits.isBlank()) {
                return java.util.Optional.empty();
            }
            Path installDir = resolveInstallDir(modpackRoot);
            if (installDir == null) {
                return java.util.Optional.empty();
            }
            Path jar = installDir.resolve("versions").resolve(inherits).resolve(inherits + ".jar");
            if (Files.exists(jar)) {
                return java.util.Optional.of(jar);
            }
        } catch (IOException e) {
            LOGGER.debug("Unable to locate vanilla jar for {}", modpackRoot, e);
        }
        return java.util.Optional.empty();
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
