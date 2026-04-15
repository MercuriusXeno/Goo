package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Static helpers for canister inventory click handling: blob/omniblob insert,
 * empty-cursor drain, and gasket cleanup on placement.
 * Extracted from CanisterItem to reduce method count.
 */
final class CanisterInventoryHandler {

    private CanisterInventoryHandler() { }

    // --- Primary click dispatch ---

    /**
     * Dispatches primary-click interactions based on cursor item type.
     *
     * @param canister     the canister item stack
     * @param cursor       the cursor item stack
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    static boolean handlePrimaryClick(
            ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        if (cursor.getItem() instanceof GooBlobItem) {
            return handleBlobInsert(canister, cursor, cursorAccess);
        }
        if (cursor.getItem() instanceof GooOmniblobItem) {
            return handleOmniblobInsert(canister, cursor, cursorAccess);
        }
        return false;
    }

    // --- Blob/omniblob insert ---

    /**
     * Transfers blob goo into the canister, shrinking the blob stack by accepted blobs.
     *
     * @param canister    the canister item stack
     * @param cursor      the blob stack on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was transferred
     */
    private static boolean handleBlobInsert(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooType type = ((GooBlobItem) cursor.getItem()).getGooType();
        long volume = BlobStacks.volumeOf(cursor);
        long accepted = CanisterItem.addGoo(canister, type, volume);
        if (accepted <= 0) { return false; }

        int blobsUsed = (int) (accepted / BlobStacks.MB_PER_BLOB);
        cursor.shrink(blobsUsed);
        if (cursor.isEmpty()) { cursorAccess.set(ItemStack.EMPTY); }
        return true;
    }

    /**
     * Transfers omniblob goo into the canister, reducing or clearing the cursor.
     *
     * @param canister    the canister item stack
     * @param cursor      the omniblob on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was transferred
     */
    private static boolean handleOmniblobInsert(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooType type = ((GooOmniblobItem) cursor.getItem()).getGooType();
        long volume = GooOmniblobItem.getVolume(cursor);
        long accepted = CanisterItem.addGoo(canister, type, volume);
        if (accepted <= 0) { return false; }

        applyOmniblobRemainder(cursor, cursorAccess, volume - accepted);
        return true;
    }

    /**
     * Clears the omniblob cursor or updates its remaining volume.
     *
     * @param cursor       the omniblob item stack
     * @param cursorAccess access to set the cursor contents
     * @param remaining    the remaining volume after transfer
     */
    private static void applyOmniblobRemainder(ItemStack cursor, SlotAccess cursorAccess, long remaining) {
        if (remaining <= 0) {
            cursorAccess.set(ItemStack.EMPTY);
        } else {
            GooOmniblobItem.setVolume(cursor, remaining);
        }
    }

    // --- Drain operations ---

    /**
     * Drains up to 64,000 mB of the dominant goo type onto the cursor as a blob output.
     *
     * @param canister    the canister item stack
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was extracted
     */
    static boolean handleEmptyCursorDrain(ItemStack canister, SlotAccess cursorAccess) {
        GooType dominant = dominantType(canister);
        if (dominant == null) { return false; }

        long extracted = extractCapped(canister, dominant, ContainerCapacity.BLOB_CAP);
        if (extracted <= 0) { return false; }

        cursorAccess.set(BlobStacks.createForOutput(dominant, extracted));
        return true;
    }

    // --- Shared helpers ---

    /**
     * Returns the dominant goo type in the canister, or null if empty.
     *
     * @param canister the canister item stack
     * @return the dominant goo type, or null
     */
    private static @Nullable GooType dominantType(ItemStack canister) {
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        if (content.isEmpty()) { return null; }
        return content.getGooType();
    }

    /**
     * Extracts up to cap mB of the given type, capped by available volume.
     *
     * @param canister the canister item stack
     * @param type     the goo type to extract
     * @param cap      the maximum volume to extract
     * @return the amount actually extracted
     */
    private static long extractCapped(ItemStack canister, GooType type, long cap) {
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        long available = (content.getGooType() == type) ? content.amount() : 0;
        return CanisterItem.removeGoo(canister, type, Math.min(available, cap));
    }

    // --- Gasket mutual exclusivity ---

    /**
     * Pops any gaskets on the attachment target below when a canister is placed
     * above it. E.g. hub intake gasket pops when a canister is copper-fitted on top.
     *
     * @param level        the current level
     * @param canisterPos  the canister block position
     */
    static void popConflictingGaskets(Level level, BlockPos canisterPos) {
        BlockPos belowPos = canisterPos.below();
        BlockEntity belowBe = level.getBlockEntity(belowPos);
        if (belowBe instanceof IGasketHolder holder) {
            popReceiverGasket(level, belowPos, holder);
        }
    }

    /**
     * Pops the RECEIVER gasket on the top face of the block below, if present.
     *
     * @param level  the current level
     * @param pos    the gasket holder block position
     * @param holder the gasket holder
     */
    private static void popReceiverGasket(Level level, BlockPos pos, IGasketHolder holder) {
        java.util.UUID gasketId = holder.getGasketId(GasketRole.RECEIVER);
        if (gasketId != null) {
            GasketInstallation.popGasket(level, pos, gasketId);
            holder.clearGasket(GasketRole.RECEIVER);
        }
    }
}
