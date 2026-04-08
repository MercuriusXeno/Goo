package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Item model property for blob/omniblob size tiers.
 * Regular blobs always return 1.0 (standard blob model).
 * Omniblobs use volume-based tiers: 0.0 (micro), 1.0 (blob), 2.0 (kilo), 3.0 (mega+).
 */
public class BlobSizeProperty implements RangeSelectItemModelProperty {

    /**
     * Codec for deserialization (no config parameters, singleton).
     *
     * @return the result
     */
    public static final MapCodec<BlobSizeProperty> MAP_CODEC =
        MapCodec.unit(new BlobSizeProperty());

    /** Volume threshold for the small blob model (Blob = 1,000 mB). */
    private static final long BLOB_THRESHOLD = 1_000L;

    /** Volume threshold for the base blob model (Kiloblob = 1,000,000 mB). */
    private static final long KILOBLOB_THRESHOLD = 1_000_000L;

    /** Volume threshold for the large blob model (Megablob = 1,000,000,000 mB). */
    private static final long MEGABLOB_THRESHOLD = 1_000_000_000L;
    /** Model variant value for megablob tier. */
    private static final float SIZE_MEGABLOB = 3.0f;
    /** Model variant value for kiloblob tier. */
    private static final float SIZE_KILOBLOB = 2.0f;

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level,
            @Nullable ItemOwner owner, int seed) {
        if (stack.getItem() instanceof GooBlobItem) { return 1.0f; }
        if (stack.getItem() instanceof GooOmniblobItem) {
            return omniblobSize(GooOmniblobItem.getVolume(stack));
        }
        return 0.0f;
    }

    /** Returns the model variant size for an omniblob based on its volume tier.
     *
     * @param volume the omniblob volume in microblobs
     * @return the model variant float
     */
    private float omniblobSize(long volume) {
        if (volume >= MEGABLOB_THRESHOLD) { return SIZE_MEGABLOB; }
        if (volume >= KILOBLOB_THRESHOLD) { return SIZE_KILOBLOB; }
        return volume >= BLOB_THRESHOLD ? 1.0f : 0.0f;
    }

    @Override
    public @NonNull MapCodec<BlobSizeProperty> type() {
        return MAP_CODEC;
    }
}
