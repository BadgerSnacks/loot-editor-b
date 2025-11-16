package dev.badgersnacks.looteditor.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of all items/blocks discovered in the modpack.
 */
public final class ItemCatalog {

    private final List<ItemDescriptor> descriptors;
    private final Map<String, ItemDescriptor> byId;
    private final List<String> namespaces;

    public ItemCatalog(List<ItemDescriptor> descriptors) {
        this.descriptors = List.copyOf(descriptors);
        Map<String, ItemDescriptor> temp = new LinkedHashMap<>();
        for (ItemDescriptor descriptor : descriptors) {
            temp.put(descriptor.qualifiedId(), descriptor);
        }
        this.byId = Collections.unmodifiableMap(temp);
        this.namespaces = this.descriptors.stream()
                .map(ItemDescriptor::namespace)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<ItemDescriptor> descriptors() {
        return descriptors;
    }

    public List<String> namespaces() {
        return namespaces;
    }

    public Optional<ItemDescriptor> find(String qualifiedId) {
        return Optional.ofNullable(byId.get(qualifiedId));
    }

    public byte[] iconDataFor(String qualifiedId) {
        ItemDescriptor descriptor = byId.get(qualifiedId);
        return descriptor == null ? null : descriptor.iconData();
    }
}
