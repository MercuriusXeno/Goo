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

    /** Codec for deserialization (no config parameters, singleton). */
    public static final MapCodec<BlobSizeProperty> MAP_CODEC =
        MapCodec.unit(new BlobSizeProperty());

    /** Volume threshold for the small blob model (Blob = 1,000 mB). */
    private static final long BLOB_THRESHOLD = 1_000L;

    /** Volume threshold for the base blob model (Kiloblob = 1,000,000 mB). */
    private static final long KILOBLOB_THRESHOLD = 1_000_000L;

    /** Volume threshold for the large blob model (Megablob = 1,000,000,000 mB). */
    private static final long MEGABLOB_THRESHOLD = 1_000_000_000L;

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level,
            @Nullable ItemOwner owner, int seed) {
        if (stack.getItem() instanceof GooBlobItem) {
            return 1.0f;
        }
        if (stack.getItem() instanceof GooOmniblobItem) {
            long volume = GooOmniblobItem.getVolume(stack);
            if (volume >= MEGABLOB_THRESHOLD) return 3.0f;
            if (volume >= KILOBLOB_THRESHOLD) return 2.0f;
            if (volume >= BLOB_THRESHOLD) return 1.0f;
        }
        return 0.0f;
    }

    @Override
    public @NonNull MapCodec<BlobSizeProperty> type() {
        return MAP_CODEC;
    }
}
