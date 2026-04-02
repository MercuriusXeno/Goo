package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Classifies how a goo-related item interacts with a goo machine block.
 * Each type maps to a distinct handler in the target machine's interaction dispatch.
 */
public enum GooInteractionType {

    /** Tuner: pass through to let the tuner's own use logic handle it. */
    TUNER_PASS,

    /** Canister item: insert into an empty slot. */
    CANISTER_INSERT,

    /** Blob or omniblob: pour goo volume into a matching slot. */
    BLOB_INSERT,

    /** Filled goo bucket: pour bucket contents into matching slots. */
    BUCKET_INSERT,

    /** Empty vanilla bucket: extract goo into a new filled bucket. */
    BUCKET_EXTRACT;

    /**
     * Returns true if this interaction type should be subject to the
     * interaction cooldown. Only canister insertion needs cooldown because
     * the canister item is not consumed on insert. Blobs and buckets are
     * self-limiting (consumed/emptied on use), so no cooldown is needed.
     *
     * @return true if cooldown applies
     */
    public boolean requiresCooldown() {
        return this == CANISTER_INSERT;
    }

    /**
     * Resolves the interaction type for the given item stack.
     * Goo items self-classify via {@link IGooItemInteraction}; vanilla
     * items (empty bucket) are handled as a special case.
     *
     * @param stack the held item stack
     * @return the interaction type, or null if the item has no goo interaction
     */
    public static @Nullable GooInteractionType classify(ItemStack stack) {
        if (stack.getItem() instanceof IGooItemInteraction gooItem) {
            return gooItem.canisterInteraction();
        }
        if (stack.is(Items.BUCKET)) {
            return BUCKET_EXTRACT;
        }
        return null;
    }
}
