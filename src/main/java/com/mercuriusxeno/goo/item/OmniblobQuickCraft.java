package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.ItemStack;

/**
 * Pure utility for omniblob drag-to-distribute (quickcraft) math.
 * Separated from mixin code for testability.
 */
public final class OmniblobQuickCraft {

    private OmniblobQuickCraft() {}

    /**
     * Returns true if the stack is an omniblob with enough volume to distribute.
     *
     * @param stack the carried item stack
     * @return true if this is an omniblob with throwable volume
     */
    public static boolean isOmniblobQuickCraft(ItemStack stack) {
        return stack.getItem() instanceof GooOmniblobItem
            && GooOmniblobItem.getVolume(stack) > 0;
    }

    /**
     * Computes per-slot volume for left-click (charitable) drag distribution.
     * Integer division: volume / slotCount, floored.
     *
     * @param totalVolume total volume in microblobs
     * @param slotCount   number of slots being distributed to
     * @return volume per slot in microblobs
     */
    public static long charitablePerSlot(long totalVolume, int slotCount) {
        if (slotCount <= 0) { return 0L; }
        return totalVolume / slotCount;
    }

    /**
     * Returns the per-slot volume for right-click (greedy) drag distribution:
     * one blob (1,000 mB) per slot.
     *
     * @return 1,000 mB
     */
    public static long greedyPerSlot() {
        return BlobStacks.MB_PER_BLOB;
    }
}
