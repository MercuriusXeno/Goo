package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.fluid.BucketOfGooItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;

/**
 * Vat block item: retains goo contents when picked up (like shulker boxes).
 * Supports inventory click interactions matching CanisterItem behavior:
 * blob insert, blob drain, bucket fill/pour.
 */
public class VatBlockItem extends BlockItem {

    /**
     * Creates a vat block item for the given block.
     *
     * @param block      the vat block
     * @param properties the item properties
     */
    public VatBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * Handles cursor-on-vat inventory clicks: blob/omniblob insert,
     * empty-cursor drain, and bucket drain/fill.
     *
     * @param vat         the vat item stack in the slot
     * @param cursor      the item stack on the cursor
     * @param slot        the inventory slot
     * @param action      the click action
     * @param player      the interacting player
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack vat, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (cursor.isEmpty() && action == ClickAction.SECONDARY) {
            return handleEmptyCursorDrain(vat, cursorAccess);
        }
        return action == ClickAction.PRIMARY && handlePrimaryClick(vat, cursor, cursorAccess);
    }

    /**
     * Routes primary click to the appropriate handler based on cursor item type.
     *
     * @param vat          the vat item stack
     * @param cursor       the item stack on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    private static boolean handlePrimaryClick(ItemStack vat, ItemStack cursor, SlotAccess cursorAccess) {
        if (cursor.getItem() instanceof GooBlobItem) {
            return handleBlobInsert(vat, cursor, cursorAccess);
        }
        if (cursor.getItem() instanceof GooOmniblobItem) {
            return handleOmniblobInsert(vat, cursor, cursorAccess);
        }
        if (cursor.is(Items.BUCKET)) {
            return handleBucketDrain(vat, cursor, cursorAccess);
        }
        return cursor.getItem() instanceof BucketOfGooItem
                && handlePartialBucketDrain(vat, cursor, cursorAccess);
    }

    // --- Goo contents helpers (vat-specific capacity) ---

    /**
     * Returns the goo contents from the vat item, or EMPTY if none.
     *
     * @param stack the item stack
     * @return the goo contents, never null
     */
    public static GooContents getGooContents(ItemStack stack) {
        GooContents contents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /**
     * Sets the goo contents on the vat item. Removes component if empty.
     *
     * @param stack    the item stack
     * @param contents the goo contents to set
     */
    public static void setGooContents(ItemStack stack, GooContents contents) {
        if (contents.isEmpty()) {
            stack.remove(GooDataComponents.GOO_CONTENTS.get());
        } else {
            stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
        }
    }

    /**
     * Adds goo to the vat item, capped at vat capacity. Returns amount accepted.
     *
     * @param stack  the vat item stack
     * @param type   the goo type to add
     * @param amount the volume in microblobs to add
     * @return the amount actually accepted
     */
    public static long addGoo(ItemStack stack, GooType type, long amount) {
        long capacity = ContainerCapacity.vatCapacity(GooEnchantments.getCompressionLevel(stack));
        return GooContentsOps.addGoo(stack, type, amount, capacity);
    }

    /**
     * Removes goo of a specific type from the vat item. Returns amount removed.
     *
     * @param stack  the vat item stack
     * @param type   the goo type to remove
     * @param amount the volume in microblobs to remove
     * @return the amount actually removed
     */
    public static long removeGoo(ItemStack stack, GooType type, long amount) {
        return GooContentsOps.removeGoo(stack, type, amount);
    }

    // --- Interaction handlers ---

    /**
     * Transfers blob goo into the vat, shrinking the blob stack by accepted blobs.
     *
     * @param vat         the vat item stack
     * @param cursor      the blob stack on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was transferred
     */
    private static boolean handleBlobInsert(ItemStack vat, ItemStack cursor, SlotAccess cursorAccess) {
        GooType type = ((GooBlobItem) cursor.getItem()).getGooType();
        long volume = BlobStacks.volumeOf(cursor);
        long accepted = addGoo(vat, type, volume);
        if (accepted <= 0) { return false; }
        int blobsUsed = (int) (accepted / BlobStacks.MB_PER_BLOB);
        cursor.shrink(blobsUsed);
        if (cursor.isEmpty()) { cursorAccess.set(ItemStack.EMPTY); }
        return true;
    }

    /**
     * Transfers omniblob goo into the vat, reducing or clearing the cursor.
     *
     * @param vat         the vat item stack
     * @param cursor      the omniblob on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was transferred
     */
    private static boolean handleOmniblobInsert(ItemStack vat, ItemStack cursor, SlotAccess cursorAccess) {
        GooType type = ((GooOmniblobItem) cursor.getItem()).getGooType();
        long volume = GooOmniblobItem.getVolume(cursor);
        long accepted = addGoo(vat, type, volume);
        if (accepted <= 0) { return false; }
        updateOmniblobRemainder(cursor, cursorAccess, volume - accepted);
        return true;
    }

    /** Clears or shrinks the omniblob cursor after a partial transfer.
     *
     * @param cursor       the omniblob on the cursor
     * @param cursorAccess access to set the cursor contents
     * @param remaining    the remaining volume after transfer
     */
    private static void updateOmniblobRemainder(ItemStack cursor, SlotAccess cursorAccess, long remaining) {
        if (remaining <= 0) {
            cursorAccess.set(ItemStack.EMPTY);
        } else {
            GooOmniblobItem.setVolume(cursor, remaining);
        }
    }

    /**
     * Drains up to 64,000 mB of the dominant goo type onto the cursor as a blob output.
     *
     * @param vat         the vat item stack
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was extracted
     */
    private static boolean handleEmptyCursorDrain(ItemStack vat, SlotAccess cursorAccess) {
        GooContents contents = getGooContents(vat);
        if (contents.isEmpty()) { return false; }
        GooType dominant = contents.largestType();
        return dominant != null && extractDominantAsBlobs(vat, cursorAccess, contents, dominant);
    }

    /** Extracts the dominant type from the vat as blob output onto the cursor.
     *
     * @param vat          the vat item stack
     * @param cursorAccess access to set the cursor contents
     * @param contents     the current vat contents
     * @param dominant     the dominant goo type
     * @return true if any goo was extracted
     */
    private static boolean extractDominantAsBlobs(ItemStack vat, SlotAccess cursorAccess,
            GooContents contents, GooType dominant) {
        long toExtract = Math.min(contents.getVolume(dominant), ContainerCapacity.BLOB_CAP);
        long extracted = removeGoo(vat, dominant, toExtract);
        if (extracted <= 0) { return false; }
        cursorAccess.set(BlobStacks.createForOutput(dominant, extracted));
        return true;
    }

    /**
     * Drains up to BUCKET_CAP of the dominant goo type into an empty bucket on the cursor.
     *
     * @param vat         the vat item stack
     * @param cursor      the empty bucket on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was extracted
     */
    private static boolean handleBucketDrain(ItemStack vat, ItemStack cursor, SlotAccess cursorAccess) {
        GooContents contents = getGooContents(vat);
        if (contents.isEmpty()) { return false; }
        GooType dominant = contents.largestType();
        return dominant != null && extractDominantIntoBucket(vat, cursorAccess, contents, dominant);
    }

    /** Extracts the dominant type from the vat into a new bucket on the cursor.
     *
     * @param vat          the vat item stack
     * @param cursorAccess access to set the cursor contents
     * @param contents     the current vat contents
     * @param dominant     the dominant goo type
     * @return true if any goo was extracted
     */
    private static boolean extractDominantIntoBucket(ItemStack vat, SlotAccess cursorAccess,
            GooContents contents, GooType dominant) {
        long toExtract = Math.min(contents.getVolume(dominant), ContainerCapacity.BUCKET_CAP);
        long extracted = removeGoo(vat, dominant, toExtract);
        if (extracted <= 0) { return false; }
        cursorAccess.set(BucketOfGooItem.createWithGoo(dominant, extracted));
        return true;
    }

    /**
     * Drains goo into a partially-filled bucket, up to remaining bucket capacity.
     *
     * @param vat         the vat item stack
     * @param cursor      the partially-filled bucket on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was transferred
     */
    private static boolean handlePartialBucketDrain(ItemStack vat, ItemStack cursor, SlotAccess cursorAccess) {
        GooContents bucketContents = BucketOfGooItem.getContents(cursor);
        long remainingCap = ContainerCapacity.BUCKET_CAP - bucketContents.totalVolume();
        return remainingCap > 0 && drainIntoBucket(vat, cursor, bucketContents, remainingCap);
    }

    /** Drains dominant goo from the vat into the bucket up to the remaining capacity.
     *
     * @param vat            the vat item stack
     * @param cursor         the partially-filled bucket on the cursor
     * @param bucketContents the current bucket contents
     * @param remainingCap   the remaining bucket capacity in microblobs
     * @return true if any goo was transferred
     */
    private static boolean drainIntoBucket(ItemStack vat, ItemStack cursor,
            GooContents bucketContents, long remainingCap) {
        GooContents vatContents = getGooContents(vat);
        if (vatContents.isEmpty()) { return false; }
        GooType dominant = vatContents.largestType();
        if (dominant == null) { return false; }
        long toExtract = Math.min(vatContents.getVolume(dominant), remainingCap);
        long extracted = removeGoo(vat, dominant, toExtract);
        if (extracted <= 0) { return false; }
        BucketOfGooItem.setContents(cursor, bucketContents.withAdded(dominant, extracted));
        return true;
    }
}
