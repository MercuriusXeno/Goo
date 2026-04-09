package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.fluid.BucketOfGooItem;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Bucket and canister fluid-transfer handlers for vat interactions.
 * Extracted from VatInteractionHandler to keep method counts under threshold.
 */
final class VatFluidInteraction {

    /** Fills an empty bucket with up to 8 blobs (8,000 mB) of the dominant goo type from the vat. */
    private static final long BUCKET_FILL_AMOUNT = 8 * BlobStacks.MB_PER_BLOB;

    private VatFluidInteraction() { }

    /**
     * Routes empty-bucket, filled-bucket, and canister interactions.
     *
     * @param vat    the vat block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @param hand   the hand used
     * @return the interaction result, or null if no match
     */
    @Nullable
    static InteractionResult dispatchFluidContainers(
            VatBlockEntity vat, ItemStack stack,
            Player player, InteractionHand hand) {
        InteractionResult bucket = dispatchBuckets(vat, stack, player, hand);
        if (bucket != null) { return bucket; }
        if (stack.getItem() instanceof CanisterItem) {
            return handleCanisterInteraction(vat, stack);
        }
        return null;
    }

    /**
     * Routes empty-bucket and filled-bucket interactions.
     *
     * @param vat    the vat block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @param hand   the hand used
     * @return the interaction result, or null if no match
     */
    @Nullable
    private static InteractionResult dispatchBuckets(
            VatBlockEntity vat, ItemStack stack,
            Player player, InteractionHand hand) {
        if (stack.is(Items.BUCKET)) {
            return handleBucketFill(vat, stack, player);
        }
        if (stack.getItem() instanceof BucketOfGooItem) {
            return handleBucketPour(vat, stack, player, hand);
        }
        return null;
    }

    // --- Bucket handlers ---

    /** Fills an empty bucket with the dominant goo type from the vat.
     *
     * @param vat    the vat block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleBucketFill(VatBlockEntity vat, ItemStack stack, Player player) {
        GooType dominant = extractableDominant(vat);
        if (dominant == null) { return InteractionResult.PASS; }
        long available = vat.getContents().getVolume(dominant);
        long extracted = vat.extractGoo(dominant, Math.min(available, BUCKET_FILL_AMOUNT));
        if (extracted <= 0) { return InteractionResult.PASS; }
        swapBucketForFilled(stack, player, dominant, extracted);
        return InteractionResult.SUCCESS;
    }

    /**
     * Consumes the empty bucket and gives the player a filled goo bucket.
     *
     * @param stack  the empty bucket stack to consume
     * @param player the interacting player
     * @param type   the goo type to fill
     * @param volume the volume in microblobs
     */
    private static void swapBucketForFilled(
            ItemStack stack, Player player, GooType type, long volume) {
        stack.shrink(1);
        PlayerUtils.addOrDrop(player, BucketOfGooItem.createWithGoo(type, volume));
    }

    /** Pours a filled goo bucket into the vat, tracking partial fills.
     *
     * @param vat    the vat block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @param hand   the hand used
     * @return the interaction result
     */
    private static InteractionResult handleBucketPour(
            VatBlockEntity vat, ItemStack stack, Player player, InteractionHand hand) {
        GooContents bucketGoo = BucketOfGooItem.getContents(stack);
        if (bucketGoo.isEmpty()) { return InteractionResult.PASS; }
        if (!vat.canAccept()) { return InteractionResult.PASS; }
        GooContents remainder = pourAllTypes(vat, bucketGoo);
        if (remainder == bucketGoo) { return InteractionResult.PASS; }
        BucketOfGooItem.setOrRevert(stack, remainder, player, hand);
        return InteractionResult.SUCCESS;
    }

    /**
     * Inserts each goo type from the source contents into the vat, returning the remainder.
     *
     * @param vat    the vat block entity
     * @param source the goo contents to pour
     * @return the remaining goo contents after insertion
     */
    private static GooContents pourAllTypes(VatBlockEntity vat, GooContents source) {
        GooContents remainder = source;
        for (Map.Entry<GooType, Long> entry : source.getAll().entrySet()) {
            long accepted = vat.insertGoo(entry.getKey(), entry.getValue());
            if (accepted > 0) {
                remainder = remainder.withRemoved(entry.getKey(), accepted);
            }
        }
        return remainder;
    }

    // --- Canister handlers ---

    /** Routes canister-vat interaction: dump if canister has fluid, drain if empty.
     *
     * @param vat   the vat block entity
     * @param stack the item stack
     * @return the interaction result
     */
    private static InteractionResult handleCanisterInteraction(VatBlockEntity vat, ItemStack stack) {
        GooContents canisterContents = CanisterItem.getGooContents(stack);
        if (!canisterContents.isEmpty()) {
            return handleCanisterDump(vat, stack, canisterContents);
        }
        return handleCanisterDrain(vat, stack);
    }

    /** Dumps all canister goo into the vat, removing accepted amounts from the canister.
     *
     * @param vat              the vat block entity
     * @param stack            the item stack
     * @param canisterContents the canister goo contents
     * @return the interaction result
     */
    private static InteractionResult handleCanisterDump(
            VatBlockEntity vat, ItemStack stack, GooContents canisterContents) {
        if (!vat.canAccept()) { return InteractionResult.PASS; }
        boolean inserted = transferAllTypesToVat(vat, stack, canisterContents);
        return inserted ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Iterates each goo type in the canister and inserts as much as the vat accepts.
     *
     * @param vat      the vat block entity
     * @param stack    the canister item stack
     * @param contents the canister goo contents to transfer
     * @return true if any goo was inserted
     */
    private static boolean transferAllTypesToVat(
            VatBlockEntity vat, ItemStack stack, GooContents contents) {
        boolean inserted = false;
        for (Map.Entry<GooType, Long> entry : contents.getAll().entrySet()) {
            long accepted = vat.insertGoo(entry.getKey(), entry.getValue());
            if (accepted <= 0) { continue; }
            CanisterItem.removeGoo(stack, entry.getKey(), accepted);
            inserted = true;
        }
        return inserted;
    }

    /** Drains the vat's dominant type into an empty canister, up to canister capacity.
     *
     * @param vat   the vat block entity
     * @param stack the item stack
     * @return the result
     */
    private static InteractionResult handleCanisterDrain(VatBlockEntity vat, ItemStack stack) {
        GooType dominant = extractableDominant(vat);
        if (dominant == null) { return InteractionResult.PASS; }
        long extracted = drainDominantForCanister(vat, stack, dominant);
        if (extracted <= 0) { return InteractionResult.PASS; }
        CanisterItem.addGoo(stack, dominant, extracted);
        return InteractionResult.SUCCESS;
    }

    /**
     * Extracts as much of the dominant type as the canister can hold.
     *
     * @param vat      the vat block entity
     * @param stack    the canister item stack
     * @param dominant the goo type to extract
     * @return the volume actually extracted in microblobs
     */
    private static long drainDominantForCanister(
            VatBlockEntity vat, ItemStack stack, GooType dominant) {
        long space = canisterRemainingSpace(stack);
        if (space <= 0) { return 0; }
        long available = vat.getContents().getVolume(dominant);
        return vat.extractGoo(dominant, Math.min(available, space));
    }

    // --- Helpers ---

    /**
     * Returns the dominant goo type if the vat is non-empty, or null otherwise.
     *
     * @param vat the vat block entity
     * @return the dominant goo type, or null if the vat is empty
     */
    @Nullable
    static GooType extractableDominant(VatBlockEntity vat) {
        if (vat.isEmpty()) { return null; }
        return vat.getDominantType();
    }

    /**
     * Returns the remaining fluid capacity of the canister stack.
     *
     * @param stack the canister item stack
     * @return remaining capacity in microblobs
     */
    private static long canisterRemainingSpace(ItemStack stack) {
        long capacity = ContainerCapacity.canisterCapacity(
            GooEnchantments.getCompressionLevel(stack));
        return capacity - CanisterItem.getGooContents(stack).totalVolume();
    }
}
