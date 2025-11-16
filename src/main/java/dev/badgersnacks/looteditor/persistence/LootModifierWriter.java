package dev.badgersnacks.looteditor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.badgersnacks.looteditor.persistence.OverrideManifest.OverrideEntry;
import dev.badgersnacks.looteditor.util.LootId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Emits the data files required for NeoForge Global Loot Modifiers that perform the runtime
 * replacement of vanilla loot tables.
 */
public final class LootModifierWriter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final OverridePaths overridePaths = new OverridePaths();

    public void writeModifier(Path packRoot, LootId target, LootId replacement)
            throws IOException {
        Path file = overridePaths.modifierFile(packRoot, target);
        Files.createDirectories(file.getParent());

        ObjectNode root = mapper.createObjectNode();
        root.put("type", "loot_editor_loader:replace_table");

        ArrayNode conditions = root.putArray("conditions");
        ObjectNode condition = conditions.addObject();
        condition.put("condition", "neoforge:loot_table_id");
        condition.put("loot_table", target.asString());

        root.put("replacement", replacement.asString());
        mapper.writeValue(file.toFile(), root);
    }

    public void writeGlobalList(Path packRoot, OverrideManifest manifest) throws IOException {
        Path file = overridePaths.globalModifiersFile(packRoot);
        Files.createDirectories(file.getParent());

        ObjectNode root = mapper.createObjectNode();
        root.put("replace", false);
        ArrayNode entries = root.putArray("entries");
        manifest.overrides().stream()
                .map(OverrideEntry::targetId)
                .sorted(Comparator.comparing(LootId::asString))
                .map(overridePaths::modifierId)
                .forEach(id -> entries.add(id.asString()));
        mapper.writeValue(file.toFile(), root);
    }
}
