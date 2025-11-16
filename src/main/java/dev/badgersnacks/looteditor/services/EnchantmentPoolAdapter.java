package dev.badgersnacks.looteditor.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.badgersnacks.looteditor.model.EnchantmentPool;
import dev.badgersnacks.looteditor.model.EnchantmentPoolEntry;
import dev.badgersnacks.looteditor.model.EnchantmentPoolLink;
import dev.badgersnacks.looteditor.model.EnchantmentPoolLink.LinkedEnchantment;
import dev.badgersnacks.looteditor.model.LootPoolEntryModel;
import dev.badgersnacks.looteditor.model.LootTableDescriptor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts loot table JSON to/from {@link LootPoolEntryModel} collections, applying enchantment pool metadata.
 */
public final class EnchantmentPoolAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EnchantmentPoolAdapter() {
    }

    /**
     * Collapses the multiple JSON entries generated for a pool back into a single logical row so the editor can
     * present one item with an attached pool instead of dozens of near-duplicates.
     */
    public static List<LootPoolEntryModel> mergeForEditing(JsonNode lootTable, List<EnchantmentPoolLink> links) {
        List<ParsedEntry> parsedEntries = parseEntries(lootTable);
        Map<Integer, ParsedEntry> entriesByOrder = new HashMap<>();
        for (ParsedEntry parsed : parsedEntries) {
            entriesByOrder.put(parsed.orderIndex(), parsed);
        }
        Set<Integer> consumedOrders = new HashSet<>();
        Map<Integer, LootPoolEntryModel> aggregated = new HashMap<>();
        for (EnchantmentPoolLink link : links) {
            List<Integer> matchedOrders = new ArrayList<>();
            for (LinkedEnchantment enchantment : link.enchantments()) {
                Optional<ParsedEntry> match = findMatchingEntry(parsedEntries, link, enchantment, consumedOrders);
                match.ifPresent(parsed -> {
                    consumedOrders.add(parsed.orderIndex());
                    matchedOrders.add(parsed.orderIndex());
                });
            }
            if (!matchedOrders.isEmpty()) {
                LootPoolEntryModel model = new LootPoolEntryModel(
                        link.itemId(),
                        link.weight(),
                        link.entryType(),
                        link.minCount(),
                        link.maxCount(),
                        link.poolId()
                );
                aggregated.put(link.orderIndex(), model);
            }
        }
        List<LootPoolEntryModel> result = new ArrayList<>();
        int cursor = 0;
        int maxOrder = parsedEntries.stream().mapToInt(ParsedEntry::orderIndex).max().orElse(-1);
        while (cursor <= maxOrder) {
            if (aggregated.containsKey(cursor)) {
                result.add(aggregated.remove(cursor));
            }
            ParsedEntry parsed = entriesByOrder.get(cursor);
            if (parsed != null && !consumedOrders.contains(cursor)) {
                result.add(parsed.model());
            }
            cursor++;
        }
        aggregated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(entry.getValue()));
        return result;
    }

    private static Optional<ParsedEntry> findMatchingEntry(List<ParsedEntry> parsedEntries,
                                                           EnchantmentPoolLink link,
                                                           LinkedEnchantment enchantment,
                                                           Set<Integer> consumedOrders) {
        return parsedEntries.stream()
                .filter(parsed -> !consumedOrders.contains(parsed.orderIndex()))
                .filter(parsed -> parsed.model().itemId().equals(link.itemId()))
                .filter(parsed -> parsed.model().entryType().equals(link.entryType()))
                .filter(parsed -> parsed.model().minCount() == link.minCount()
                        && parsed.model().maxCount() == link.maxCount())
                .filter(parsed -> hasSingleEnchantment(parsed.entryNode(), enchantment))
                .findFirst();
    }

    private static boolean hasSingleEnchantment(ObjectNode entryNode, LinkedEnchantment linked) {
        ArrayNode functions = (ArrayNode) entryNode.path("functions");
        if (functions == null) {
            return false;
        }
        for (JsonNode fn : functions) {
            if (!"minecraft:set_enchantments".equals(fn.path("function").asText())) {
                continue;
            }
            JsonNode enchantments = fn.path("enchantments");
            if (!enchantments.isObject()) {
                continue;
            }
            var fields = enchantments.fieldNames();
            if (!fields.hasNext()) {
                continue;
            }
            String enchantmentId = fields.next();
            if (fields.hasNext()) {
                continue; // ignore entries with multiple enchantments
            }
            if (!enchantmentId.equals(linked.enchantmentId())) {
                continue;
            }
            JsonNode value = enchantments.get(enchantmentId);
            if (value.isNumber()) {
                int level = value.asInt();
                return level == linked.minLevel() && level == linked.maxLevel();
            } else if (value.isObject()) {
                int min = value.path("min").asInt(1);
                int max = value.path("max").asInt(min);
                return min == linked.minLevel() && max == linked.maxLevel();
            }
        }
        return false;
    }

    /**
     * Expands any entry that references a pool into multiple JSON entries and records metadata so the process can be
     * reversed when the table is opened again.
     */
    public static RebuildResult rebuild(JsonNode template,
                                        List<LootPoolEntryModel> entries,
                                        LootTableDescriptor descriptor,
                                        EnchantmentPoolService poolService,
                                        PathAwarePoolLinkWriter linkWriter) throws Exception {
        ObjectNode root;
        if (template != null && template.isObject()) {
            root = ((ObjectNode) template).deepCopy();
        } else {
            root = JsonNodeFactory.instance.objectNode();
        }
        root.put("type", root.path("type").asText("minecraft:generic"));
        ArrayNode pools = JsonNodeFactory.instance.arrayNode();
        ObjectNode pool = JsonNodeFactory.instance.objectNode();
        JsonNode existingRolls = root.path("pools").path(0).path("rolls");
        if (existingRolls.isMissingNode()) {
            pool.put("rolls", 1);
        } else {
            pool.set("rolls", existingRolls.deepCopy());
        }
        ArrayNode newEntries = JsonNodeFactory.instance.arrayNode();
        List<EnchantmentPoolLink> links = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LootPoolEntryModel entry = entries.get(i);
            if (entry.enchantmentPoolId() == null) {
                newEntries.add(buildStandardEntry(entry));
                continue;
            }
            Path packRoot = linkWriter.modpackRoot();
            if (packRoot == null) {
                newEntries.add(buildStandardEntry(entry));
                continue;
            }
            Optional<EnchantmentPool> poolOpt = poolService.findPool(packRoot, entry.enchantmentPoolId());
            if (poolOpt.isEmpty()) {
                newEntries.add(buildStandardEntry(entry));
                continue;
            }
            EnchantmentPool enchantPool = poolOpt.get();
            List<LinkedEnchantment> linkEnchantments = new ArrayList<>();
            double totalWeight = enchantPool.entries().stream().mapToDouble(EnchantmentPoolEntry::weight).sum();
            if (totalWeight <= 0) {
                totalWeight = 1;
            }
            for (EnchantmentPoolEntry option : enchantPool.entries()) {
                ObjectNode entryNode = buildStandardEntry(entry);
                int weighted = Math.max(1, (int) Math.round(entry.weight() * option.weight() / totalWeight));
                entryNode.put("weight", weighted);
                ArrayNode functions = ensureFunctions(entryNode);
                functions.add(buildSetEnchantFunction(option, enchantPool.treasureAllowed()));
                newEntries.add(entryNode);
                linkEnchantments.add(new LinkedEnchantment(option.enchantmentId(), option.weight(), option.minLevel(), option.maxLevel()));
            }
            links.add(new EnchantmentPoolLink(i, enchantPool.id(), entry.itemId(),
                    entry.entryType(), entry.weight(), entry.minCount(), entry.maxCount(), linkEnchantments));
        }
        pool.set("entries", newEntries);
        pools.add(pool);
        root.set("pools", pools);
        linkWriter.writeLinks(descriptor, links);
        return new RebuildResult(root, links);
    }

    private static ObjectNode buildStandardEntry(LootPoolEntryModel entry) {
        ObjectNode entryNode = JsonNodeFactory.instance.objectNode();
        entryNode.put("type", entry.entryType());
        entryNode.put("name", entry.itemId());
        entryNode.put("weight", entry.weight());
        if (entry.minCount() != 1 || entry.maxCount() != 1) {
            ArrayNode functions = ensureFunctions(entryNode);
            functions.add(buildSetCountFunction(entry));
        }
        return entryNode;
    }

    private static ArrayNode ensureFunctions(ObjectNode entryNode) {
        ArrayNode functions;
        JsonNode existing = entryNode.path("functions");
        if (existing.isArray()) {
            functions = (ArrayNode) existing;
        } else {
            functions = JsonNodeFactory.instance.arrayNode();
            entryNode.set("functions", functions);
        }
        return functions;
    }

    private static ObjectNode buildSetCountFunction(LootPoolEntryModel entry) {
        ObjectNode setCount = JsonNodeFactory.instance.objectNode();
        setCount.put("function", "minecraft:set_count");
        if (entry.minCount() == entry.maxCount()) {
            setCount.put("count", entry.minCount());
        } else {
            ObjectNode count = JsonNodeFactory.instance.objectNode();
            count.put("type", "minecraft:uniform");
            count.put("min", entry.minCount());
            count.put("max", entry.maxCount());
            setCount.set("count", count);
        }
        return setCount;
    }

    private static ObjectNode buildSetEnchantFunction(EnchantmentPoolEntry option, boolean treasureAllowed) {
        ObjectNode function = JsonNodeFactory.instance.objectNode();
        function.put("function", "minecraft:set_enchantments");
        ObjectNode enchantments = JsonNodeFactory.instance.objectNode();
        if (option.minLevel() == option.maxLevel()) {
            enchantments.put(option.enchantmentId(), option.minLevel());
        } else {
            ObjectNode range = JsonNodeFactory.instance.objectNode();
            range.put("type", "minecraft:uniform");
            range.put("min", option.minLevel());
            range.put("max", option.maxLevel());
            enchantments.set(option.enchantmentId(), range);
        }
        function.set("enchantments", enchantments);
        function.put("add", false);
        if (treasureAllowed) {
            function.put("treasure", true);
        }
        return function;
    }

    private static List<ParsedEntry> parseEntries(JsonNode lootTable) {
        List<ParsedEntry> entries = new ArrayList<>();
        JsonNode pools = lootTable.path("pools");
        if (!pools.isArray()) {
            return entries;
        }
        int order = 0;
        for (JsonNode pool : pools) {
            JsonNode jsonEntries = pool.path("entries");
            if (!jsonEntries.isArray()) {
                continue;
            }
            for (JsonNode entry : jsonEntries) {
                ObjectNode entryNode = (ObjectNode) entry;
                String type = entry.path("type").asText("minecraft:item");
                String itemId = entry.path("name").asText(entry.path("id").asText("unknown"));
                double weight = entry.path("weight").asDouble(1.0d);
                CountRange range = parseCountRange(entry.path("functions"));
                LootPoolEntryModel model = new LootPoolEntryModel(itemId, weight, type, range.min(), range.max(), null);
                entries.add(new ParsedEntry(order++, model, entryNode));
            }
        }
        return entries;
    }

    private static CountRange parseCountRange(JsonNode functions) {
        if (!functions.isArray()) {
            return new CountRange(1, 1);
        }
        for (JsonNode function : functions) {
            if ("minecraft:set_count".equals(function.path("function").asText())) {
                JsonNode count = function.path("count");
                if (count.isNumber()) {
                    int value = Math.max(1, count.asInt());
                    return new CountRange(value, value);
                } else if (count.isObject()) {
                    JsonNode min = count.path("min");
                    JsonNode max = count.path("max");
                    if (min.isNumber() && max.isNumber()) {
                        int minVal = Math.max(1, (int) Math.round(min.asDouble()));
                        int maxVal = Math.max(minVal, (int) Math.round(max.asDouble()));
                        return new CountRange(minVal, maxVal);
                    }
                }
            }
        }
        return new CountRange(1, 1);
    }

    private record ParsedEntry(int orderIndex, LootPoolEntryModel model, ObjectNode entryNode) {
    }

    private record CountRange(int min, int max) {
    }

    /**
     * Allows callers to persist link metadata after the adapter rebuilds JSON.
     */
    public interface PathAwarePoolLinkWriter {
        Path modpackRoot();

        void writeLinks(LootTableDescriptor descriptor, List<EnchantmentPoolLink> links) throws Exception;
    }

    public record RebuildResult(JsonNode node, List<EnchantmentPoolLink> links) {
    }
}
