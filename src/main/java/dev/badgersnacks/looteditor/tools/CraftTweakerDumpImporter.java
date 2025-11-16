package dev.badgersnacks.looteditor.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts CraftTweaker's {@code /ct dump_brackets lootTables} output into a JSON manifest that
 * loot-editor-b (and other tools) can consume. The command expects a modpack root so it can locate
 * {@code ct_dumps/lootTables.txt} plus {@code crafttweaker.log} metadata.
 */
public final class CraftTweakerDumpImporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CraftTweakerDumpImporter() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("""
                    Usage: CraftTweakerDumpImporter <packRoot> <outputFile> [--dump <customDumpPath>] [--log <craftTweakerLog>]

                    <packRoot>   Root of the modpack instance that contains ct_dumps/ and crafttweaker.log.
                    <outputFile> Destination JSON file (directories are created automatically).
                    """);
            System.exit(1);
        }

        Path packRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputFile = Paths.get(args[1]).toAbsolutePath().normalize();
        Path dumpFile = packRoot.resolve("ct_dumps").resolve("lootTables.txt");
        Path logFile = packRoot.resolve("logs").resolve("crafttweaker.log");

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--dump" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--dump requires a path argument");
                    }
                    dumpFile = Paths.get(args[++i]).toAbsolutePath().normalize();
                }
                case "--log" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--log requires a path argument");
                    }
                    logFile = Paths.get(args[++i]).toAbsolutePath().normalize();
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        List<String> ids;
        if (Files.isRegularFile(dumpFile)) {
            ids = readDumpFile(dumpFile);
        } else if (Files.isRegularFile(logFile)) {
            ids = readFromLog(logFile);
        } else {
            throw new IOException("Missing CraftTweaker dump. Run /ct dump loottables in a test world.");
        }

        ObjectNode root = MAPPER.createObjectNode();
        root.put("source", "crafttweaker");
        root.put("generated", Instant.now().toString());
        root.put("packRoot", packRoot.toString());
        root.put("inputDump", dumpFile.toString());
        root.put("entries", ids.size());

        if (Files.exists(logFile)) {
            root.put("craftTweakerLog", logFile.toString());
            root.put("craftTweakerLogTimestamp", Files.getLastModifiedTime(logFile).toInstant().toString());
        }

        ArrayNode tablesNode = root.putArray("tables");
        ids.forEach(tablesNode::add);

        Files.createDirectories(outputFile.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), root);

        System.out.printf("CraftTweaker manifest written to %s (%d tables)%n", outputFile, ids.size());
    }

    private static List<String> readDumpFile(Path dumpFile) throws IOException {
        List<String> lines = Files.readAllLines(dumpFile, StandardCharsets.UTF_8);
        Set<String> ids = lines.stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new IOException("CraftTweaker dump file was empty: " + dumpFile);
        }
        return List.copyOf(ids);
    }

    private static List<String> readFromLog(Path logFile) throws IOException {
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        String marker = "Loot Tables list generated";
        int markerIndex = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).contains(marker)) {
                markerIndex = i;
                break;
            }
        }
        if (markerIndex == -1) {
            throw new IOException("No loot table dump found inside log: " + logFile);
        }

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (int i = markerIndex - 1; i >= 0; i--) {
            String line = lines.get(i);
            int idx = line.indexOf("CraftTweaker-Commands]:");
            if (idx == -1) {
                break;
            }
            String id = line.substring(idx + "CraftTweaker-Commands]:".length()).trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            throw new IOException("Found loot table marker but no entries inside log: " + logFile);
        }
        List<String> ordered = ids.stream().collect(Collectors.toList());
        java.util.Collections.reverse(ordered); // log was read backwards
        return ordered;
    }
}
