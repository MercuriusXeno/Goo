package com.mercuriusxeno.goo.block.gasket;

import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A read-only fluid source that always reports a fixed amount of water.
 * Extracts succeed but never deplete the source - the water is infinite.
 * Used by waterlogged choral gaskets as the push source.
 */
public final class InfiniteWaterSource implements ResourceHandler<FluidResource> {

    /** The fixed water volume reported by this source (1000 mB = one bucket). */
    private static final int VOLUME = 1000;

    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

    @Override
    public int size() { return 1; }

    @Override
    public FluidResource getResource(int index) { return WATER; }

    @Override
    public long getAmountAsLong(int index) { return VOLUME; }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) { return VOLUME; }

    @Override
    public boolean isValid(int index, FluidResource resource) { return false; }

    /** Extracts up to maxAmount without depleting. The source is infinite. */
    @Override
    public int extract(int index, FluidResource resource, int maxAmount, TransactionContext tx) {
        if (index != 0 || !resource.getFluid().isSame(Fluids.WATER)) { return 0; }
        return Math.min(maxAmount, VOLUME);
    }

    /** Cannot insert into an infinite source. */
    @Override
    public int insert(int index, FluidResource resource, int maxAmount, TransactionContext tx) {
        return 0;
    }
}
