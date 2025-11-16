package dev.badgersnacks.looteditor.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.badgersnacks.looteditor.util.LootId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Describes the mapping between target loot table ids and the replacement tables stored inside
 * the exported datapack.
 */
public record OverrideManifest(@JsonProperty("overrides") List<OverrideEntry> overrides) {
    public OverrideManifest {
        if (overrides == null) {
            overrides = List.of();
        } else {
            overrides = List.copyOf(overrides);
        }
    }

    public OverrideManifest upsert(LootId target, LootId replacement) {
        List<OverrideEntry> updated = new ArrayList<>(overrides);
        String targetId = target.asString();
        String replacementId = replacement.asString();
        for (int i = 0; i < updated.size(); i++) {
            OverrideEntry entry = updated.get(i);
            if (entry.target().equals(targetId)) {
                if (!entry.replacement().equals(replacementId)) {
                    updated.set(i, new OverrideEntry(targetId, replacementId));
                }
                return new OverrideManifest(updated);
            }
        }
        updated.add(new OverrideEntry(targetId, replacementId));
        return new OverrideManifest(updated);
    }

    public Optional<LootId> replacementFor(LootId target) {
        String lookup = target.asString();
        return overrides.stream()
                .filter(entry -> entry.target().equals(lookup))
                .findFirst()
                .map(entry -> LootId.parse(entry.replacement()));
    }

    public record OverrideEntry(
            @JsonProperty("target") String target,
            @JsonProperty("replacement") String replacement) {
        public LootId targetId() {
            return LootId.parse(target);
        }

        public LootId replacementId() {
            return LootId.parse(replacement);
        }
    }
}
