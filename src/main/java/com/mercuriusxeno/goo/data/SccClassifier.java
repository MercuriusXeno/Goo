package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Classifies strongly connected components (SCCs) from the recipe dependency graph.
 * Each SCC is marked as anchored (reachable from a base-valued item) or dead.
 */
final class SccClassifier {

    private SccClassifier() {
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

    /**
     * Returns the first item in sorted order that has a base value, or null.
     *
     * @param sorted        items sorted by identifier
     * @param baseValueKeys item IDs with hand-keyed base values
     * @return the first base-valued item, or null if none
     */
    @Nullable
    static Identifier findDirectAnchor(List<Identifier> sorted,
                                       Set<Identifier> baseValueKeys) {
        for (Identifier id : sorted) {
            if (baseValueKeys.contains(id)) {
                return id;
            }
        }
        return null;
    }

    /**
     * Returns true if any member of the sorted list is in the anchored set.
     *
     * @param sorted   items to check
     * @param anchored set of anchored item IDs
     * @return true if any member is anchored
     */
    static boolean hasAnchoredMember(List<Identifier> sorted, Set<Identifier> anchored) {
        for (Identifier id : sorted) {
            if (anchored.contains(id)) {
                return true;
            }
        }
        return false;
    }
}
