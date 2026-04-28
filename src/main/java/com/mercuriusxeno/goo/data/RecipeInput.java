package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.*;

/**
 * Minecraft-free representation of a recipe for the derivation engine.
 * Decouples the pure derivation logic from Minecraft's Recipe/Ingredient APIs.
 *
 * <p>{@code slotTagIds} tracks which ingredient slots originated from a tag
 * (e.g. {@code #minecraft:planks}). The scaffold generator uses this to group
 * roots that share a tag into a single scaffold entry.</p>
 *
 * @param output                 the output item ID
 * @param resultCount            the number of items produced
 * @param ingredientAlternatives per-slot sets of acceptable input item IDs
 * @param containerItems         items returned after crafting (e.g. buckets)
 * @param slotTagIds             per-slot tag IDs, or empty if not tag-based
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
     * @param output                 the output item ID
     * @param resultCount            number of items produced
     * @param ingredientAlternatives ingredient slots, each a set of alternative item IDs
     */
    public RecipeInput(Identifier output, int resultCount,
                       List<Set<Identifier>> ingredientAlternatives) {
        this(output, resultCount, ingredientAlternatives, Collections.emptyMap(),
                Collections.nCopies(ingredientAlternatives.size(), Optional.empty()));
    }

    /**
     * Backward-compatible constructor for recipes with container items but no tag info.
     *
     * @param output                 the output item ID
     * @param resultCount            number of items produced
     * @param ingredientAlternatives ingredient slots, each a set of alternative item IDs
     * @param containerItems         map of ingredient item ID to returned container item ID
     */
    public RecipeInput(Identifier output, int resultCount,
                       List<Set<Identifier>> ingredientAlternatives,
                       Map<Identifier, Identifier> containerItems) {
        this(output, resultCount, ingredientAlternatives, containerItems,
                Collections.nCopies(ingredientAlternatives.size(), Optional.empty()));
    }

    /**
     * Returns true if this recipe has no ingredient slots.
     *
     * @return true if ingredientAlternatives is empty
     */
    public boolean hasNoIngredients() {
        return ingredientAlternatives.isEmpty();
    }

    /**
     * Number of ingredient slots in this recipe.
     *
     * @return the count of ingredient slots
     */
    public int slotCount() {
        return ingredientAlternatives.size();
    }

    /**
     * Returns the single input item if every ingredient slot accepts the same
     * lone item, or null if slots are heterogeneous, multi-variant, or empty.
     * Recipes satisfying this are eligible for reverse derivation.
     *
     * @return the sole input item ID, or null if not homogeneous
     */
    public @Nullable Identifier soleInputItem() {
        Identifier sole = null;
        for (Set<Identifier> alts : ingredientAlternatives) {
            sole = matchSingleAlternative(alts, sole);
            if (sole == null) {
                return null;
            }
        }
        return sole;
    }

    /**
     * Returns the single item if this slot has exactly one alternative matching current,
     * or null if the slot is heterogeneous or mismatched.
     *
     * @param alts    the alternative item IDs for this ingredient slot
     * @param current the running sole item (null on first iteration)
     * @return the confirmed sole item, or null if broken
     */
    private @Nullable Identifier matchSingleAlternative(Set<Identifier> alts,
                                                        @Nullable Identifier current) {
        if (alts.size() != 1) {
            return null;
        }
        Identifier item = alts.iterator().next();
        if (current != null && !current.equals(item)) {
            return null;
        }
        return item;
    }
}
