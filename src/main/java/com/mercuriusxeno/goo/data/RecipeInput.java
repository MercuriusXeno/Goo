package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Minecraft-free representation of a recipe for the derivation engine.
 * Decouples the pure derivation logic from Minecraft's Recipe/Ingredient APIs.
 *
 * <p>{@code slotTagIds} tracks which ingredient slots originated from a tag
 * (e.g. {@code #minecraft:planks}). The scaffold generator uses this to group
 * roots that share a tag into a single scaffold entry.</p>
 */
public record RecipeInput(
    Identifier output,
    int resultCount,
    List<Set<Identifier>> ingredientAlternatives,
    Map<Identifier, Identifier> containerItems,
    List<Optional<Identifier>> slotTagIds
) {

    /**
     * Backward-compatible constructor for recipes with no container items or tag info.
     *
     * @param output                  the output item ID
     * @param resultCount             number of items produced
     * @param ingredientAlternatives  ingredient slots, each a set of alternative item IDs
     */
    public RecipeInput(Identifier output, int resultCount,
                       List<Set<Identifier>> ingredientAlternatives) {
        this(output, resultCount, ingredientAlternatives, Collections.emptyMap(),
                Collections.nCopies(ingredientAlternatives.size(), Optional.empty()));
    }

    /**
     * Backward-compatible constructor for recipes with container items but no tag info.
     *
     * @param output                  the output item ID
     * @param resultCount             number of items produced
     * @param ingredientAlternatives  ingredient slots, each a set of alternative item IDs
     * @param containerItems          map of ingredient item ID to returned container item ID
     */
    public RecipeInput(Identifier output, int resultCount,
                       List<Set<Identifier>> ingredientAlternatives,
                       Map<Identifier, Identifier> containerItems) {
        this(output, resultCount, ingredientAlternatives, containerItems,
                Collections.nCopies(ingredientAlternatives.size(), Optional.empty()));
    }

    /**
     * Returns true if this recipe has no ingredient slots.
     */
    public boolean hasNoIngredients() {
        return ingredientAlternatives.isEmpty();
    }
}
