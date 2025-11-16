package dev.badgersnacks.looteditor.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.badgersnacks.looteditor.model.LootTableDescriptor;
import dev.badgersnacks.looteditor.scanner.ModpackScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

/**
 * Standalone entry point that runs {@link ModpackScanner} without launching the JavaFX UI and writes
 * the discovered loot tables to a JSON manifest consumable by loot-editor-b or other tooling.
 */
public final class LootTableScanCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LootTableScanCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("""
                    Usage: LootTableScanCli <packRoot> <outputFile>

                    <packRoot>   Root of the modpack instance that should be scanned (must contain mods/, kubejs/, etc.).
                    <outputFile> Destination JSON file (directories are created automatically).
                    """);
            System.exit(1);
        }

        Path packRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputFile = Paths.get(args[1]).toAbsolutePath().normalize();

        ModpackScanner scanner = new ModpackScanner();
        List<LootTableDescriptor> descriptors = scanner.scan(packRoot);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("source", "jar_scan");
        root.put("generated", Instant.now().toString());
        root.put("packRoot", packRoot.toString());
        root.put("entries", descriptors.size());

        ArrayNode tablesNode = root.putArray("tables");
        for (LootTableDescriptor descriptor : descriptors) {
            ObjectNode node = tablesNode.addObject();
            node.put("id", descriptor.qualifiedName());
            node.put("namespace", descriptor.namespace());
            node.put("path", descriptor.tablePath());
            node.put("sourceType", descriptor.sourceType().name());
            node.put("sourceLabel", descriptor.sourceType().label());
            node.put("sourceDisplay", descriptor.sourceDisplay());
            node.put("editable", descriptor.editable());
            node.put("containerPath", descriptor.containerPath().toString());
            if (descriptor.archiveEntry() != null) {
                node.put("archiveEntry", descriptor.archiveEntry());
            }
        }

        Files.createDirectories(outputFile.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), root);

        System.out.printf("Scanned %d loot tables. Manifest written to %s%n", descriptors.size(), outputFile);
        descriptors.stream()
                .collect(java.util.stream.Collectors.groupingBy(LootTableDescriptor::sourceType,
                        java.util.stream.Collectors.counting()))
                .forEach((type, count) -> System.out.printf("  %s: %d%n", type, count));
    }
}
