package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure derivation engine: takes base values, denied items, and recipes, and produces
 * a {@link DerivationResult} with derived values, effective values, conflicts, cycles,
 * and divisibility losses.
 *
 * <p>All mutable state is local to one derivation run. Callers receive an immutable result.
 */
class GooValueDerivation {

    private final Map<Identifier, GooValue> baseValues;
    private final Set<Identifier> deniedItems;
    private final Map<Identifier, GooValue> derivedValues = new HashMap<>();
    private final Map<Identifier, RecipeInput> derivationSources = new HashMap<>();

    private GooValueDerivation(Map<Identifier, GooValue> baseValues, Set<Identifier> deniedItems) {
        this.baseValues = baseValues;
        this.deniedItems = deniedItems;
    }

    /**
     * Runs a full derivation pass and returns the results.
     *
     * @param recipes      all recipe inputs to consider
     * @param baseValues   hand-keyed base values (read-only)
     * @param deniedItems  items excluded from derivation output (read-only)
     * @param baseOverride when true, base values always win over derived values
     * @return complete derivation result
     */
    static DerivationResult derive(List<RecipeInput> recipes,
            Map<Identifier, GooValue> baseValues, Set<Identifier> deniedItems,
            boolean baseOverride) {
        return new GooValueDerivation(baseValues, deniedItems).run(recipes, baseOverride);
    }

    /**
     * Builds effective values from base and derived maps using the LCD or base-override rule.
     * Used by {@link GooValueRegistry#loadDerivedCache()} outside of a full derivation run.
     *
     * @param baseValues    hand-keyed base values
     * @param derivedValues recipe-derived values
     * @param baseOverride  when true, base always wins; when false, cheaper wins
     * @return effective value map (new mutable map)
     */
    static Map<Identifier, GooValue> buildEffectiveValues(
            Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues,
            boolean baseOverride) {
        Map<Identifier, GooValue> effective = new HashMap<>(baseValues);
        for (var entry : derivedValues.entrySet()) {
            Identifier itemId = entry.getKey();
            GooValue base = baseValues.get(itemId);
            GooValue derived = entry.getValue();
            if (base == null) {
                effective.put(itemId, derived);
            } else if (!baseOverride && derived.totalBlobs() < base.totalBlobs()) {
                effective.put(itemId, derived);
            }
        }
        return effective;
    }

    /** Groups recipe inputs by their output item ID. */
    static Map<Identifier, List<RecipeInput>> groupByOutput(List<RecipeInput> recipes) {
        Map<Identifier, List<RecipeInput>> result = new HashMap<>();
        for (RecipeInput recipe : recipes) {
            result.computeIfAbsent(recipe.output(), k -> new ArrayList<>()).add(recipe);
        }
        return result;
    }

    /**
     * Returns the cheaper of the base and derived values for an item, or whichever exists.
     * Returns null if neither map contains the item.
     *
     * @param itemId        the item to look up
     * @param baseValues    hand-keyed base values
     * @param derivedValues in-progress derived values
     */
    @Nullable
    static GooValue lookupForDerivation(Identifier itemId,
            Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues) {
        GooValue base = baseValues.get(itemId);
        GooValue derived = derivedValues.get(itemId);
        if (base != null && derived != null) {
            return base.totalBlobs() <= derived.totalBlobs() ? base : derived;
        }
        return base != null ? base : derived;
    }

    /**
     * Classifies a strongly connected component as anchored or dead.
     *
     * @param scc           the strongly connected component
     * @param anchoredNodes nodes reachable from any base-valued item
     * @param baseValueKeys item IDs with hand-keyed base values
     * @return classified RecipeCycle
     */
    static GooValueRegistry.RecipeCycle classifyScc(Set<Identifier> scc,
            Set<Identifier> anchoredNodes, Set<Identifier> baseValueKeys) {
        List<Identifier> sorted = new ArrayList<>(scc);
        Collections.sort(sorted);
        Identifier anchor = findDirectAnchor(sorted, baseValueKeys);
        boolean reachable = anchor != null || hasAnchoredMember(sorted, anchoredNodes);
        return new GooValueRegistry.RecipeCycle(sorted, reachable, anchor);
    }

    // ── Private: derivation pipeline ────────────────────────────────────

    private DerivationResult run(List<RecipeInput> recipes, boolean baseOverride) {
        Map<Identifier, List<RecipeInput>> byOutput = groupByOutput(recipes);
        byOutput.keySet().removeAll(deniedItems);
        multiPassDerive(byOutput);

        List<GooValueRegistry.ValueConflict> conflicts = detectConflicts();
        Map<Identifier, GooValue> effective = buildEffectiveValues(baseValues, derivedValues, baseOverride);
        List<GooValueRegistry.RecipeCycle> cycles = detectCycles(byOutput);
        List<GooValueRegistry.DivisibilityLoss> losses = detectDivisibilityLoss(byOutput);

        return new DerivationResult(
            Map.copyOf(derivedValues),
            Map.copyOf(derivationSources),
            effective, conflicts, cycles, losses
        );
    }

    private void multiPassDerive(Map<Identifier, List<RecipeInput>> byOutput) {
        boolean changed = true;
        int passes = 0;
        while (changed && passes < GooValueRegistry.MAX_DERIVATION_PASSES) {
            changed = false;
            passes++;
            for (var entry : byOutput.entrySet()) {
                if (tryDeriveItem(entry.getKey(), entry.getValue())) {
                    changed = true;
                }
            }
        }
    }

    private boolean tryDeriveItem(Identifier itemId, List<RecipeInput> recipes) {
        RecipeResult best = findCheapestRecipeValue(recipes);
        if (best == null) return false;
        GooValue existing = derivedValues.get(itemId);
        if (existing == null || best.value.totalBlobs() < existing.totalBlobs()) {
            derivedValues.put(itemId, best.value);
            derivationSources.put(itemId, best.source);
            return true;
        }
        return false;
    }

    private record RecipeResult(GooValue value, RecipeInput source) {}

    private RecipeResult findCheapestRecipeValue(List<RecipeInput> recipes) {
        RecipeResult best = null;
        for (RecipeInput recipe : recipes) {
            Optional<GooValue> candidate = deriveFromRecipeInput(recipe);
            if (candidate.isEmpty()) continue;
            GooValue perItem = candidate.get().divide(recipe.resultCount());
            if (perItem.isEmpty()) continue;
            if (best == null || perItem.totalBlobs() < best.value.totalBlobs()) {
                best = new RecipeResult(perItem, recipe);
            }
        }
        return best;
    }

    /**
     * Derives a goo value from a single recipe's ingredients. Returns empty if any
     * ingredient slot has no known value. Package-private for direct testing.
     */
    Optional<GooValue> deriveFromRecipeInput(RecipeInput recipe) {
        if (recipe.hasNoIngredients()) return Optional.empty();
        GooValue total = GooValue.EMPTY;
        for (Set<Identifier> alternatives : recipe.ingredientAlternatives()) {
            GooValue slotCost = computeSlotCost(alternatives, recipe.containerItems());
            if (slotCost == null) return Optional.empty();
            total = total.add(slotCost, 1);
        }
        return total.isEmpty() ? Optional.empty() : Optional.of(total);
    }

    private GooValue computeSlotCost(Set<Identifier> alternatives,
            Map<Identifier, Identifier> containerItems) {
        Identifier cheapestId = GooValueRegistry.findCheapestAmong(
            alternatives, id -> lookupForDerivation(id, baseValues, derivedValues));
        if (cheapestId == null) return null;
        GooValue gross = lookupForDerivation(cheapestId, baseValues, derivedValues);
        return subtractContainerValue(gross, cheapestId, containerItems);
    }

    private GooValue subtractContainerValue(GooValue gross, Identifier ingredientId,
            Map<Identifier, Identifier> containerItems) {
        Identifier containerId = containerItems.get(ingredientId);
        if (containerId == null) return gross;
        GooValue containerValue = lookupForDerivation(containerId, baseValues, derivedValues);
        if (containerValue == null || containerValue.isEmpty()) return gross;
        GooValue net = gross.subtract(containerValue);
        return net.isEmpty() ? gross : net;
    }

    private List<GooValueRegistry.ValueConflict> detectConflicts() {
        List<GooValueRegistry.ValueConflict> conflicts = new ArrayList<>();
        for (var entry : baseValues.entrySet()) {
            GooValue derived = derivedValues.get(entry.getKey());
            if (derived != null && derived.totalBlobs() != entry.getValue().totalBlobs()) {
                conflicts.add(new GooValueRegistry.ValueConflict(
                    entry.getKey(), entry.getValue(), derived));
            }
        }
        return conflicts;
    }

    private List<GooValueRegistry.RecipeCycle> detectCycles(
            Map<Identifier, List<RecipeInput>> byOutput) {
        Map<Identifier, Set<Identifier>> deps = buildDependencyGraph(byOutput);
        List<Set<Identifier>> sccs = DirectedGraphUtils.findStronglyConnectedComponents(deps);
        Set<Identifier> anchored = DirectedGraphUtils.findAnchoredNodes(deps, baseValues.keySet());
        List<GooValueRegistry.RecipeCycle> cycles = new ArrayList<>();
        for (Set<Identifier> scc : sccs) {
            cycles.add(classifyScc(scc, anchored, baseValues.keySet()));
        }
        return cycles;
    }

    private Map<Identifier, Set<Identifier>> buildDependencyGraph(
            Map<Identifier, List<RecipeInput>> byOutput) {
        Map<Identifier, Set<Identifier>> deps = new HashMap<>();
        for (var entry : byOutput.entrySet()) {
            Identifier output = entry.getKey();
            Set<Identifier> inputs = collectAllInputIds(entry.getValue(), output);
            if (!inputs.isEmpty()) {
                deps.put(output, inputs);
            }
        }
        return deps;
    }

    private Set<Identifier> collectAllInputIds(List<RecipeInput> recipes, Identifier exclude) {
        Set<Identifier> result = new HashSet<>();
        for (RecipeInput recipe : recipes) {
            for (Set<Identifier> alternatives : recipe.ingredientAlternatives()) {
                for (Identifier id : alternatives) {
                    if (!id.equals(exclude)) result.add(id);
                }
            }
        }
        return result;
    }

    private static @Nullable Identifier findDirectAnchor(List<Identifier> sorted,
            Set<Identifier> baseValueKeys) {
        for (Identifier id : sorted) {
            if (baseValueKeys.contains(id)) return id;
        }
        return null;
    }

    private static boolean hasAnchoredMember(List<Identifier> sorted, Set<Identifier> anchored) {
        for (Identifier id : sorted) {
            if (anchored.contains(id)) return true;
        }
        return false;
    }

    private List<GooValueRegistry.DivisibilityLoss> detectDivisibilityLoss(
            Map<Identifier, List<RecipeInput>> byOutput) {
        List<GooValueRegistry.DivisibilityLoss> losses = new ArrayList<>();
        for (var entry : byOutput.entrySet()) {
            for (RecipeInput recipe : entry.getValue()) {
                checkDivisibilityLoss(entry.getKey(), recipe, losses);
            }
        }
        return losses;
    }

    private void checkDivisibilityLoss(Identifier outputId, RecipeInput recipe,
            List<GooValueRegistry.DivisibilityLoss> losses) {
        if (recipe.resultCount() <= 1) return;
        Optional<GooValue> inputValue = deriveFromRecipeInput(recipe);
        if (inputValue.isEmpty()) return;
        int inputTotal = inputValue.get().totalBlobs();
        int remainder = inputTotal % recipe.resultCount();
        if (remainder == 0) return;
        int perItem = inputTotal / recipe.resultCount();
        losses.add(new GooValueRegistry.DivisibilityLoss(
            outputId, recipe.resultCount(), inputTotal, perItem, remainder, recipe));
    }
}

/** Immutable result of one full derivation run. */
record DerivationResult(
    Map<Identifier, GooValue> derivedValues,
    Map<Identifier, RecipeInput> derivationSources,
    Map<Identifier, GooValue> effectiveValues,
    List<GooValueRegistry.ValueConflict> conflicts,
    List<GooValueRegistry.RecipeCycle> cycles,
    List<GooValueRegistry.DivisibilityLoss> divisibilityLosses
) {}
