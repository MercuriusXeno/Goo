package com.mercuriusxeno.goo.data;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Items that derive their goo value from data components implement this interface.
 * The registry falls back to this when no static value is found for the item type.
 */
@FunctionalInterface
public interface IComponentValueProvider {

    /**
     * Computes the goo value for a specific item stack based on its data components.
     *
     * @param stack    the item stack to evaluate
     * @param registry the registry for looking up base item values
     * @return the computed goo value, or null if no value can be determined
     */
    @Nullable
    GooValue computeComponentValue(ItemStack stack, IGooValueLookup registry);
}
