package com.mercuriusxeno.goo.item.fluid;

import com.mercuriusxeno.goo.item.ContainerCapacity;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Capability adapter exposing a bucket's {@link com.mercuriusxeno.goo.item.GooContents}
 * as a 15-tank fluid {@link net.neoforged.neoforge.transfer.ResourceHandler}. Each tank
 * index maps to a {@link com.mercuriusxeno.goo.GooType} ordinal. Capacity is
 * {@link ContainerCapacity#BUCKET_CAP} shared across all tanks.
 *
 * <p>Pipe mods iterate tanks and can insert/extract any goo type at the
 * matching index.</p>
 */
public class BucketGooFluidHandler extends GooItemFluidHandler {

    /**
     * Creates a handler wrapping the given bucket's ItemAccess.
     *
     * @param itemAccess the item access for the bucket stack
     */
    public BucketGooFluidHandler(ItemAccess itemAccess) {
        super(itemAccess);
    }

    /**
     * Buckets have a fixed capacity defined by {@link ContainerCapacity#BUCKET_CAP}.
     *
     * @param item the item resource (unused for buckets)
     * @return bucket capacity in microblobs
     */
    @Override
    protected long getContainerCapacity(ItemResource item) {
        return ContainerCapacity.BUCKET_CAP;
    }
}
