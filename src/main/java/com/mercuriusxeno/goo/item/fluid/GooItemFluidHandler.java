package com.mercuriusxeno.goo.item.fluid;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Shared base for goo item fluid handlers (canisters, etc.) that
 * expose {@link GooContents} as a 15-tank fluid ResourceHandler. Each tank
 * index maps to a {@link GooType} ordinal with shared capacity across all
 * tanks.
 *
 * <p>Subclasses differ only in how total capacity is determined.</p>
 */
public abstract class GooItemFluidHandler extends ItemAccessResourceHandler<FluidResource> {

    private static final int TANK_COUNT = GooType.values().length;

    /**
     * Creates a handler wrapping the given item's ItemAccess.
     *
     * @param itemAccess the item access for the container stack
     */
    protected GooItemFluidHandler(ItemAccess itemAccess) {
        super(itemAccess, TANK_COUNT);
    }

    /**
     * Returns the total capacity of this container in microblobs.
     * Canister derives capacity from enchantments.
     *
     * @param item the item resource to read capacity from
     * @return total capacity in microblobs
     */
    protected abstract long getContainerCapacity(ItemResource item);

    /**
     * Returns the FluidResource for the goo type at this tank index.
     * Empty if the container has no goo of that type.
     *
     * @param item  the item resource to read from
     * @param index the tank index (GooType ordinal)
     * @return the fluid resource, or EMPTY if none
     */
    @Override
    protected FluidResource getResourceFrom(ItemResource item, int index) {
        GooType type = typeForIndex(index);
        if (type == null) { return FluidResource.EMPTY; }
        GooContents contents = readContents(item);
        long volume = contents.getVolume(type);
        return volume > 0
            ? FluidResource.of(GooFluids.SOURCES.get(type).get())
            : FluidResource.EMPTY;
    }

    /**
     * Returns the volume of the goo type at this tank index, clamped to int.
     *
     * @param item  the item resource to read from
     * @param index the tank index (GooType ordinal)
     * @return volume clamped to int
     */
    @Override
    protected int getAmountFrom(ItemResource item, int index) {
        GooType type = typeForIndex(index);
        if (type == null) { return 0; }
        return clampToInt(readContents(item).getVolume(type));
    }

    /**
     * Updates the item resource's GooContents to reflect the new fluid and amount.
     * If the amount is zero, removes the type entirely.
     *
     * @param item      the item resource to update
     * @param oldAmount the previous amount
     * @param resource  the fluid resource being transferred
     * @param newAmount the new amount after transfer
     * @return the updated item resource
     */
    @Override
    protected ItemResource update(ItemResource item, int oldAmount,
            FluidResource resource, int newAmount) {
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        if (type == null) { return item; }
        GooContents updated = rebuildContents(readContents(item), type, newAmount);
        return applyContents(item, updated);
    }

    /**
     * Returns the effective capacity for this tank, accounting for shared
     * capacity. The slot's effective limit is total capacity minus volume
     * stored in all other tanks.
     *
     * @param index    the tank index (GooType ordinal)
     * @param resource the fluid resource being queried
     * @return effective capacity clamped to int
     */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        ItemResource item = itemAccess.getResource();
        long totalCapacity = getContainerCapacity(item);
        long otherVolume = 0;
        GooContents contents = readContents(item);
        GooType[] types = GooType.values();
        for (int i = 0; i < types.length; i++) {
            if (i != index) { otherVolume += contents.getVolume(types[i]); }
        }
        return clampToInt(totalCapacity - otherVolume);
    }

    /**
     * Only goo fluids matching this tank index are valid.
     *
     * @param index    the tank index (GooType ordinal)
     * @param resource the fluid resource to validate
     * @return true if the resource matches this tank's goo type
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) { return false; }
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        return type != null && type.ordinal() == index;
    }

    // --- Shared helpers ---

    /**
     * Replaces the volume for one goo type, removing it entirely if zero.
     *
     * @param contents the current contents
     * @param type     the goo type to replace
     * @param amount   the new volume (0 to remove)
     * @return the updated contents
     */
    protected static GooContents rebuildContents(GooContents contents, GooType type, int amount) {
        GooContents cleared = contents.withRemoved(type, contents.getVolume(type));
        return amount > 0 ? cleared.withAdded(type, amount) : cleared;
    }

    /**
     * Applies goo contents to an item resource, removing the component if empty.
     *
     * @param item     the item resource
     * @param contents the goo contents to apply
     * @return the updated item resource
     */
    protected static ItemResource applyContents(ItemResource item, GooContents contents) {
        return contents.isEmpty()
            ? item.without(GooDataComponents.GOO_CONTENTS)
            : item.with(GooDataComponents.GOO_CONTENTS, contents);
    }

    /**
     * Returns the GooType for the given tank index, or null if out of range.
     *
     * @param index the tank index
     * @return the corresponding GooType, or null
     */
    protected static GooType typeForIndex(int index) {
        GooType[] types = GooType.values();
        return index >= 0 && index < types.length ? types[index] : null;
    }

    /**
     * Reads GooContents from the item resource, defaulting to EMPTY.
     *
     * @param item the item resource to read from
     * @return the goo contents, never null
     */
    protected static GooContents readContents(ItemResource item) {
        GooContents contents = item.getComponents().get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /**
     * Safely narrows a long to int, clamping at Integer.MAX_VALUE.
     *
     * @param value the long value to clamp
     * @return the value as int, capped at Integer.MAX_VALUE
     */
    protected static int clampToInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
