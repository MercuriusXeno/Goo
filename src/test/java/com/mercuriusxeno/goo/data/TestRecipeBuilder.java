package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test helper for building RecipeInput and GooValue instances concisely.
 */
public final class TestRecipeBuilder {

    /** Prevent instantiation. */
    private TestRecipeBuilder() {}

    /**
     * Creates a RecipeInput with the given output, result count, and ingredient slots.
     * Each ingredient slot is an array of item ID strings (alternatives).
     *
     * @param output      output item ID (e.g. "minecraft:iron_ingot")
     * @param count       number of items produced
     * @param ingredients varargs of ingredient alternatives (each is a String array)
     * @return a RecipeInput with no MC dependency
     */
    @SafeVarargs
    public static RecipeInput recipe(String output, int count, String[]... ingredients) {
        return recipe(output, count, Map.of(), ingredients);
    }

    /**
     * Creates a RecipeInput with container items for returned-container subtraction.
     *
     * @param output      output item ID
     * @param count       number of items produced
     * @param containers  map of ingredient item ID string to container item ID string
     * @param ingredients varargs of ingredient alternatives
     * @return a RecipeInput with container mappings
     */
    @SafeVarargs
    public static RecipeInput recipe(String output, int count,
            Map<String, String> containers, String[]... ingredients) {
        Identifier outputId = Identifier.parse(output);
        List<Set<Identifier>> slots = buildSlots(ingredients);
        Map<Identifier, Identifier> containerMap = buildContainerMap(containers);
        return new RecipeInput(outputId, count, slots, containerMap);
    }

    /** Converts string arrays into identifier slot sets. */
    private static List<Set<Identifier>> buildSlots(String[][] ingredients) {
        List<Set<Identifier>> slots = new ArrayList<>();
        for (String[] alts : ingredients) {
            Set<Identifier> set = new HashSet<>();
            for (String alt : alts) {
                set.add(Identifier.parse(alt));
            }
            slots.add(set);
        }
        return slots;
    }

    /** Converts a string-to-string map into an Identifier-to-Identifier map. */
    private static Map<Identifier, Identifier> buildContainerMap(Map<String, String> containers) {
        if (containers.isEmpty()) return Map.of();
        Map<Identifier, Identifier> map = new HashMap<>();
        containers.forEach((k, v) -> map.put(Identifier.parse(k), Identifier.parse(v)));
        return map;
    }

    /**
     * Creates a single-type GooValue.
     */
    public static GooValue goo(GooType type, int amount) {
        Map<GooType, Integer> map = new EnumMap<>(GooType.class);
        map.put(type, amount);
        return new GooValue(map);
    }

    /**
     * Creates a multi-type GooValue from type-amount pairs.
     */
    public static GooValue goo(GooType type1, int amount1, GooType type2, int amount2) {
        Map<GooType, Integer> map = new EnumMap<>(GooType.class);
        map.put(type1, amount1);
        map.put(type2, amount2);
        return new GooValue(map);
    }

    /**
     * Creates a multi-type GooValue from three type-amount pairs.
     */
    public static GooValue goo(GooType t1, int a1, GooType t2, int a2, GooType t3, int a3) {
        Map<GooType, Integer> map = new EnumMap<>(GooType.class);
        map.put(t1, a1);
        map.put(t2, a2);
        map.put(t3, a3);
        return new GooValue(map);
    }

    /** Shorthand for creating an Identifier from a string. */
    public static Identifier id(String value) {
        return Identifier.parse(value);
    }

    /** Shorthand for a single-item ingredient slot. */
    public static String[] slot(String item) {
        return new String[]{item};
    }

    /** Shorthand for a multi-alternative ingredient slot. */
    public static String[] slot(String... items) {
        return items;
    }
}
