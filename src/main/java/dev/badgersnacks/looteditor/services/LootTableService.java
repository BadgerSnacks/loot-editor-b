package dev.badgersnacks.looteditor.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.badgersnacks.looteditor.model.LootPoolEntryModel;
import dev.badgersnacks.looteditor.model.LootTableDescriptor;
import dev.badgersnacks.looteditor.model.LootTableDescriptor.SourceType;
import dev.badgersnacks.looteditor.persistence.LootModifierWriter;
import dev.badgersnacks.looteditor.persistence.OverrideManifest;
import dev.badgersnacks.looteditor.persistence.OverrideManifestService;
import dev.badgersnacks.looteditor.persistence.OverridePaths;
import dev.badgersnacks.looteditor.util.LootId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Central place for loading, parsing, and writing loot table JSON.
 */
public class LootTableService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LootTableService.class);

    private final ObjectMapper mapper;
    private final DataPackService dataPackService;
    private final OverrideManifestService overrideManifestService = new OverrideManifestService();
    private final OverridePaths overridePaths = new OverridePaths();
    private final LootModifierWriter lootModifierWriter = new LootModifierWriter();

    public LootTableService() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.dataPackService = new DataPackService(this.mapper);
    }

    public JsonNode load(LootTableDescriptor descriptor) throws IOException {
        if (descriptor.isArchiveEntry()) {
            return loadFromArchive(descriptor);
        }
        return mapper.readTree(descriptor.containerPath().toFile());
    }

    public LootTableDescriptor saveToPreferredLocation(Path modpackRoot,
                                                       LootTableDescriptor descriptor,
                                                       JsonNode node) throws IOException {
        if (modpackRoot == null) {
            throw new IOException("Modpack root is not set");
        }
        if (descriptor == null) {
            throw new IOException("Descriptor is null");
        }
        Path packRoot = dataPackService.ensurePackRoot(modpackRoot);
        boolean isOverrideFile = overridePaths.isReplacementPath(packRoot, descriptor.containerPath());
        boolean canEditInPlace = descriptor.editable()
                && !descriptor.isArchiveEntry()
                && Files.isRegularFile(descriptor.containerPath())
                && !isOverrideFile;

        Path targetPath;
        SourceType sourceType;
        String sourceLabel;
        boolean editable;
        if (canEditInPlace) {
            writeNode(descriptor.containerPath(), node);
            targetPath = descriptor.containerPath();
            sourceType = descriptor.sourceType();
            sourceLabel = descriptor.sourceDisplay();
            editable = true;
        } else {
            LootId targetId = LootId.of(descriptor.namespace(), descriptor.tablePath());
            LootId replacementId = overridePaths.replacementId(targetId);
            Path overrideFile = overridePaths.replacementFile(packRoot, targetId);
            Files.createDirectories(overrideFile.getParent());
            writeNode(overrideFile, node);

            OverrideManifest manifest = overrideManifestService.load(packRoot).upsert(targetId, replacementId);
            overrideManifestService.save(packRoot, manifest);
            lootModifierWriter.writeModifier(packRoot, targetId, replacementId);
            lootModifierWriter.writeGlobalList(packRoot, manifest);

            targetPath = overrideFile;
            sourceType = SourceType.DATAPACK;
            sourceLabel = "Loot Editor Override";
            editable = true;
        }
        syncWorlds(modpackRoot);
        return new LootTableDescriptor(descriptor.namespace(),
                descriptor.tablePath(),
                targetPath,
                null,
                sourceLabel,
                sourceType,
                editable);
    }

    public LootTableDescriptor createTable(Path modpackRoot,
                                           String namespace,
                                           String tablePath,
                                           LootTableTemplate template) throws IOException {
        if (modpackRoot == null) {
            throw new IOException("Modpack root is not set");
        }
        String normalizedNamespace = normalizeNamespace(namespace);
        String normalizedPath = normalizeTablePath(tablePath);
        Path target = dataPackService.resolveLootTablePath(modpackRoot, normalizedNamespace, normalizedPath);
        if (Files.exists(target)) {
            throw new IOException("Loot table already exists: " + normalizedNamespace + ":" + normalizedPath);
        }
        Files.createDirectories(target.getParent());
        JsonNode templateNode = templateFor(template);
        mapper.writeValue(target.toFile(), templateNode);
        syncWorlds(modpackRoot);
        return new LootTableDescriptor(normalizedNamespace, normalizedPath, target, null,
                "Datapack: " + DataPackService.PACK_FOLDER, SourceType.DATAPACK, true);
    }

    public LootTableDescriptor forkToKubeJs(Path modpackRoot, LootTableDescriptor descriptor) throws IOException {
        if (modpackRoot == null) {
            throw new IOException("Modpack root is not set");
        }
        if (descriptor == null) {
            throw new IOException("Descriptor is null");
        }
        JsonNode data = load(descriptor);
        Path target = resolveKubePath(modpackRoot, descriptor.namespace(), descriptor.tablePath());
        if (Files.exists(target)) {
            throw new IOException("Target table already exists at " + target);
        }
        Files.createDirectories(target.getParent());
        mapper.writeValue(target.toFile(), data);
        return new LootTableDescriptor(descriptor.namespace(), descriptor.tablePath(), target, null,
                "KubeJS Override", SourceType.KUBEJS, true);
    }

    public LootTableDescriptor exportToDatapack(Path modpackRoot, LootTableDescriptor descriptor) throws IOException {
        if (modpackRoot == null) {
            throw new IOException("Modpack root is not set");
        }
        JsonNode data = load(descriptor);
        return saveToPreferredLocation(modpackRoot, descriptor, data);
    }

    public List<LootPoolEntryModel> extractEntries(JsonNode lootTable) {
        if (lootTable == null) {
            return Collections.emptyList();
        }
        List<LootPoolEntryModel> entries = new ArrayList<>();
        JsonNode pools = lootTable.path("pools");
        if (pools.isArray()) {
            for (JsonNode pool : pools) {
                JsonNode jsonEntries = pool.path("entries");
                if (!jsonEntries.isArray()) {
                    continue;
                }
                for (JsonNode entry : jsonEntries) {
                    String type = entry.path("type").asText("minecraft:item");
                    String itemId = entry.path("name").asText(entry.path("id").asText("unknown"));
                    double weight = entry.path("weight").asDouble(1.0d);
                    CountRange range = parseCountRange(entry.path("functions"));
                    entries.add(new LootPoolEntryModel(itemId, weight, type, range.min(), range.max(), null));
                }
            }
        }
        return entries;
    }

    private Path resolveKubePath(Path modpackRoot, String namespace, String tablePath) {
        return modpackRoot.resolve("kubejs")
                .resolve("data")
                .resolve(namespace)
                .resolve("loot_table")
                .resolve(tablePath + ".json");
    }

    private String normalizeNamespace(String namespace) throws IOException {
        if (namespace == null || namespace.isBlank()) {
            throw new IOException("Namespace is required");
        }
        return namespace.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeTablePath(String tablePath) throws IOException {
        if (tablePath == null || tablePath.isBlank()) {
            throw new IOException("Table path is required");
        }
        String normalized = tablePath.trim()
                .replace('\\', '/');
        if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized;
    }

    private JsonNode templateFor(LootTableTemplate template) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", template.lootType());
        ArrayNode pools = mapper.createArrayNode();
        ObjectNode pool = mapper.createObjectNode();
        pool.put("rolls", 1);
        ArrayNode entries = mapper.createArrayNode();
        ObjectNode entry = mapper.createObjectNode();
        entry.put("type", "minecraft:item");
        entry.put("name", template.defaultEntry());
        entry.put("weight", 1);
        entries.add(entry);
        pool.set("entries", entries);
        pools.add(pool);
        root.set("pools", pools);
        return root;
    }

    public JsonNode rebuildTable(JsonNode template, List<LootPoolEntryModel> entries) {
        ObjectNode root;
        if (template != null && template.isObject()) {
            root = ((ObjectNode) template).deepCopy();
        } else {
            root = JsonNodeFactory.instance.objectNode();
        }
        root.put("type", root.path("type").asText("minecraft:generic"));
        ArrayNode pools = JsonNodeFactory.instance.arrayNode();
        ObjectNode pool = JsonNodeFactory.instance.objectNode();
        JsonNode existingRolls = root.path("pools").path(0).path("rolls");
        if (existingRolls.isMissingNode()) {
            pool.put("rolls", 1);
        } else {
            pool.set("rolls", existingRolls.deepCopy());
        }
        ArrayNode newEntries = JsonNodeFactory.instance.arrayNode();
        for (LootPoolEntryModel entry : entries) {
            ObjectNode entryNode = JsonNodeFactory.instance.objectNode();
            entryNode.put("type", entry.entryType());
            entryNode.put("name", entry.itemId());
            entryNode.put("weight", entry.weight());
            if (entry.minCount() != 1 || entry.maxCount() != 1) {
                ArrayNode functions = JsonNodeFactory.instance.arrayNode();
                ObjectNode setCount = JsonNodeFactory.instance.objectNode();
                setCount.put("function", "minecraft:set_count");
                if (entry.minCount() == entry.maxCount()) {
                    setCount.put("count", entry.minCount());
                } else {
                    ObjectNode count = JsonNodeFactory.instance.objectNode();
                    count.put("type", "minecraft:uniform");
                    count.put("min", entry.minCount());
                    count.put("max", entry.maxCount());
                    setCount.set("count", count);
                }
                functions.add(setCount);
                entryNode.set("functions", functions);
            }
            newEntries.add(entryNode);
        }
        pool.set("entries", newEntries);
        pools.add(pool);
        root.set("pools", pools);
        return root;
    }

    public String prettyPrint(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Path> syncWorldDatapacks(Path modpackRoot) throws IOException {
        if (modpackRoot == null) {
            return Collections.emptyList();
        }
        return dataPackService.syncWorldDatapacks(modpackRoot);
    }

    private void writeNode(Path target, JsonNode node) throws IOException {
        if (target == null) {
            throw new IOException("Target path is null");
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writeValue(target.toFile(), node);
    }

    private void syncWorlds(Path modpackRoot) {
        try {
            List<Path> worlds = dataPackService.syncWorldDatapacks(modpackRoot);
            if (!worlds.isEmpty()) {
                LOGGER.info("Propagated loot_editor datapack to worlds: {}", worlds);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to propagate datapack to worlds under {}", modpackRoot, e);
        }
    }

    private JsonNode loadFromArchive(LootTableDescriptor descriptor) throws IOException {
        if (!descriptor.isArchiveEntry()) {
            throw new IOException("Descriptor is not backed by an archive entry: " + descriptor.qualifiedName());
        }
        try (FileSystem zipFs = FileSystems.newFileSystem(descriptor.containerPath(), (ClassLoader) null)) {
            Path entry = zipFs.getPath(descriptor.archiveEntry());
            try (var in = Files.newInputStream(entry)) {
                return mapper.readTree(in);
            }
        }
    }

    private CountRange parseCountRange(JsonNode functions) {
        if (!functions.isArray()) {
            return new CountRange(1, 1);
        }
        for (JsonNode function : functions) {
            if ("minecraft:set_count".equals(function.path("function").asText())) {
                JsonNode count = function.path("count");
                if (count.isNumber()) {
                    int value = Math.max(1, count.asInt());
                    return new CountRange(value, value);
                } else if (count.isObject()) {
                    JsonNode min = count.path("min");
                    JsonNode max = count.path("max");
                    if (min.isNumber() && max.isNumber()) {
                        int minVal = Math.max(1, (int) Math.round(min.asDouble()));
                        int maxVal = Math.max(minVal, (int) Math.round(max.asDouble()));
                        return new CountRange(minVal, maxVal);
                    }
                }
            }
        }
        return new CountRange(1, 1);
    }

    private record CountRange(int min, int max) {
    }

    public enum LootTableTemplate {
        GENERIC_CHEST("minecraft:chest", "minecraft:stone", "Chest / Container"),
        ENTITY_DROPS("minecraft:entity", "minecraft:rotten_flesh", "Entity Drops"),
        BLOCK_DROPS("minecraft:block", "minecraft:stone", "Block Drops");

        private final String lootType;
        private final String defaultEntry;
        private final String label;

        LootTableTemplate(String lootType, String defaultEntry, String label) {
            this.lootType = lootType;
            this.defaultEntry = defaultEntry;
            this.label = label;
        }

        public String lootType() {
            return lootType;
        }

        public String defaultEntry() {
            return defaultEntry;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
