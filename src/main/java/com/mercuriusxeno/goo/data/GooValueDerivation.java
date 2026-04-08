package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.Goo;
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
final class GooValueDerivation {

    /** Log message for negative base values. */
    private static final String ERR_NEGATIVE_BASE = "Negative goo in base value for {}: {} -- skipped";
    /** Log message for negative derived values. */
    private static final String ERR_NEGATIVE_DERIVED = "Negative goo in derived value for {}: {} -- skipped";

    private final Map<Identifier, GooValue> baseValues;
    private final Set<Identifier> deniedItems;
    private final Map<Identifier, GooValue> derivedValues = new HashMap<>();
    private final Map<Identifier, RecipeInput> derivationSources = new HashMap<>();

    /**
     * Creates a new derivation context with the given base values and deny list.
     *
     * @param baseValues hand-keyed base values (read-only reference)
     * @param deniedItems items excluded from derivation output
     */
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
        Map<Identifier, GooValue> effective = new HashMap<>();
        // Validate base values
        for (var entry : baseValues.entrySet()) {
            GooValue value = entry.getValue();
            if (value.hasNegative()) {
                if (Goo.LOGGER.isErrorEnabled()) { Goo.LOGGER.error(ERR_NEGATIVE_BASE, entry.getKey(), value); }
                continue;
            }
            effective.put(entry.getKey(), value);
        }
        // Merge derived values, validating each
        for (var entry : derivedValues.entrySet()) {
            Identifier itemId = entry.getKey();
            GooValue derived = entry.getValue();
            if (derived.hasNegative()) {
                Goo.LOGGER.error(ERR_NEGATIVE_DERIVED, itemId, derived);
                continue;
            }
            GooValue base = effective.get(itemId);
            if (base == null) {
                effective.put(itemId, derived);
            } else if (!baseOverride && isCheaper(derived, base)) {
                effective.put(itemId, derived);
            }
        }
        return effective;
    }

    /**
     * Groups recipe inputs by their output item ID.
     *
     * @param recipes the recipe inputs to group
     * @return map of output item ID to list of recipes producing it
     */
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
     * @return the cheaper value, or null if neither map contains the item
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

    /**
     * Executes the full derivation pipeline: multi-pass derive, conflict/cycle/loss detection.
     *
     * @param recipes all recipe inputs to consider
     * @param baseOverride when true, base values always win over derived values
     * @return complete derivation result
     */
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

    /**
     * Iterates derivation passes until no new values are found or the pass limit is hit.
     *
     * @param byOutput recipes grouped by output item ID
     */
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

    /**
     * Attempts to derive or improve the value for a single item from its recipes.
     *
     * @param itemId the item to derive a value for
     * @param recipes candidate recipes producing this item
     * @return true if the derived value was new or improved
     */
    private boolean tryDeriveItem(Identifier itemId, List<RecipeInput> recipes) {
        RecipeResult best = findCheapestRecipeValue(recipes);
        if (best == null) { return false; }
        GooValue existing = derivedValues.get(itemId);
        if (existing == null || isCheaper(best.value, existing)) {
            derivedValues.put(itemId, best.value);
            derivationSources.put(itemId, best.source);
            return true;
        }
        return false;
    }

    /** Pairs a computed value with the recipe that produced it. */
    private record RecipeResult(GooValue value, RecipeInput source) {}

    /**
     * Cheaper = fewer total blobs, then fewer goo types as tiebreaker.
     *
     * @param candidate the proposed replacement value
     * @param current the existing value to compare against
     * @return true if candidate is cheaper than current
     */
    private static boolean isCheaper(GooValue candidate, GooValue current) {
        int cBlobs = candidate.totalBlobs();
        int eBlobs = current.totalBlobs();
        if (cBlobs != eBlobs) { return cBlobs < eBlobs; }
        return candidate.typeCount() < current.typeCount();
    }

    /**
     * Finds the recipe that produces the cheapest per-item value, or null if none resolves.
     *
     * @param recipes candidate recipes to evaluate
     * @return the cheapest recipe result, or null if none resolves
     */
    private RecipeResult findCheapestRecipeValue(List<RecipeInput> recipes) {
        RecipeResult best = null;
        for (RecipeInput recipe : recipes) {
            Optional<GooValue> candidate = deriveFromRecipeInput(recipe);
            if (candidate.isEmpty()) { continue; }
            GooValue perItem = candidate.get().divide(recipe.resultCount());
            if (perItem.isEmpty()) { continue; }
            if (best == null || isCheaper(perItem, best.value)) {
                best = new RecipeResult(perItem, recipe);
            }
        }
        return best;
    }

    /**
     * Derives a goo value from a single recipe's ingredients. Returns empty if any
     * ingredient slot has no known value. Package-private for direct testing.
     *
     * @param recipe the recipe to derive from
     * @return the total input goo value, or empty if any slot is unresolvable
     */
    Optional<GooValue> deriveFromRecipeInput(RecipeInput recipe) {
        if (recipe.hasNoIngredients()) { return Optional.empty(); }
        GooValue total = GooValue.EMPTY;
        for (Set<Identifier> alternatives : recipe.ingredientAlternatives()) {
            GooValue slotCost = computeSlotCost(alternatives, recipe.containerItems());
            if (slotCost == null) { return Optional.empty(); }
            total = total.add(slotCost, 1);
        }
        return total.isEmpty() ? Optional.empty() : Optional.of(total);
    }

    /**
     * Computes the cost for one ingredient slot by picking the cheapest alternative.
     *
     * @param alternatives the set of acceptable item IDs for this slot
     * @param containerItems map of ingredient to its crafting remainder
     * @return the net goo cost of the cheapest alternative, or null
     */
    private GooValue computeSlotCost(Set<Identifier> alternatives,
            Map<Identifier, Identifier> containerItems) {
        Identifier cheapestId = GooValueRegistry.findCheapestAmong(
            alternatives, id -> lookupForDerivation(id, baseValues, derivedValues));
        if (cheapestId == null) { return null; }
        GooValue gross = lookupForDerivation(cheapestId, baseValues, derivedValues);
        return subtractContainerValue(gross, cheapestId, containerItems);
    }

    /**
     * Subtracts the crafting remainder's value from the gross ingredient cost.
     *
     * @param gross the gross ingredient goo value
     * @param ingredientId the ingredient item ID
     * @param containerItems map of ingredient to its crafting remainder
     * @return the net value after subtracting the container
     */
    private GooValue subtractContainerValue(GooValue gross, Identifier ingredientId,
            Map<Identifier, Identifier> containerItems) {
        Identifier containerId = containerItems.get(ingredientId);
        if (containerId == null) { return gross; }
        GooValue containerValue = lookupForDerivation(containerId, baseValues, derivedValues);
        if (containerValue == null || containerValue.isEmpty()) { return gross; }
        GooValue net = gross.subtract(containerValue);
        GooValue floored = net.floorZero();
        return floored.isEmpty() ? gross : floored;
    }

    /**
     * Detects items where the base value and derived value disagree on total blobs.
     *
     * @return list of value conflicts between base and derived
     */
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

    /**
     * Finds strongly connected components in the recipe dependency graph and classifies each.
     *
     * @param byOutput recipes grouped by output item ID
     * @return classified recipe cycles (anchored or dead)
     */
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

    /**
     * Builds a forward dependency graph: output item to set of input items it depends on.
     *
     * @param byOutput recipes grouped by output item ID
     * @return dependency graph as output to input-set map
     */
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

    /**
     * Collects all input item IDs from a set of recipes, excluding the given output ID.
     *
     * @param recipes the recipes to collect inputs from
     * @param exclude item ID to exclude (typically the output item)
     * @return set of all input item IDs
     */
    private Set<Identifier> collectAllInputIds(List<RecipeInput> recipes, Identifier exclude) {
        Set<Identifier> result = new HashSet<>();
        for (RecipeInput recipe : recipes) {
            for (Set<Identifier> alternatives : recipe.ingredientAlternatives()) {
                for (Identifier id : alternatives) {
                    if (!id.equals(exclude)) { result.add(id); }
                }
            }
        }
        return result;
    }

    /**
     * Returns the first item in sorted order that has a base value, or null.
     *
     * @param sorted items sorted by identifier
     * @param baseValueKeys item IDs with hand-keyed base values
     * @return the first base-valued item, or null if none
     */
    private static @Nullable Identifier findDirectAnchor(List<Identifier> sorted,
            Set<Identifier> baseValueKeys) {
        for (Identifier id : sorted) {
            if (baseValueKeys.contains(id)) { return id; }
        }
        return null;
    }

    /**
     * Returns true if any member of the sorted list is in the anchored set.
     *
     * @param sorted items to check
     * @param anchored set of anchored item IDs
     * @return true if any member is anchored
     */
    private static boolean hasAnchoredMember(List<Identifier> sorted, Set<Identifier> anchored) {
        for (Identifier id : sorted) {
            if (anchored.contains(id)) { return true; }
        }
        return false;
    }

    /**
     * Scans all multi-output recipes for integer division remainder losses.
     *
     * @param byOutput recipes grouped by output item ID
     * @return list of detected divisibility losses
     */
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

    /**
     * Checks a single recipe for divisibility loss and appends to the losses list if found.
     *
     * @param outputId the output item ID
     * @param recipe the recipe to check
     * @param losses accumulator for detected losses
     */
    private void checkDivisibilityLoss(Identifier outputId, RecipeInput recipe,
            List<GooValueRegistry.DivisibilityLoss> losses) {
        if (recipe.resultCount() <= 1) { return; }
        Optional<GooValue> inputValue = deriveFromRecipeInput(recipe);
        if (inputValue.isEmpty()) { return; }
        int inputTotal = inputValue.get().totalBlobs();
        int remainder = inputTotal % recipe.resultCount();
        if (remainder == 0) { return; }
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
