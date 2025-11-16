package dev.badgersnacks.looteditor.model;

import java.nio.file.Path;
import java.util.Objects;

public record LootTableDescriptor(
        String namespace,
        String tablePath,
        Path containerPath,
        String archiveEntry,
        String sourceDisplay,
        SourceType sourceType,
        boolean editable
) {

    public LootTableDescriptor {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(tablePath, "tablePath");
        Objects.requireNonNull(containerPath, "containerPath");
        Objects.requireNonNull(sourceDisplay, "sourceDisplay");
        Objects.requireNonNull(sourceType, "sourceType");
    }

    public String qualifiedName() {
        return namespace + ":" + tablePath;
    }

    public boolean isArchiveEntry() {
        return archiveEntry != null && !archiveEntry.isBlank();
    }

    public enum SourceType {
        DATAPACK("Datapack"),
        KUBEJS("KubeJS"),
        MOD_JAR("Mod Jar"),
        LOOT_DUMP("Loot Dump"),
        VANILLA("Minecraft"),
        UNKNOWN("Unknown");

        private final String label;

        SourceType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
