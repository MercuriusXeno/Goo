package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts Minecraft RecipeHolder instances into MC-free {@link RecipeInput} records,
 * enabling the derivation engine to operate without MC dependencies.
 * Pure-static utility with no instance state.
 */
final class GooValueRecipeAdapter {

    /** Suppress-warnings key for unchecked casts. */
    private static final String SUPPRESS_UNCHECKED = "unchecked";
    /** Log: unsupported recipe type. */
    private static final String LOG_UNSUPPORTED_RECIPE = "Unsupported recipe type: {}";
    /** Log: failed to get recipe result. */
    private static final String LOG_RECIPE_RESULT_FAIL = "Failed to get result from {}: {}";

    /** Ingredient slots paired with per-slot tag identity from the recipe's HolderSet. */
    record AdaptedIngredients(
            List<Set<Identifier>> slots,
            List<Optional<Identifier>> tagIds
    ) {}

    private GooValueRecipeAdapter() {}

    /**
     * Converts MC RecipeHolders into MC-free RecipeInputs.
     *
     * @param holders the MC recipe holders to adapt
     * @param registries the registry access for result assembly
     * @return list of adapted RecipeInput records
     */
    static List<RecipeInput> adaptRecipes(Collection<RecipeHolder<?>> holders,
            HolderLookup.Provider registries) {
        List<RecipeInput> result = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            RecipeInput adapted = adaptOneRecipe(holder.value(), registries);
            if (adapted != null) {
                result.add(adapted);
            }
        }
        return result;
    }

    /**
     * Converts a single MC recipe to a RecipeInput, or null if unsupported.
     *
     * @param recipe the MC recipe to adapt
     * @param registries the registry access for result assembly
     * @return the adapted RecipeInput, or null if unsupported
     */
    @Nullable
    private static RecipeInput adaptOneRecipe(Recipe<?> recipe, HolderLookup.Provider registries) {
        ItemStack resultStack = getRecipeResult(recipe, registries);
        if (resultStack == null || resultStack.isEmpty()) { return null; }

        Identifier outputId = BuiltInRegistries.ITEM.getKey(resultStack.getItem());
        AdaptedIngredients adapted = adaptIngredients(recipe);
        Map<Identifier, Identifier> containers = adaptContainerItems(recipe);
        return new RecipeInput(outputId, resultStack.getCount(),
                adapted.slots(), containers, adapted.tagIds());
    }

    /**
     * Builds a map of ingredient item ID to its crafting remainder item ID.
     *
     * @param recipe the MC recipe to inspect
     * @return map of ingredient to its crafting remainder
     */
    private static Map<Identifier, Identifier> adaptContainerItems(Recipe<?> recipe) {
        Map<Identifier, Identifier> containers = new HashMap<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) { continue; }
            collectContainersFromIngredient(ingredient, containers);
        }
        return containers;
    }

    /**
     * Inspects each alternative in an ingredient for a crafting remainder.
     *
     * @param ingredient the ingredient to inspect
     * @param containers accumulator for ingredient-to-remainder mappings
     */
    private static void collectContainersFromIngredient(Ingredient ingredient,
            Map<Identifier, Identifier> containers) {
        for (Holder<Item> holder : resolveIngredientItems(ingredient)) {
            ItemStackTemplate remainder = holder.value().getCraftingRemainder();
            if (remainder != null) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(holder.value());
                Identifier containerId = BuiltInRegistries.ITEM.getKey(remainder.item().value());
                containers.put(itemId, containerId);
            }
        }
    }

    /**
     * Converts MC Ingredients to sets of item Identifiers, capturing tag identity per slot.
     *
     * @param recipe the MC recipe to extract ingredients from
     * @return adapted ingredient slots with tag identity metadata
     */
    private static AdaptedIngredients adaptIngredients(Recipe<?> recipe) {
        List<Set<Identifier>> slots = new ArrayList<>();
        List<Optional<Identifier>> tagIds = new ArrayList<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) { continue; }
            Set<Identifier> alternatives = adaptOneIngredient(ingredient);
            if (!alternatives.isEmpty()) {
                slots.add(alternatives);
                tagIds.add(extractTagId(ingredient));
            }
        }
        return new AdaptedIngredients(slots, tagIds);
    }

    /**
     * Extracts the tag identity from a standard ingredient's HolderSet, if present.
     *
     * @param ingredient the ingredient to extract a tag from
     * @return the tag ID if present, otherwise empty
     */
    private static Optional<Identifier> extractTagId(Ingredient ingredient) {
        if (ingredient.isCustom()) { return Optional.empty(); }
        HolderSet<Item> holderSet = ingredient.getValues();
        return holderSet.unwrapKey()
                .map(TagKey::location);
    }

    /**
     * Resolves a single MC Ingredient to its set of item IDs.
     *
     * @param ingredient the ingredient to resolve
     * @return set of alternative item IDs
     */
    private static Set<Identifier> adaptOneIngredient(Ingredient ingredient) {
        Set<Identifier> alternatives = new HashSet<>();
        for (Holder<Item> holder : resolveIngredientItems(ingredient)) {
            alternatives.add(BuiltInRegistries.ITEM.getKey(holder.value()));
        }
        return alternatives;
    }

    /**
     * Resolves an ingredient's item holders, using getValues() for standard ingredients.
     *
     * @param ingredient the ingredient to resolve
     * @return list of item holders for the ingredient
     */
    private static List<Holder<Item>> resolveIngredientItems(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return ingredient.getCustomIngredient().items().toList();
        }
        return ingredient.getValues().stream().toList();
    }

    /**
     * Gets the result ItemStack from a recipe using the public assemble() API.
     * Vanilla implementations ignore the input and return a copy of the stored result.
     *
     * @param recipe the MC recipe to get the result from
     * @param registries the registry access for assembly context
     * @return the result ItemStack, or null if unsupported
     */
    @SuppressWarnings({SUPPRESS_UNCHECKED, "PMD.AvoidCatchingGenericException"}) // mod recipes throw unpredictable RuntimeExceptions
    static ItemStack getRecipeResult(Recipe<?> recipe, HolderLookup.Provider registries) {
        try {
            return assembleResult(recipe);
        } catch (RuntimeException e) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_RECIPE_RESULT_FAIL, recipe.getClass().getSimpleName(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * Dispatches to the correct assemble method by recipe type.
     * TransmuteRecipe needs the result field read directly; others use dummy inputs.
     *
     * @param recipe the recipe to assemble
     * @return the result stack, or null if the recipe type is unsupported
     */
    private static ItemStack assembleResult(Recipe<?> recipe) {
        if (recipe instanceof TransmuteRecipe transmute) {
            return transmute.result.apply(DataComponentPatch.EMPTY);
        } else if (recipe instanceof CraftingRecipe crafting) {
            return crafting.assemble(CraftingInput.EMPTY);
        } else if (recipe instanceof SingleItemRecipe single) {
            return single.assemble(new SingleRecipeInput(ItemStack.EMPTY));
        }
        if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_UNSUPPORTED_RECIPE, recipe.getClass().getName()); }
        return null;
    }
}
