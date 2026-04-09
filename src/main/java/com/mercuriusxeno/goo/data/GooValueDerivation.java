package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
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
        Map<Identifier, GooValue> effective = collectValidBaseValues(baseValues);
        mergeDerivedValues(effective, derivedValues, baseOverride);
        return effective;
    }

    /**
     * Copies base values into a new map, skipping entries with negative goo amounts.
     *
     * @param baseValues hand-keyed base values to validate
     * @return new map containing only non-negative base values
     */
    private static Map<Identifier, GooValue> collectValidBaseValues(
            Map<Identifier, GooValue> baseValues) {
        Map<Identifier, GooValue> effective = new HashMap<>();
        for (var entry : baseValues.entrySet()) {
            addIfNonNegativeBase(effective, entry.getKey(), entry.getValue());
        }
        return effective;
    }

    /**
     * Adds a base value to the map if non-negative, logging an error otherwise.
     *
     * @param dest accumulator map for valid base values
     * @param itemId the item identifier
     * @param value the base goo value to validate
     */
    private static void addIfNonNegativeBase(Map<Identifier, GooValue> dest,
            Identifier itemId, GooValue value) {
        if (value.hasNegative()) {
            if (Goo.LOGGER.isErrorEnabled()) { Goo.LOGGER.error(ERR_NEGATIVE_BASE, itemId, value); }
            return;
        }
        dest.put(itemId, value);
    }

    /**
     * Merges derived values into the effective map, skipping negatives and applying override rule.
     *
     * @param effective accumulator map built from validated base values
     * @param derivedValues recipe-derived values to merge
     * @param baseOverride when true, base always wins; when false, cheaper wins
     */
    private static void mergeDerivedValues(Map<Identifier, GooValue> effective,
            Map<Identifier, GooValue> derivedValues, boolean baseOverride) {
        for (var entry : derivedValues.entrySet()) {
            mergeSingleDerived(effective, entry.getKey(), entry.getValue(), baseOverride);
        }
    }

    /**
     * Merges one derived value into the effective map if it passes validation and override rules.
     *
     * @param effective accumulator map built from validated base values
     * @param itemId the item identifier
     * @param derived the recipe-derived goo value
     * @param baseOverride when true, base always wins; when false, cheaper wins
     */
    private static void mergeSingleDerived(Map<Identifier, GooValue> effective,
            Identifier itemId, GooValue derived, boolean baseOverride) {
        if (derived.hasNegative()) {
            Goo.LOGGER.error(ERR_NEGATIVE_DERIVED, itemId, derived);
            return;
        }
        GooValue base = effective.get(itemId);
        if (base == null || (!baseOverride && GooRecipeEvaluator.isCheaper(derived, base))) {
            effective.put(itemId, derived);
        }
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
        return assembleResult(byOutput, baseOverride);
    }

    /**
     * Builds the final derivation result from derived values and diagnostic scans.
     *
     * @param byOutput recipes grouped by output item ID
     * @param baseOverride when true, base values always win over derived values
     * @return complete derivation result with diagnostics
     */
    private DerivationResult assembleResult(Map<Identifier, List<RecipeInput>> byOutput,
            boolean baseOverride) {
        return new DerivationResult(
            Map.copyOf(derivedValues),
            Map.copyOf(derivationSources),
            buildEffectiveValues(baseValues, derivedValues, baseOverride),
            GooDerivationDiagnostics.detectConflicts(baseValues, derivedValues),
            GooDerivationDiagnostics.detectCycles(byOutput, baseValues),
            GooDerivationDiagnostics.detectDivisibilityLoss(byOutput, baseValues, derivedValues)
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
            changed = runSinglePass(byOutput);
            passes++;
        }
    }

    /**
     * Runs one derivation pass over all output items, returning true if any value changed.
     *
     * @param byOutput recipes grouped by output item ID
     * @return true if any derived value was new or improved during this pass
     */
    private boolean runSinglePass(Map<Identifier, List<RecipeInput>> byOutput) {
        boolean changed = false;
        for (var entry : byOutput.entrySet()) {
            if (tryDeriveItem(entry.getKey(), entry.getValue())) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Attempts to derive or improve the value for a single item from its recipes.
     *
     * @param itemId the item to derive a value for
     * @param recipes candidate recipes producing this item
     * @return true if the derived value was new or improved
     */
    private boolean tryDeriveItem(Identifier itemId, List<RecipeInput> recipes) {
        GooRecipeEvaluator.RecipeResult best =
                GooRecipeEvaluator.findCheapestRecipeValue(recipes, baseValues, derivedValues);
        if (best == null) { return false; }
        GooValue existing = derivedValues.get(itemId);
        if (existing != null && !GooRecipeEvaluator.isCheaper(best.value(), existing)) { return false; }
        derivedValues.put(itemId, best.value());
        derivationSources.put(itemId, best.source());
        return true;
    }

    /**
     * Derives a goo value from a single recipe's ingredients. Returns empty if any
     * ingredient slot has no known value. Package-private for direct testing.
     *
     * @param recipe the recipe to derive from
     * @return the total input goo value, or empty if any slot is unresolvable
     */
    Optional<GooValue> deriveFromRecipeInput(RecipeInput recipe) {
        return GooRecipeEvaluator.deriveFromRecipeInput(recipe, baseValues, derivedValues);
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
