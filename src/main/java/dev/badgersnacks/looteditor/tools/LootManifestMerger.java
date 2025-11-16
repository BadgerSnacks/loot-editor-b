package dev.badgersnacks.looteditor.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Combines the CraftTweaker dump manifest and the static jar scan manifest into a single merged file
 * so the UI (or other tools) can reason about "present vs missing" loot tables without re-running scans.
 */
public final class LootManifestMerger {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LootManifestMerger() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("""
                    Usage: LootManifestMerger <ctManifest> <scanManifest> <outputFile>

                    <ctManifest>    Path to the CraftTweaker JSON manifest.
                    <scanManifest>  Path to the static scan JSON manifest.
                    <outputFile>    Destination for the merged manifest (directories created automatically).
                    """);
            System.exit(1);
        }

        Path ctManifestPath = Paths.get(args[0]).toAbsolutePath().normalize();
        Path scanManifestPath = Paths.get(args[1]).toAbsolutePath().normalize();
        Path outputPath = Paths.get(args[2]).toAbsolutePath().normalize();

        ObjectNode ctRoot = readManifest(ctManifestPath);
        ObjectNode scanRoot = readManifest(scanManifestPath);

        Map<String, TableAccumulator> merged = new LinkedHashMap<>();

        // ingest scan manifest entries (carry metadata)
        if (scanRoot != null) {
            ArrayNode scanTables = scanRoot.withArray("tables");
            for (JsonNode node : scanTables) {
                String id = node.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                TableAccumulator acc = merged.computeIfAbsent(id, TableAccumulator::new);
                acc.namespace = node.path("namespace").asText(null);
                acc.path = node.path("path").asText(null);
                acc.presentIn.add("jar_scan");

                // Preserve the raw metadata (container path, editable, etc.)
                ObjectNode details = MAPPER.createObjectNode();
                node.fields().forEachRemaining(entry -> details.set(entry.getKey(), entry.getValue()));
                acc.details = details;
            }
        }

        // ingest CraftTweaker manifest entries (presence list only)
        if (ctRoot != null) {
            ArrayNode ctTables = ctRoot.withArray("tables");
            for (JsonNode node : ctTables) {
                String id = node.asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                TableAccumulator acc = merged.computeIfAbsent(id, TableAccumulator::new);
                acc.presentIn.add("crafttweaker");
                if (acc.namespace == null || acc.path == null) {
                    ParsedId parsed = ParsedId.from(id);
                    acc.namespace = parsed.namespace();
                    acc.path = parsed.path();
                }
            }
        }

        ObjectNode mergedRoot = MAPPER.createObjectNode();
        mergedRoot.put("source", "merged");
        mergedRoot.put("generated", Instant.now().toString());
        String packRoot = resolvePackRoot(ctRoot, scanRoot);
        if (packRoot != null && !packRoot.isBlank()) {
            mergedRoot.put("packRoot", packRoot);
        }
        ObjectNode sourcesNode = mergedRoot.putObject("sources");
        sourcesNode.put("crafttweaker", Files.exists(ctManifestPath) ? ctManifestPath.toString() : "missing");
        sourcesNode.put("jar_scan", Files.exists(scanManifestPath) ? scanManifestPath.toString() : "missing");

        ArrayNode tablesNode = mergedRoot.putArray("tables");
        merged.values().stream()
                .sorted(Comparator.comparing(acc -> acc.id))
                .forEach(acc -> {
                    ObjectNode entry = tablesNode.addObject();
                    entry.put("id", acc.id);
                    if (acc.namespace != null) {
                        entry.put("namespace", acc.namespace);
                    }
                    if (acc.path != null) {
                        entry.put("path", acc.path);
                    }
                    ArrayNode presentArray = entry.putArray("presentIn");
                    acc.presentIn.forEach(presentArray::add);
                    if (acc.details != null) {
                        entry.set("details", acc.details);
                    }
                });

        Files.createDirectories(outputPath.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), mergedRoot);
        System.out.printf("Merged %d loot table entries into %s%n", merged.size(), outputPath);
    }

    private static String resolvePackRoot(ObjectNode ctRoot, ObjectNode scanRoot) {
        String ctRootPath = extractPackRoot(ctRoot);
        if (ctRootPath != null && !ctRootPath.isBlank()) {
            return ctRootPath;
        }
        String scanRootPath = extractPackRoot(scanRoot);
        if (scanRootPath != null && !scanRootPath.isBlank()) {
            return scanRootPath;
        }
        return null;
    }

    private static String extractPackRoot(ObjectNode node) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get("packRoot");
        if (value == null || !value.isTextual()) {
            return null;
        }
        return value.asText();
    }

    private static ObjectNode readManifest(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(path.toFile());
            if (root instanceof ObjectNode objectNode) {
                return objectNode;
            }
            throw new IOException("Manifest at " + path + " is not a JSON object");
        } catch (IOException e) {
            throw new IOException("Failed to read manifest " + path, e);
        }
    }

    private static final class TableAccumulator {
        final String id;
        String namespace;
        String path;
        ObjectNode details;
        final Set<String> presentIn = new LinkedHashSet<>();

        TableAccumulator(String id) {
            this.id = id;
            ParsedId parsed = ParsedId.from(id);
            this.namespace = parsed.namespace();
            this.path = parsed.path();
        }
    }

    private record ParsedId(String namespace, String path) {
        static ParsedId from(String id) {
            if (id == null) {
                return new ParsedId(null, null);
            }
            int idx = id.indexOf(':');
            if (idx < 0) {
                return new ParsedId(null, id);
            }
            return new ParsedId(id.substring(0, idx), id.substring(idx + 1));
        }
    }
}
