package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.fluid.BucketOfGooItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registers CauldronInteraction handlers for goo blobs and buckets.
 * Pouring goo into an empty cauldron replaces it with a goo fluid block.
 */
public final class GooCauldronInteractions {

    /** Microblobs in one blob (matches BucketOfGooItem). */
    private static final long MICROBLOBS_PER_BLOB = BucketOfGooItem.MICROBLOBS_PER_BLOB;

    /** Maximum blobs in a full fluid block. */
    private static final int BLOBS_PER_BLOCK = BucketOfGooItem.BLOBS_PER_BLOCK;

    private GooCauldronInteractions() {}

    /** Registers all goo-to-cauldron interactions on the EMPTY cauldron map. */
    public static void register() {
        for (GooType type : GooType.values()) {
            CauldronInteractions.EMPTY.put(GooItems.BLOBS.get(type).get(), GooCauldronInteractions::pourBlob);
            CauldronInteractions.EMPTY.put(GooItems.OMNIBLOBS.get(type).get(), GooCauldronInteractions::pourBlob);
        }
        CauldronInteractions.EMPTY.put(GooItems.BUCKET_OF_GOO.get(), GooCauldronInteractions::pourBucket);
    }

    /** Pours a goo blob or omniblob into an empty cauldron, replacing it with a fluid block.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @param hand   the hand used
     * @param stack  the item stack
     * @return the interaction result
     */
    private static InteractionResult pourBlob(
            BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, ItemStack stack) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) { return InteractionResult.TRY_WITH_EMPTY_HAND; }
        long volume = BlobStacks.volumeOf(stack);
        if (volume <= 0) { return InteractionResult.TRY_WITH_EMPTY_HAND; }

        if (!level.isClientSide()) {
            pourBlobServerSide(level, pos, type, volume, stack, player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Places the goo fluid and consumes the blob stack on the server side.
     * @param level the current level
     * @param pos the cauldron block position
     * @param type the goo type to place
     * @param volume volume in microblobs
     * @param stack the blob item stack to consume
     * @param player the interacting player (creative skips consumption)
     */
    private static void pourBlobServerSide(Level level, BlockPos pos, GooType type,
            long volume, ItemStack stack, Player player) {
        placeGooFluid(level, pos, type, volume);
        if (!player.isCreative()) {
            stack.consume(1, player);
        }
    }

    /** Pours a goo bucket into an empty cauldron, replacing it with a fluid block.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @param hand   the hand used
     * @param stack  the item stack
     * @return the interaction result
     */
    private static InteractionResult pourBucket(
            BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, ItemStack stack) {
        GooContents contents = BucketOfGooItem.getContents(stack);
        if (!contents.isSingleType()) { return InteractionResult.TRY_WITH_EMPTY_HAND; }
        GooType type = contents.getSingleType();
        long volume = contents.getVolume(type);
        if (volume < MICROBLOBS_PER_BLOB) { return InteractionResult.TRY_WITH_EMPTY_HAND; }
        pourBucketServerSide(level, pos, type, volume, stack, player, hand);
        return InteractionResult.SUCCESS;
    }

    /**
     * Executes the server-side bucket pour: places goo fluid and empties the bucket.
     * @param level the current level
     * @param pos the cauldron block position
     * @param type the goo type to place
     * @param volume volume in microblobs
     * @param stack the bucket item stack to empty
     * @param player the interacting player
     * @param hand the hand holding the bucket
     */
    private static void pourBucketServerSide(Level level, BlockPos pos, GooType type,
            long volume, ItemStack stack, Player player, InteractionHand hand) {
        if (level.isClientSide()) { return; }
        placeGooFluid(level, pos, type, volume);
        BucketOfGooItem.setOrRevert(stack, GooContents.EMPTY, player, hand);
    }

    /** Replaces the block at pos with a goo fluid block at the appropriate level.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param type   the goo type
     * @param volume volume in microblobs
     */
    private static void placeGooFluid(Level level, BlockPos pos, GooType type, long volume) {
        int blobs = (int) Math.min(volume / MICROBLOBS_PER_BLOB, BLOBS_PER_BLOCK);
        if (blobs <= 0) { return; }
        int fluidLevel = BLOBS_PER_BLOCK - blobs;
        BlockState fluidState = GooBlocks.FLUID_BLOCKS.get(type).get()
            .defaultBlockState().setValue(LiquidBlock.LEVEL, fluidLevel);
        level.setBlockAndUpdate(pos, fluidState);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}
