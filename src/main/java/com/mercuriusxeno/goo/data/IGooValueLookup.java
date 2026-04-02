package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Read-only view of the goo value registry. Consumers that only need to look up values
 * should depend on this interface rather than the concrete {@link GooValueRegistry}.
 */
public interface IGooValueLookup {

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
     */
    boolean hasBaseValue(Identifier itemId);

    /**
     * Returns true if the item is explicitly denied a goo value.
     *
     * @param itemId the item's registry ID
     */
    boolean isDenied(Identifier itemId);

    /** Returns the total number of items with effective goo values. */
    int size();

    /** Returns an unmodifiable view of all effective goo values. */
    Map<Identifier, GooValue> getEffectiveValues();
}
