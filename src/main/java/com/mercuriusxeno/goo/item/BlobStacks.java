package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.Map;

/**
 * Pure utility for blob/omniblob stack math. Centralizes volume calculations
 * and the machine output rule so all producers and consumers share one code path.
 */
public final class BlobStacks {

    /** Volume of one blob in microblobs. */
    public static final long MB_PER_BLOB = 1000L;

    /** Maximum blobs in one stack (vanilla stack limit). */
    public static final int MAX_STACK = 64;

    /** Maximum volume representable as a blob stack (64 blobs = 64,000 mB). */
    public static final long MAX_BLOB_STACK_VOLUME = MB_PER_BLOB * MAX_STACK;

    private BlobStacks() {}

    /**
     * Returns the volume of the given item stack in microblobs.
     * For blob stacks: count * 1000. For omniblobs: reads the BLOB_VOLUME component.
     * Returns 0 for non-goo items.
     *
     * @param stack the item stack to measure
     * @return volume in microblobs
     */
    public static long volumeOf(ItemStack stack) {
        if (stack.getItem() instanceof GooBlobItem) {
            return stack.getCount() * MB_PER_BLOB;
        }
        if (stack.getItem() instanceof GooOmniblobItem) {
            return GooOmniblobItem.getVolume(stack);
        }
        return 0L;
    }

    /**
     * Returns the goo type of the given item stack, or null if not a goo blob/omniblob.
     *
     * @param stack the item stack to inspect
     * @return the goo type, or null
     */
    public static GooType gooTypeOf(ItemStack stack) {
        if (stack.getItem() instanceof GooBlobItem blob) {
            return blob.getGooType();
        }
        if (stack.getItem() instanceof GooOmniblobItem omniblob) {
            return omniblob.getGooType();
        }
        return null;
    }

    /**
     * Creates an item stack for machine output following the output rule:
     * if volume is a clean multiple of 1000 and fits in one stack (<=64,000 mB),
     * returns a blob stack. Otherwise returns an omniblob with that volume.
     *
     * @param type     the goo type
     * @param volumeMb volume in microblobs
     * @return a single ItemStack (blob stack or omniblob)
     */
    public static ItemStack createForOutput(GooType type, long volumeMb) {
        if (volumeMb <= 0) { return ItemStack.EMPTY; }
        if (isCleanBlobStack(volumeMb)) {
            return createBlobStack(type, (int) (volumeMb / MB_PER_BLOB));
        }
        return GooOmniblobItem.createWithVolume(type, volumeMb);
    }

    /**
     * Returns true if the volume can be represented as a clean blob stack:
     * evenly divisible by 1000 and at most 64,000 mB.
     *
     * @param volumeMb volume in microblobs
     * @return true if a blob stack is appropriate
     */
    public static boolean isCleanBlobStack(long volumeMb) {
        return volumeMb > 0
            && volumeMb % MB_PER_BLOB == 0
            && volumeMb <= MAX_BLOB_STACK_VOLUME;
    }

    /**
     * Creates a blob stack of the given count.
     *
     * @param type  the goo type
     * @param count number of blobs (1-64)
     * @return a blob ItemStack
     */
    public static ItemStack createBlobStack(GooType type, int count) {
        return new ItemStack(GooItems.BLOBS.get(type).get(), count);
    }

    /**
     * Returns the number of whole blobs in the given volume.
     *
     * @param volumeMb volume in microblobs
     * @return number of whole blobs
     */
    public static long wholeBlobs(long volumeMb) {
        return volumeMb / MB_PER_BLOB;
    }

    /**
     * Returns the sub-blob remainder of the given volume.
     *
     * @param volumeMb volume in microblobs
     * @return remainder in microblobs (0-999)
     */
    public static long remainder(long volumeMb) {
        return volumeMb % MB_PER_BLOB;
    }

    /**
     * Depletes a blob or omniblob stack by the accepted volume.
     * For blob stacks: shrinks count by accepted/1000 (whole blobs only).
     * For omniblobs: deducts volume, consuming the stack if empty.
     *
     * @param stack    the blob or omniblob item stack to deplete
     * @param accepted the volume in microblobs that was accepted
     * @param player   the player holding the stack (for consume callback)
     */
    public static void deplete(ItemStack stack, long accepted, Player player) {
        if (stack.getItem() instanceof GooBlobItem) {
            depleteBlob(stack, accepted);
        } else if (stack.getItem() instanceof GooOmniblobItem) {
            depleteOmniblob(stack, accepted, player);
        }
    }

    /** Shrinks a blob stack by the number of whole blobs consumed.
     *
     * @param stack    the blob stack
     * @param accepted the accepted volume in microblobs
     */
    private static void depleteBlob(ItemStack stack, long accepted) {
        int blobsUsed = (int) (accepted / MB_PER_BLOB);
        stack.shrink(blobsUsed);
    }

    /** Deducts volume from an omniblob, consuming the stack if empty.
     *
     * @param stack    the omniblob stack
     * @param accepted the accepted volume in microblobs
     * @param player   the player holding the stack
     */
    private static void depleteOmniblob(ItemStack stack, long accepted, Player player) {
        long remaining = GooOmniblobItem.getVolume(stack) - accepted;
        if (remaining <= 0) {
            stack.consume(1, player);
        } else {
            GooOmniblobItem.setVolume(stack, remaining);
        }
    }

    /**
     * Drops all goo in a {@link GooContents} as blob items at the given position.
     * Each goo type becomes one item (blob stack or omniblob per the output rule).
     *
     * @param contents the goo contents to drop
     * @param level    the world
     * @param pos      the position to drop items at
     */
    public static void dropAll(GooContents contents, Level level, BlockPos pos) {
        if (contents.isEmpty()) { return; }
        for (Map.Entry<GooType, Long> entry : contents.getAll().entrySet()) {
            Block.popResource(level, pos, createForOutput(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Computes how many blobs to extract from an omniblob based on volume and shift state.
     *
     * @param volume    current volume in microblobs
     * @param shiftHeld whether shift is held
     * @return number of blobs to extract (0-64)
     */
    public static int computeExtractCount(long volume, boolean shiftHeld) {
        long whole = wholeBlobs(volume);
        if (whole <= 0) { return 0; }
        if (shiftHeld) {
            return (int) Math.min(whole, MAX_STACK);
        }
        return 1;
    }
}
