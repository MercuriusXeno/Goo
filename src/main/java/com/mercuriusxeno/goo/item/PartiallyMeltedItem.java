package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Represents an item mid-extraction in the crucible. Holds goo values
 * via GooContents (multi-type goo storage).
 * As the crucible operates, goo is drained from this item into the reservoir.
 * If the crucible breaks, the partially melted item drops with remaining goo.
 */
public class PartiallyMeltedItem extends Item {

    /** Creates a partially melted item. Unstackable. */
    public PartiallyMeltedItem(Properties properties) {
        super(properties);
    }

    /** Returns the goo contents from the stack, or EMPTY if absent. */
    public static GooContents getContents(ItemStack stack) {
        GooContents contents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /** Sets the goo contents on the stack. */
    public static void setContents(ItemStack stack, GooContents contents) {
        stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
    }

    /**
     * Creates a partially melted ItemStack pre-loaded with the given goo contents.
     * Used when the crucible begins melting an item.
     */
    public static ItemStack createWith(GooContents contents) {
        ItemStack stack = new ItemStack(
            com.mercuriusxeno.goo.registry.GooItems.PARTIALLY_MELTED_ITEM.get());
        setContents(stack, contents);
        return stack;
    }

    /**
     * Merges additional goo contents into an existing PMI stack.
     * Used by the crucible's shared pool when a new item is inserted.
     */
    public static void mergeContents(ItemStack stack, GooContents additional) {
        GooContents current = getContents(stack);
        setContents(stack, current.mergeWith(additional));
    }

    /** Returns true if all goo has been fully drained from this item. */
    public static boolean isFullyMelted(ItemStack stack) {
        return getContents(stack).isEmpty();
    }

    /**
     * Drains a specific amount of one goo type from the stack.
     * Returns the amount actually drained (may be less if insufficient).
     */
    public static long drain(ItemStack stack, GooType type, long amount) {
        GooContents contents = getContents(stack);
        long available = contents.getVolume(type);
        long drained = Math.min(available, amount);
        if (drained > 0) {
            setContents(stack, contents.withRemoved(type, drained));
        }
        return drained;
    }
}
