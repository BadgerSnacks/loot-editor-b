package dev.badgersnacks.looteditor.agents;

import dev.badgersnacks.looteditor.model.LootTableDescriptor;
import dev.badgersnacks.looteditor.scanner.ModpackScanner;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Agent task that crawls a modpack folder and returns every loot table descriptor it sees.
 */
public class ScannerAgentTask implements AgentTask<List<LootTableDescriptor>> {

    private final Path modpackRoot;
    private final ModpackScanner scanner;

    public ScannerAgentTask(Path modpackRoot, ModpackScanner scanner) {
        this.modpackRoot = Objects.requireNonNull(modpackRoot, "modpackRoot");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
    }

    @Override
    public String name() {
        return "modpack-scan";
    }

    @Override
    public List<LootTableDescriptor> run() throws Exception {
        return scanner.scan(modpackRoot);
    }
}
