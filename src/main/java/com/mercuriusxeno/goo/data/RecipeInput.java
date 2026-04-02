package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minecraft-free representation of a recipe for the derivation engine.
 * Decouples the pure derivation logic from Minecraft's Recipe/Ingredient APIs.
 */
public record RecipeInput(
    Identifier output,
    int resultCount,
    List<Set<Identifier>> ingredientAlternatives,
    Map<Identifier, Identifier> containerItems
) {

    /**
     * Backward-compatible constructor for recipes with no container items.
     *
     * @param output                  the output item ID
     * @param resultCount             number of items produced
     * @param ingredientAlternatives  ingredient slots, each a set of alternative item IDs
     */
    public RecipeInput(Identifier output, int resultCount,
                       List<Set<Identifier>> ingredientAlternatives) {
        this(output, resultCount, ingredientAlternatives, Collections.emptyMap());
    }

    /**
     * Returns true if this recipe has no ingredient slots.
     */
    public boolean hasNoIngredients() {
        return ingredientAlternatives.isEmpty();
    }
}
