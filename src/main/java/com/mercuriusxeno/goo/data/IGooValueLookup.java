package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Read-only view of the goo value registry. Consumers that only need to look up values
 * should depend on this interface rather than the concrete {@link GooValueRegistry}.
 */
public interface IGooValueLookup {

    /**
     * Returns the item ID with the lowest {@link GooValue#totalBlobs()} among {@code candidates},
     * using {@code lookup} to resolve each ID. Null and empty values are skipped.
     *
     * @param candidates set of item IDs to compare
     * @param lookup     function from item ID to GooValue (may return null)
     * @return cheapest item ID, or null if no candidate has a non-empty value
     */
    static @Nullable Identifier findCheapestAmong(
            Set<Identifier> candidates, Function<Identifier, GooValue> lookup) {
        Identifier cheapestId = null;
        int cheapestTotal = Integer.MAX_VALUE;
        for (Identifier itemId : candidates) {
            GooValue val = lookup.apply(itemId);
            if (val != null && !val.isEmpty() && val.totalBlobs() < cheapestTotal) {
                cheapestTotal = val.totalBlobs();
                cheapestId = itemId;
            }
        }
        return cheapestId;
    }

    /**
     * Returns the effective goo value for an item ID, or null if none is registered.
     *
     * @param itemId the item's registry ID
     * @return effective GooValue, or null
     */
    @Nullable GooValue lookup(Identifier itemId);

    /**
     * Returns true if the item has a hand-keyed base value (not derived from recipes).
     *
     * @param itemId the item's registry ID
     * @return true if the item has a hand-keyed base value
     */
    boolean hasBaseValue(Identifier itemId);

    /**
     * Returns true if the item is explicitly denied a goo value.
     *
     * @param itemId the item's registry ID
     * @return true if the item is on the deny list
     */
    boolean isDenied(Identifier itemId);

    /**
     * Returns true if the item is restricted from plexer reconstitution.
     * Restricted items still have goo values (decomposable) but cannot be created.
     *
     * @param itemId the item's registry ID
     * @return true if the item is on the restricted list
     */
    boolean isRestricted(Identifier itemId);

    /**
     * Returns the total number of items with effective goo values.
     *
     * @return effective value count
     */
    int size();

    /**
     * Returns an unmodifiable view of all effective goo values.
     *
     * @return unmodifiable map of item ID to effective GooValue
     */
    Map<Identifier, GooValue> getEffectiveValues();
}
