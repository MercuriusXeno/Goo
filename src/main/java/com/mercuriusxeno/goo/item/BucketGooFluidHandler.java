package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Capability adapter exposing a bucket's {@link GooContents} as a 15-tank
 * fluid {@link net.neoforged.neoforge.transfer.ResourceHandler}. Each tank
 * index maps to a {@link GooType} ordinal. Capacity is
 * {@link ContainerCapacity#BUCKET_CAP} shared across all tanks.
 *
 * <p>Pipe mods iterate tanks and can insert/extract any goo type at the
 * matching index.</p>
 */
public class BucketGooFluidHandler extends ItemAccessResourceHandler<FluidResource> {

    private static final int TANK_COUNT = GooType.values().length;

    /** Creates a handler wrapping the given bucket's ItemAccess. */
    public BucketGooFluidHandler(ItemAccess itemAccess) {
        super(itemAccess, TANK_COUNT);
    }

    /**
     * Returns the FluidResource for the goo type at this tank index.
     * Empty if the bucket has no goo of that type.
     */
    @Override
    protected FluidResource getResourceFrom(ItemResource item, int index) {
        GooType type = typeForIndex(index);
        if (type == null) return FluidResource.EMPTY;
        GooContents contents = readContents(item);
        long volume = contents.getVolume(type);
        return volume > 0
            ? FluidResource.of(GooFluids.SOURCES.get(type).get())
            : FluidResource.EMPTY;
    }

    /** Returns the volume of the goo type at this tank index, clamped to int. */
    @Override
    protected int getAmountFrom(ItemResource item, int index) {
        GooType type = typeForIndex(index);
        if (type == null) return 0;
        return clampToInt(readContents(item).getVolume(type));
    }

    /**
     * Updates the item resource's GooContents to reflect the new fluid and amount.
     * If the amount is zero, removes the type entirely.
     */
    @Override
    protected ItemResource update(ItemResource item, int oldAmount,
            FluidResource resource, int newAmount) {
        GooContents contents = readContents(item);
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        if (type == null) return item;

        long existing = contents.getVolume(type);
        GooContents cleared = contents.withRemoved(type, existing);
        GooContents updated = newAmount > 0
            ? cleared.withAdded(type, newAmount)
            : cleared;

        return updated.isEmpty()
            ? item.without(GooDataComponents.GOO_CONTENTS)
            : item.with(GooDataComponents.GOO_CONTENTS, updated);
    }

    /**
     * Returns the effective capacity for this tank, accounting for shared
     * capacity. The slot's effective limit is total capacity minus volume
     * stored in all other tanks.
     */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        ItemResource item = itemAccess.getResource();
        GooContents contents = readContents(item);
        long otherVolume = 0;
        GooType[] types = GooType.values();
        for (int i = 0; i < types.length; i++) {
            if (i != index) otherVolume += contents.getVolume(types[i]);
        }
        return clampToInt(ContainerCapacity.BUCKET_CAP - otherVolume);
    }

    /** Only goo fluids matching this tank index are valid. */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) return false;
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        return type != null && type.ordinal() == index;
    }

    // --- Helpers ---

    /** Returns the GooType for the given tank index, or null if out of range. */
    private static GooType typeForIndex(int index) {
        GooType[] types = GooType.values();
        return index >= 0 && index < types.length ? types[index] : null;
    }

    /** Reads GooContents from the item resource, defaulting to EMPTY. */
    private static GooContents readContents(ItemResource item) {
        GooContents contents = item.getComponents().get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /** Safely narrows a long to int, clamping at Integer.MAX_VALUE. */
    private static int clampToInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
