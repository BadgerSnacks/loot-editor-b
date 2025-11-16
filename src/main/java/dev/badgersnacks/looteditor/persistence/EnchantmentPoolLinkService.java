package dev.badgersnacks.looteditor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.badgersnacks.looteditor.model.EnchantmentPoolLink;
import dev.badgersnacks.looteditor.model.EnchantmentPoolLink.LinkedEnchantment;
import dev.badgersnacks.looteditor.model.LootTableDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores metadata so the editor knows which loot entries were generated from enchantment pools.
 */
public class EnchantmentPoolLinkService {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<EnchantmentPoolLink> loadLinks(Path modpackRoot, LootTableDescriptor descriptor) {
        Path file = linkFile(modpackRoot, descriptor);
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        try {
            var root = mapper.readTree(file.toFile());
            ArrayNode linksNode = (ArrayNode) root.path("links");
            List<EnchantmentPoolLink> links = new ArrayList<>();
            for (var node : linksNode) {
                List<LinkedEnchantment> enchantments = new ArrayList<>();
                ArrayNode enchantArray = (ArrayNode) node.path("enchantments");
                enchantArray.forEach(enchantmentNode -> enchantments.add(new LinkedEnchantment(
                        enchantmentNode.path("id").asText(),
                        enchantmentNode.path("weight").asDouble(1.0d),
                        enchantmentNode.path("min_level").asInt(1),
                        enchantmentNode.path("max_level").asInt(1)
                )));
                links.add(new EnchantmentPoolLink(
                        node.path("order").asInt(0),
                        node.path("pool").asText(),
                        node.path("item").asText(),
                        node.path("entry_type").asText("minecraft:item"),
                        node.path("weight").asDouble(1.0d),
                        node.path("min_count").asInt(1),
                        node.path("max_count").asInt(1),
                        enchantments
                ));
            }
            return links;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public void saveLinks(Path modpackRoot, LootTableDescriptor descriptor, List<EnchantmentPoolLink> links) throws IOException {
        Path file = linkFile(modpackRoot, descriptor);
        if (links.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        ObjectNode root = mapper.createObjectNode();
        root.put("table", descriptor.qualifiedName());
        ArrayNode linksNode = mapper.createArrayNode();
        for (EnchantmentPoolLink link : links) {
            ObjectNode node = mapper.createObjectNode();
            node.put("order", link.orderIndex());
            node.put("pool", link.poolId());
            node.put("item", link.itemId());
            node.put("entry_type", link.entryType());
            node.put("weight", link.weight());
            node.put("min_count", link.minCount());
            node.put("max_count", link.maxCount());
            ArrayNode enchantArray = mapper.createArrayNode();
            for (LinkedEnchantment enchantment : link.enchantments()) {
                ObjectNode enchantNode = mapper.createObjectNode();
                enchantNode.put("id", enchantment.enchantmentId());
                enchantNode.put("weight", enchantment.weight());
                enchantNode.put("min_level", enchantment.minLevel());
                enchantNode.put("max_level", enchantment.maxLevel());
                enchantArray.add(enchantNode);
            }
            node.set("enchantments", enchantArray);
            linksNode.add(node);
        }
        root.set("links", linksNode);
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
    }

    private Path linkFile(Path modpackRoot, LootTableDescriptor descriptor) {
        String namespace = descriptor.namespace();
        String tablePath = descriptor.tablePath();
        return modpackRoot.resolve("kubejs")
                .resolve("data")
                .resolve("loot_editor")
                .resolve("pool_links")
                .resolve(namespace)
                .resolve(tablePath + ".json");
    }
}
