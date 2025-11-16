package dev.badgersnacks.looteditor.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.badgersnacks.looteditor.model.EnchantmentPool;
import dev.badgersnacks.looteditor.model.EnchantmentPoolEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reads and writes enchantment pool definitions under kubejs/data/<namespace>/enchantment_pools/.
 */
public class EnchantmentPoolService {

    private static final String DEFAULT_NAMESPACE = "loot_editor";
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public List<EnchantmentPool> listPools(Path modpackRoot) throws IOException {
        Path kubeData = kubeDataRoot(modpackRoot);
        if (!Files.isDirectory(kubeData)) {
            return Collections.emptyList();
        }
        List<EnchantmentPool> result = new ArrayList<>();
        Files.walk(kubeData)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> loadPoolFromFile(path).ifPresent(result::add));
        result.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        return result;
    }

    public Optional<EnchantmentPool> findPool(Path modpackRoot, String poolId) {
        if (poolId == null || poolId.isBlank()) {
            return Optional.empty();
        }
        String[] parts = poolId.split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : DEFAULT_NAMESPACE;
        String name = parts.length == 2 ? parts[1] : parts[0];
        Path path = poolFile(modpackRoot, namespace, name);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return loadPoolFromFile(path);
    }

    public EnchantmentPool savePool(Path modpackRoot, EnchantmentPool pool) throws IOException {
        Path path = poolFile(modpackRoot, pool.namespace(), pool.name());
        Files.createDirectories(path.getParent());
        mapper.writeValue(path.toFile(), serialize(pool));
        return pool;
    }

    public void deletePool(Path modpackRoot, String poolId) throws IOException {
        findPool(modpackRoot, poolId).ifPresent(pool -> {
            try {
                Files.deleteIfExists(poolFile(modpackRoot, pool.namespace(), pool.name()));
            } catch (IOException ignored) {
            }
        });
    }

    private Path kubeDataRoot(Path modpackRoot) {
        return modpackRoot.resolve("kubejs").resolve("data");
    }

    private Path poolFile(Path modpackRoot, String namespace, String name) {
        return kubeDataRoot(modpackRoot)
                .resolve(namespace)
                .resolve("enchantment_pools")
                .resolve(name + ".json");
    }

    private Optional<EnchantmentPool> loadPoolFromFile(Path file) {
        try {
            Map<String, Object> raw = mapper.readValue(file.toFile(), new TypeReference<>() {
            });
            String namespace = file.getParent().getParent().getFileName().toString();
            String name = file.getFileName().toString().replace(".json", "");
            String displayName = (String) raw.getOrDefault("display_name", name);
            boolean treasure = Boolean.TRUE.equals(raw.get("treasure_allowed"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entryMaps = (List<Map<String, Object>>) raw.getOrDefault("entries", List.of());
            List<EnchantmentPoolEntry> entries = entryMaps.stream()
                    .map(entryMap -> new EnchantmentPoolEntry(
                            (String) entryMap.getOrDefault("enchantment", "minecraft:unbreaking"),
                            ((Number) entryMap.getOrDefault("weight", 1)).doubleValue(),
                            ((Number) entryMap.getOrDefault("min_level", 1)).intValue(),
                            ((Number) entryMap.getOrDefault("max_level", 1)).intValue()
                    ))
                    .collect(Collectors.toCollection(ArrayList::new));
            EnchantmentPool pool = new EnchantmentPool(namespace, name, displayName, treasure, entries);
            return Optional.of(pool);
        } catch (IOException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private ObjectNode serialize(EnchantmentPool pool) {
        ObjectNode node = mapper.createObjectNode();
        node.put("display_name", pool.displayName());
        node.put("treasure_allowed", pool.treasureAllowed());
        var entries = mapper.createArrayNode();
        for (EnchantmentPoolEntry entry : pool.entries()) {
            ObjectNode entryNode = mapper.createObjectNode();
            entryNode.put("enchantment", entry.enchantmentId());
            entryNode.put("weight", entry.weight());
            entryNode.put("min_level", entry.minLevel());
            entryNode.put("max_level", entry.maxLevel());
            entries.add(entryNode);
        }
        node.set("entries", entries);
        return node;
    }

    public static String defaultNamespace() {
        return DEFAULT_NAMESPACE;
    }

    public static String sanitizeName(String input) {
        if (input == null || input.isBlank()) {
            return "pool";
        }
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
    }
}
