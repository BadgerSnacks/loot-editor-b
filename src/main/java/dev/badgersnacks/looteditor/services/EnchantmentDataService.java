package dev.badgersnacks.looteditor.services;

import dev.badgersnacks.looteditor.model.EnchantmentDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Loads the list of enchantments exposed by the pack (CraftTweaker dumps when available, otherwise a vanilla fallback).
 */
public class EnchantmentDataService {

    private static final List<EnchantmentDescriptor> VANILLA = List.of(
            descriptor("minecraft:aqua_affinity"),
            descriptor("minecraft:bane_of_arthropods"),
            descriptor("minecraft:binding_curse"),
            descriptor("minecraft:blast_protection"),
            descriptor("minecraft:channeling"),
            descriptor("minecraft:depth_strider"),
            descriptor("minecraft:efficiency"),
            descriptor("minecraft:feather_falling"),
            descriptor("minecraft:fire_aspect"),
            descriptor("minecraft:fire_protection"),
            descriptor("minecraft:flame"),
            descriptor("minecraft:fortune"),
            descriptor("minecraft:frost_walker"),
            descriptor("minecraft:impaling"),
            descriptor("minecraft:infinity"),
            descriptor("minecraft:knockback"),
            descriptor("minecraft:looting"),
            descriptor("minecraft:loyalty"),
            descriptor("minecraft:luck_of_the_sea"),
            descriptor("minecraft:lure"),
            descriptor("minecraft:mending"),
            descriptor("minecraft:multishot"),
            descriptor("minecraft:piercing"),
            descriptor("minecraft:power"),
            descriptor("minecraft:projectile_protection"),
            descriptor("minecraft:protection"),
            descriptor("minecraft:punch"),
            descriptor("minecraft:quick_charge"),
            descriptor("minecraft:respiration"),
            descriptor("minecraft:riptide"),
            descriptor("minecraft:sharpness"),
            descriptor("minecraft:fortune"),
            descriptor("minecraft:smite"),
            descriptor("minecraft:soul_speed"),
            descriptor("minecraft:swift_sneak"),
            descriptor("minecraft:sweeping"),
            descriptor("minecraft:thorns"),
            descriptor("minecraft:unbreaking")
    );

    public List<EnchantmentDescriptor> load(Path modpackRoot) {
        Path dump = modpackRoot.resolve("ct_dumps").resolve("enchantment.txt");
        if (!Files.exists(dump)) {
            return VANILLA;
        }
        try {
            List<EnchantmentDescriptor> descriptors = Files.readAllLines(dump).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && line.startsWith("<enchantment:") && line.endsWith(">"))
                    .map(line -> line.substring("<enchantment:".length(), line.length() - 1))
                    .map(EnchantmentDataService::descriptor)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (descriptors.isEmpty()) {
                return VANILLA;
            }
            descriptors.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
            return descriptors;
        } catch (IOException e) {
            return VANILLA;
        }
    }

    private static EnchantmentDescriptor descriptor(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        String namespace = normalized.contains(":") ? normalized.substring(0, normalized.indexOf(':')) : "minecraft";
        String name = normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized;
        String displayName = prettify(name);
        return new EnchantmentDescriptor(namespace + ":" + name, namespace, displayName);
    }

    private static String prettify(String name) {
        return Arrays.stream(name.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }
}
