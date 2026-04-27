package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.*;

/**
 * Tag-based grouping of scaffold roots. Builds a tag-to-item index from
 * multi-variant recipe slots, then groups roots by shared tag membership.
 * All methods are stateless; the class is not instantiable.
 */
final class ScaffoldTagIndex {

    /**
     * Minimum members for a tag to qualify as a scaffold group.
     */
    private static final int MIN_GROUP_SIZE = 2;

    private ScaffoldTagIndex() {
    }

    /**
     * Groups roots by tag: roots whose itemId appears in a multi-variant tagged slot.
     * A root qualifies if its itemId is a member of some slot with size > 1 that has a tag ID.
     *
     * @param roots   the roots to group
     * @param recipes recipes providing tag metadata for grouping
     * @return map of tag ID to the list of roots that belong to that tag
     */
    static Map<Identifier, List<ScaffoldGenerator.Root>> buildTagGroups(
            List<ScaffoldGenerator.Root> roots, List<RecipeInput> recipes) {
        Map<Identifier, Set<Identifier>> tagMembers = buildTagIndex(recipes);
        return collectTagGroups(roots, tagMembers);
    }

    /**
     * For each tag, collects roots that are members and returns groups with 2+ members.
     *
     * @param roots      all roots to match against tags
     * @param tagMembers tag ID to member item IDs
     * @return map of tag ID to grouped roots (only groups with MIN_GROUP_SIZE+ members)
     */
    private static Map<Identifier, List<ScaffoldGenerator.Root>> collectTagGroups(
            List<ScaffoldGenerator.Root> roots,
            Map<Identifier, Set<Identifier>> tagMembers) {
        Map<Identifier, List<ScaffoldGenerator.Root>> groups = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Set<Identifier>> entry : tagMembers.entrySet()) {
            List<ScaffoldGenerator.Root> members = collectRootsForTag(roots, entry.getValue());
            if (members.size() >= MIN_GROUP_SIZE) {
                groups.put(entry.getKey(), members);
            }
        }
        return groups;
    }

    /**
     * Collects roots whose itemId is in the given tag member set.
     *
     * @param roots    all roots to filter
     * @param tagItems item IDs belonging to the tag
     * @return roots matching the tag (may be empty)
     */
    private static List<ScaffoldGenerator.Root> collectRootsForTag(
            List<ScaffoldGenerator.Root> roots, Set<Identifier> tagItems) {
        List<ScaffoldGenerator.Root> members = new ArrayList<>();
        for (ScaffoldGenerator.Root root : roots) {
            if (tagItems.contains(root.itemId())) {
                members.add(root);
            }
        }
        return members;
    }

    /**
     * Builds an index of tag ID to item IDs from multi-variant tagged recipe slots.
     * Only includes slots with size > 1 that have a tag ID, because single-item
     * tags don't benefit from grouping.
     *
     * @param recipes recipes to scan for tagged multi-variant slots
     * @return map of tag ID to set of member item IDs
     */
    static Map<Identifier, Set<Identifier>> buildTagIndex(List<RecipeInput> recipes) {
        Map<Identifier, Set<Identifier>> index = new HashMap<>();
        for (RecipeInput recipe : recipes) {
            indexRecipeSlots(index, recipe);
        }
        return index;
    }

    /**
     * Indexes all tagged multi-variant slots in a single recipe into the tag index.
     *
     * @param index  tag index to populate (mutated)
     * @param recipe the recipe whose slots to scan
     */
    private static void indexRecipeSlots(Map<Identifier, Set<Identifier>> index, RecipeInput recipe) {
        List<Optional<Identifier>> tagIds = recipe.slotTagIds();
        List<Set<Identifier>> slots = recipe.ingredientAlternatives();
        for (int i = 0; i < tagIds.size(); i++) {
            indexSlotTag(index, tagIds.get(i), slots.get(i));
        }
    }

    /**
     * Indexes a single slot's tag into the tag index if it is a tagged multi-variant slot.
     *
     * @param index tag index to populate (mutated)
     * @param tagId the slot's tag ID (empty if untagged)
     * @param alts  the slot's alternative item IDs
     */
    private static void indexSlotTag(Map<Identifier, Set<Identifier>> index,
                                     Optional<Identifier> tagId, Set<Identifier> alts) {
        if (tagId.isEmpty()) {
            return;
        }
        if (alts.size() <= 1) {
            return;
        }
        index.computeIfAbsent(tagId.get(), k -> new HashSet<>()).addAll(alts);
    }
}
