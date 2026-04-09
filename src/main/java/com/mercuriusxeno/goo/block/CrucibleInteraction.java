package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.fluid.BucketOfGooItem;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map;

/**
 * Static helpers for crucible right-click interactions: fuel insertion,
 * bucket pour/fill, canister collection, blob insertion, fuel removal,
 * and goo extraction. Keeps framework overrides in CrucibleBlock.
 */
final class CrucibleInteraction {

    private CrucibleInteraction() { }

    /** Returns true if this item type would be handled by the crucible on the server.
     *
     * @param stack the item stack
     * @return true if the condition is met
     */
    static boolean wouldHandleItem(ItemStack stack) {
        return isFuelItem(stack)
            || stack.is(Items.BUCKET)
            || isGooCarrier(stack);
    }

    /**
     * Returns true if the stack holds a goo carrier item (bucket, canister, blob, or omniblob).
     *
     * @param stack the item stack to test
     * @return true if the item is a goo carrier
     */
    static boolean isGooCarrier(ItemStack stack) {
        return stack.getItem() instanceof BucketOfGooItem
            || stack.getItem() instanceof CanisterItem
            || stack.getItem() instanceof GooBlobItem
            || stack.getItem() instanceof GooOmniblobItem;
    }

    /** Returns true if the item can be inserted as crucible fuel.
     *
     * @param stack the item stack
     * @return true if the stack is a fuel item
     */
    static boolean isFuelItem(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD) || stack.getItem() instanceof DepletedBlazeRodItem;
    }

    /** Inserts a fuel rod only if the platform is empty.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return true if fuel was inserted
     */
    static boolean tryInsertFuel(ItemStack stack, CrucibleBlockEntity crucible,
            Player player) {
        if (!isFuelItem(stack)) { return false; }
        if (crucible.hasFuel()) { return false; }
        crucible.addFuel(stack);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    /** Pours a filled goo bucket into the crucible reservoir, reverting to vanilla bucket.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @param hand     the hand used
     * @return true if the bucket was poured
     */
    static boolean tryPourBucket(ItemStack stack, CrucibleBlockEntity crucible,
            Player player, InteractionHand hand) {
        if (!(stack.getItem() instanceof BucketOfGooItem)) { return false; }
        GooContents contents = BucketOfGooItem.getContents(stack);
        if (contents.isEmpty()) { return false; }

        CrucibleInsertion.insertGooContents(crucible, contents);
        BucketOfGooItem.setOrRevert(stack, GooContents.EMPTY, player, hand);
        return true;
    }

    /** Fills a vanilla bucket with the entire reservoir contents as slurry.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return true if the bucket was filled
     */
    static boolean tryFillBucket(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        if (!stack.is(Items.BUCKET)) { return false; }
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) { return false; }

        ItemStack filledBucket = createBucketFromReservoir(res);
        crucible.drainReservoir();
        replaceBucketInHand(stack, filledBucket, player);
        return true;
    }

    /** Creates a bucket of goo item from the given reservoir contents.
     *
     * @param contents the goo contents
     * @return the filled bucket item stack
     */
    static ItemStack createBucketFromReservoir(GooContents contents) {
        ItemStack bucket = new ItemStack(GooItems.BUCKET_OF_GOO.get());
        BucketOfGooItem.setContents(bucket, contents);
        return bucket;
    }

    /** Replaces a single bucket in the player's hand with the filled result.
     *
     * @param bucket the empty bucket stack
     * @param filled the filled bucket stack
     * @param player the interacting player
     */
    static void replaceBucketInHand(ItemStack bucket, ItemStack filled, Player player) {
        bucket.shrink(1);
        PlayerUtils.addOrDrop(player, filled);
    }

    /** Collects matching goo type from the reservoir into a canister.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @return true if goo was collected
     */
    static boolean tryCollectWithCanister(ItemStack stack, CrucibleBlockEntity crucible) {
        if (!(stack.getItem() instanceof CanisterItem)) { return false; }
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) { return false; }
        GooType type = res.largestType();
        if (type == null) { return false; }
        long added = CanisterItem.addGoo(stack, type, res.getVolume(type));
        if (added <= 0) { return false; }
        crucible.extractGoo(type, added);
        return true;
    }

    /** Inserts a goo blob or omniblob directly into the reservoir (bypass, no fuel needed).
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return true if the blob was inserted
     */
    static boolean tryInsertBlob(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) { return false; }
        long volume = BlobStacks.volumeOf(stack);
        if (volume <= 0) { return false; }
        crucible.insertGoo(type, volume);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    /** Removes the fuel rod from the crucible and gives it to the player.
     *
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return the result
     */
    static InteractionResult tryRemoveFuelRod(CrucibleBlockEntity crucible, Player player) {
        ItemStack rod = crucible.removeFuelRod();
        if (rod.isEmpty()) { return InteractionResult.PASS; }
        PlayerUtils.addOrDrop(player, rod);
        return InteractionResult.SUCCESS;
    }

    /** Extracts the entire reservoir as one item per goo type (blob stack or omniblob).
     *
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return the result
     */
    static InteractionResult tryExtractGoo(CrucibleBlockEntity crucible, Player player) {
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) { return InteractionResult.PASS; }

        for (Map.Entry<GooType, Long> entry : res.getAll().entrySet()) {
            BlobStacks.mergeIntoInventory(player, entry.getKey(), entry.getValue());
        }
        crucible.drainReservoir();
        return InteractionResult.SUCCESS;
    }
}
