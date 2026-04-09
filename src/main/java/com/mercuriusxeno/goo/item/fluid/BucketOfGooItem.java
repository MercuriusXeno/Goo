package com.mercuriusxeno.goo.item.fluid;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.item.IGooItemInteraction;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.NonNull;

/**
 * A bucket that holds arbitrary amounts of multiple goo types.
 * When containing one type, named by that type (e.g. "Metal Bucket of Goo").
 * When containing 2+ types, called "Slurry".
 * Supports in-world placement and pickup of goo fluid blocks.
 */
public class BucketOfGooItem extends Item implements IGooItemInteraction {

    /** Block update flags: notify clients + update neighbors. */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /** Default sound volume for bucket interactions. */
    private static final float SOUND_VOLUME = 1.0f;
    /** Default sound pitch for bucket interactions. */
    private static final float SOUND_PITCH = 1.0f;
    /** Translation key for the empty bucket name. */
    private static final String KEY_EMPTY = "item.goo.bucket_of_goo";
    /** Translation key for a single-type bucket name. */
    private static final String KEY_TYPED = "item.goo.bucket_of_goo.typed";
    /** Translation key for a mixed-type (slurry) bucket name. */
    private static final String KEY_SLURRY = "item.goo.bucket_of_goo.slurry";

    /** Microblobs in one blob. */
    public static final long MICROBLOBS_PER_BLOB = 1_000L;

    /** Maximum blobs that fit in a single fluid block (source level). */
    public static final int BLOBS_PER_BLOCK = 8;

    /** Microblobs in a full fluid block. */
    public static final long MICROBLOBS_PER_BLOCK = MICROBLOBS_PER_BLOB * BLOBS_PER_BLOCK;

    /**
     * Creates a new bucket of goo item.
     *
     * @param properties the item properties
     */
    public BucketOfGooItem(Properties properties) {
        super(properties);
    }

    /**
     * Returns the goo contents from the stack, or EMPTY if absent.
     *
     * @param stack the item stack
     * @return the goo contents, never null
     */
    public static GooContents getContents(ItemStack stack) {
        GooContents contents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /**
     * Sets the goo contents on the stack.
     *
     * @param stack    the item stack
     * @param contents the goo contents to set
     */
    public static void setContents(ItemStack stack, GooContents contents) {
        stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
    }

    /**
     * Sets contents, reverting to a vanilla bucket if empty.
     *
     * @param stack    the bucket item stack
     * @param contents the goo contents to set
     * @param player   the interacting player
     * @param hand     the hand used
     */
    public static void setOrRevert(ItemStack stack, GooContents contents,
            Player player, InteractionHand hand) {
        if (contents.isEmpty()) {
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        } else {
            setContents(stack, contents);
        }
    }

    /**
     * Creates a bucket ItemStack pre-loaded with a single goo type and volume.
     *
     * @param type   the goo type to fill with
     * @param volume the volume in microblobs
     * @return a new bucket item stack
     */
    public static ItemStack createWithGoo(GooType type, long volume) {
        ItemStack stack = new ItemStack(
            com.mercuriusxeno.goo.registry.GooItems.BUCKET_OF_GOO.get());
        setContents(stack, GooContents.EMPTY.withAdded(type, volume));
        return stack;
    }

    /**
     * Handles right-click: picks up or places goo fluid in the world.
     *
     * @param level  the current level
     * @param player the interacting player
     * @param hand   the hand used
     * @return the interaction result
     */
    @Override
    public @NonNull InteractionResult use(Level level, Player player, @NonNull InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return handleServerUse(level, player, hand);
    }

    /** Handles server-side right-click: ray traces and delegates to hit handler.
     *
     * @param level  the current level
     * @param player the interacting player
     * @param hand   the hand used
     * @return the interaction result
     */
    private InteractionResult handleServerUse(Level level, Player player, InteractionHand hand) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        GooContents contents = getContents(stack);
        return handleHit(level, player, hand, stack, contents, hit);
    }

    /**
     * Routes the hit to pickup or placement based on what was hit.
     *
     * @param level    the current level
     * @param player   the interacting player
     * @param hand     the hand used
     * @param stack    the bucket item stack
     * @param contents the current bucket goo contents
     * @param hit      the ray trace hit result
     * @return the interaction result
     */
    private InteractionResult handleHit(Level level, Player player,
            InteractionHand hand, ItemStack stack, GooContents contents,
            BlockHitResult hit) {
        BlockPos hitPos = hit.getBlockPos();
        FluidState fluidState = level.getFluidState(hitPos);
        GooType hitType = GooFluids.getTypeFromFluid(fluidState.getType());

        if (hitType != null) {
            return tryPickup(level, player, hitPos, fluidState, stack, contents, hitType);
        }
        BlockPos placePos = hitPos.relative(hit.getDirection());
        return tryPlace(level, player, hand, placePos, stack, contents);
    }

    /**
     * Picks up a goo fluid block, adding its volume to the bucket.
     * Removes the block from the world.
     *
     * @param level      the current level
     * @param player     the interacting player
     * @param pos        the block position of the fluid
     * @param fluidState the fluid state at the position
     * @param stack      the bucket item stack
     * @param contents   the current bucket goo contents
     * @param type       the goo type of the fluid
     * @return the interaction result
     */
    private InteractionResult tryPickup(Level level, Player player,
            BlockPos pos, FluidState fluidState, ItemStack stack,
            GooContents contents, GooType type) {
        int amount = fluidState.getAmount();
        long volumeGained = amount * MICROBLOBS_PER_BLOB;

        GooContents updated = contents.withAdded(type, volumeGained);
        setContents(stack, updated);
        level.removeBlock(pos, false);
        playPickupSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * Places goo from a single-type bucket onto the adjacent block face.
     * Slurry and sub-blob volumes cannot place.
     *
     * @param level    the current level
     * @param player   the interacting player
     * @param hand     the hand used
     * @param pos      the target block position
     * @param stack    the bucket item stack
     * @param contents the current bucket goo contents
     * @return the interaction result
     */
    private InteractionResult tryPlace(Level level, Player player,
            InteractionHand hand, BlockPos pos, ItemStack stack,
            GooContents contents) {
        if (!contents.isSingleType()) { return InteractionResult.PASS; }
        GooType type = contents.getSingleType();
        long volume = contents.getVolume(type);
        if (volume < MICROBLOBS_PER_BLOB) { return InteractionResult.PASS; }
        return executePlacement(level, player, hand, pos, stack, contents, type, volume);
    }

    /**
     * Calculates blob count, validates placement, and sets the fluid block.
     *
     * @param level    the current level
     * @param player   the interacting player
     * @param hand     the hand used
     * @param pos      the target block position
     * @param stack    the bucket item stack
     * @param contents the current bucket goo contents
     * @param type     the goo type being placed
     * @param volume   the available volume in microblobs
     * @return the interaction result
     */
    private InteractionResult executePlacement(Level level, Player player,
            InteractionHand hand, BlockPos pos, ItemStack stack,
            GooContents contents, GooType type, long volume) {
        if (!level.getBlockState(pos).canBeReplaced()) { return InteractionResult.PASS; }
        int blobsToPlace = Math.min((int) (volume / MICROBLOBS_PER_BLOB), BLOBS_PER_BLOCK);
        level.setBlock(pos, fluidBlockState(type, blobsToPlace), BLOCK_UPDATE_FLAGS);
        depleteAfterPlace(stack, contents, type, blobsToPlace, player, hand);
        playPlaceSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * Builds the LiquidBlock state for the given type and blob count.
     * Level 0 = source (8 blobs), level N = 8-N blobs.
     *
     * @param type      the goo type
     * @param blobCount the number of blobs to place (1-8)
     * @return the block state with correct fluid level
     */
    private BlockState fluidBlockState(GooType type, int blobCount) {
        BlockState base = GooBlocks.FLUID_BLOCKS.get(type).get().defaultBlockState();
        int blockLevel = placementLevel(blobCount);
        return base.setValue(LiquidBlock.LEVEL, blockLevel);
    }

    /**
     * Converts blob count to LiquidBlock LEVEL value.
     * 8 blobs = level 0 (source), 7 = level 1, ..., 1 = level 7.
     *
     * @param blobCount the number of blobs (1-8)
     * @return the LiquidBlock LEVEL value (0-7)
     */
    static int placementLevel(int blobCount) {
        return BLOBS_PER_BLOCK - blobCount;
    }

    /**
     * Deducts placed blobs from bucket contents, reverting to vanilla bucket if empty.
     *
     * @param stack      the bucket item stack
     * @param contents   the current bucket goo contents
     * @param type       the goo type that was placed
     * @param blobsPlaced number of blobs placed
     * @param player     the interacting player
     * @param hand       the hand used
     */
    private void depleteAfterPlace(ItemStack stack, GooContents contents,
            GooType type, int blobsPlaced, Player player, InteractionHand hand) {
        long cost = blobsPlaced * MICROBLOBS_PER_BLOB;
        GooContents remaining = contents.withRemoved(type, cost);
        setOrRevert(stack, remaining, player, hand);
    }

    /**
     * Plays the bucket-fill sound at the given position.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void playPickupSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BUCKET_FILL,
                SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
    }

    /**
     * Plays the bucket-empty sound at the given position.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void playPlaceSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
    }

    /**
     * Returns the display name based on contents: empty, single-type, or slurry.
     *
     * @param stack the item stack
     * @return the display name component
     */
    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        GooContents contents = getContents(stack);
        return buildName(contents);
    }

    /**
     * Builds the display name based on contents: empty, single-type, or slurry.
     *
     * @param contents the goo contents to name
     * @return the display name component
     */
    private Component buildName(GooContents contents) {
        if (contents.isEmpty()) {
            return Component.translatable(KEY_EMPTY);
        }
        if (contents.isSingleType()) {
            GooType type = contents.getSingleType();
            return Component.translatable(KEY_TYPED,
                Component.translatable(type.getTranslationKey()));
        }
        return Component.translatable(KEY_SLURRY);
    }

    /**
     * Returns BUCKET_INSERT so canister blocks route to bucket pour logic.
     *
     * @return the bucket insert interaction type
     */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.BUCKET_INSERT;
    }
}
