package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates individual recipes during goo value derivation: computes per-item
 * goo costs from ingredient alternatives and crafting remainders. Extracted from
 * {@link GooValueDerivation} to keep per-class method counts manageable.
 */
final class GooRecipeEvaluator {

    private GooRecipeEvaluator() {
    }

    /**
     * Cheaper = fewer total blobs, then fewer goo types as tiebreaker.
     *
     * @param candidate the proposed replacement value
     * @param current   the existing value to compare against
     * @return true if candidate is cheaper than current
     */
    static boolean isCheaper(GooValue candidate, GooValue current) {
        int cBlobs = candidate.totalBlobs();
        int eBlobs = current.totalBlobs();
        if (cBlobs != eBlobs) {
            return cBlobs < eBlobs;
        }
        return candidate.typeCount() < current.typeCount();
    }

    /**
     * Finds the recipe that produces the cheapest per-item value, or null if none resolves.
     *
     * @param recipes       candidate recipes to evaluate
     * @param baseValues    hand-keyed base values for lookup
     * @param derivedValues in-progress derived values for lookup
     * @return the cheapest recipe result, or null if none resolves
     */
    @Nullable
    static RecipeResult findCheapestRecipeValue(java.util.List<RecipeInput> recipes,
                                                Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues) {
        RecipeResult best = null;
        for (RecipeInput recipe : recipes) {
            best = betterOfTwo(best, evaluateRecipe(recipe, baseValues, derivedValues));
        }
        return best;
    }

    /**
     * Computes the per-item goo value from a recipe, or null if inputs are unresolvable.
     *
     * @param recipe        the recipe to evaluate
     * @param baseValues    hand-keyed base values for lookup
     * @param derivedValues in-progress derived values for lookup
     * @return per-item result, or null if any input is unresolvable or divides to empty
     */
    @Nullable
    static RecipeResult evaluateRecipe(RecipeInput recipe,
                                       Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues) {
        Optional<GooValue> candidate = deriveFromRecipeInput(recipe, baseValues, derivedValues);
        if (candidate.isEmpty()) {
            return null;
        }
        GooValue perItem = candidate.get().divide(recipe.resultCount());
        if (perItem.isEmpty()) {
            return null;
        }
        return new RecipeResult(perItem, recipe);
    }

    /**
     * Returns the cheaper of two recipe results, treating null as infinitely expensive.
     *
     * @param current   the current best result (may be null)
     * @param candidate the new candidate result (may be null)
     * @return the cheaper of the two, or null if both are null
     */
    @Nullable
    static RecipeResult betterOfTwo(@Nullable RecipeResult current, @Nullable RecipeResult candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return isCheaper(candidate.value, current.value) ? candidate : current;
    }

    /**
     * Derives a goo value from a single recipe's ingredients. Returns empty if any
     * ingredient slot has no known value.
     *
     * @param recipe        the recipe to derive from
     * @param baseValues    hand-keyed base values for lookup
     * @param derivedValues in-progress derived values for lookup
     * @return the total input goo value, or empty if any slot is unresolvable
     */
    static Optional<GooValue> deriveFromRecipeInput(RecipeInput recipe,
                                                    Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues) {
        if (recipe.hasNoIngredients()) {
            return Optional.empty();
        }
        GooValue total = GooValue.EMPTY;
        for (Set<Identifier> alternatives : recipe.ingredientAlternatives()) {
            GooValue slotCost = computeSlotCost(alternatives, recipe.containerItems(), baseValues, derivedValues);
            if (slotCost == null) {
                return Optional.empty();
            }
            total = total.add(slotCost, 1);
        }
        return total.isEmpty() ? Optional.empty() : Optional.of(total);
    }

    /**
     * Computes the cost for one ingredient slot by picking the cheapest alternative.
     *
     * @param alternatives   the set of acceptable item IDs for this slot
     * @param containerItems map of ingredient to its crafting remainder
     * @param baseValues     hand-keyed base values for lookup
     * @param derivedValues  in-progress derived values for lookup
     * @return the net goo cost of the cheapest alternative, or null
     */
    @Nullable
    static GooValue computeSlotCost(Set<Identifier> alternatives,
                                    Map<Identifier, Identifier> containerItems,
                                    Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues) {
        Identifier cheapestId = IGooValueLookup.findCheapestAmong(
                alternatives, id -> GooValueDerivation.lookupForDerivation(id, baseValues, derivedValues));
        if (cheapestId == null) {
            return null;
        }
        GooValue gross = GooValueDerivation.lookupForDerivation(cheapestId, baseValues, derivedValues);
        return subtractContainerValue(gross, cheapestId, containerItems, baseValues, derivedValues);
    }

    /**
     * Subtracts the crafting remainder's value from the gross ingredient cost.
     *
     * @param gross          the gross ingredient goo value
     * @param ingredientId   the ingredient item ID
     * @param containerItems map of ingredient to its crafting remainder
     * @param baseValues     hand-keyed base values for lookup
     * @param derivedValues  in-progress derived values for lookup
     * @return the net value after subtracting the container
     */
    static GooValue subtractContainerValue(GooValue gross, Identifier ingredientId,
                                           Map<Identifier, Identifier> containerItems,
                                           Map<Identifier, GooValue> baseValues, Map<Identifier, GooValue> derivedValues) {
        Identifier containerId = containerItems.get(ingredientId);
        if (containerId == null) {
            return gross;
        }
        GooValue containerValue = GooValueDerivation.lookupForDerivation(containerId, baseValues, derivedValues);
        if (containerValue == null || containerValue.isEmpty()) {
            return gross;
        }
        GooValue net = gross.subtract(containerValue);
        GooValue floored = net.floorZero();
        return floored.isEmpty() ? gross : floored;
    }

    /**
     * Pairs a computed value with the recipe that produced it.
     */
    record RecipeResult(GooValue value, RecipeInput source) {
    }
}
