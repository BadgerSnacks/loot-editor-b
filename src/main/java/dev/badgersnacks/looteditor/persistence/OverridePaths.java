package dev.badgersnacks.looteditor.persistence;

import dev.badgersnacks.looteditor.util.LootId;
import java.nio.file.Path;

/**
 * Centralizes the directory layout for Loot Editor override data inside the exported datapack.
 */
public final class OverridePaths {
    private static final String LOOT_EDITOR_NAMESPACE = "loot_editor";
    private static final String LOOT_TABLES_DIR = "loot_table";
    private static final String LOOT_MODIFIERS_DIR = "loot_modifiers";
    private static final String META_DIR = "meta";
    private static final String REPLACEMENTS_DIR = "replacements";

    public LootId replacementId(LootId target) {
        String path = REPLACEMENTS_DIR + "/" + target.namespace() + "/" + target.path();
        return LootId.of(LOOT_EDITOR_NAMESPACE, path);
    }

    public Path replacementFile(Path packRoot, LootId target) {
        Path namespaceRoot = packRoot.resolve("data")
                .resolve(LOOT_EDITOR_NAMESPACE)
                .resolve(LOOT_TABLES_DIR)
                .resolve(REPLACEMENTS_DIR)
                .resolve(target.namespace());
        return resolvePath(namespaceRoot, target.path());
    }

    public LootId modifierId(LootId target) {
        String path = target.namespace() + "/" + target.path();
        return LootId.of(LOOT_EDITOR_NAMESPACE, path);
    }

    public Path modifierFile(Path packRoot, LootId target) {
        Path namespaceRoot = packRoot.resolve("data")
                .resolve(LOOT_EDITOR_NAMESPACE)
                .resolve(LOOT_MODIFIERS_DIR)
                .resolve(target.namespace());
        return resolvePath(namespaceRoot, target.path());
    }

    public Path globalModifiersFile(Path packRoot) {
        return packRoot.resolve("data")
                .resolve(LOOT_EDITOR_NAMESPACE)
                .resolve(LOOT_MODIFIERS_DIR)
                .resolve("global_loot_modifiers.json");
    }

    public Path manifestFile(Path packRoot) {
        return packRoot.resolve("data")
                .resolve(LOOT_EDITOR_NAMESPACE)
                .resolve(META_DIR)
                .resolve("loot_overrides.json");
    }

    public boolean isReplacementPath(Path packRoot, Path candidate) {
        Path replacementRoot = packRoot.resolve("data")
                .resolve(LOOT_EDITOR_NAMESPACE)
                .resolve(LOOT_TABLES_DIR)
                .resolve(REPLACEMENTS_DIR)
                .toAbsolutePath()
                .normalize();
        Path normalized = candidate.toAbsolutePath().normalize();
        return normalized.startsWith(replacementRoot);
    }

    private Path resolvePath(Path root, String relativePath) {
        Path resolved = root;
        Path subPath = Path.of(relativePath);
        Path parent = subPath.getParent();
        if (parent != null) {
            resolved = resolved.resolve(parent);
        }
        return resolved.resolve(subPath.getFileName().toString() + ".json");
    }
}
