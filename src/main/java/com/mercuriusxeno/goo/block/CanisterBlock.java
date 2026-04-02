package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.BucketOfGooItem;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GasketRegionResolver;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Multi-canister block: holds up to 9 canisters in a 3x3 grid within
 * one block space. Click-targeted interactions let the player insert,
 * extract, and remove individual canisters by aiming at specific cells.
 */
public class CanisterBlock extends BaseEntityBlock {

    /** Codec for serialization. */
    public static final MapCodec<CanisterBlock> CODEC = simpleCodec(CanisterBlock::new);

    /** Number of slots in the 3x3 grid. */
    public static final int SLOT_COUNT = CanisterSlotLayout.SLOT_COUNT;

    /** Index of the center slot in the 3x3 grid (fallback for out-of-range lookups). */
    public static final int CENTER_SLOT = 4;

    /** Canister body height in pixels. */
    private static final int BODY_HEIGHT = 12;

    /** Height of a gasket cap in pixels (top and bottom). */
    private static final int GASKET_HEIGHT = 1;

    /** Slot center positions in pixel coordinates, delegated from layout utility. */
    public static final float[][] SLOT_CENTERS = CanisterSlotLayout.SLOT_CENTERS;

    /** Pre-computed VoxelShapes for each slot (4x12x4 at grid positions). */
    private static final VoxelShape[] SLOT_SHAPES = new VoxelShape[SLOT_COUNT];

    /** Pre-computed upper gasket shapes for each slot (1px cap at top). */
    private static final VoxelShape[] UPPER_GASKET_SHAPES = new VoxelShape[SLOT_COUNT];

    /** Pre-computed lower gasket shapes for each slot (1px base at bottom). */
    private static final VoxelShape[] LOWER_GASKET_SHAPES = new VoxelShape[SLOT_COUNT];

    /** Half-width of a canister slot in pixels (each slot is 4px wide). */
    private static final float SLOT_HALF_WIDTH = 2;

    static {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            buildSlotShapes(slot);
        }
    }

    /** Builds the body, upper gasket, and lower gasket VoxelShapes for a single slot. */
    private static void buildSlotShapes(int slot) {
        float centerX = SLOT_CENTERS[slot][0];
        float centerZ = SLOT_CENTERS[slot][1];
        float minX = centerX - SLOT_HALF_WIDTH;
        float minZ = centerZ - SLOT_HALF_WIDTH;
        float maxX = centerX + SLOT_HALF_WIDTH;
        float maxZ = centerZ + SLOT_HALF_WIDTH;

        SLOT_SHAPES[slot] = Block.box(minX, 0, minZ, maxX, BODY_HEIGHT, maxZ);
        UPPER_GASKET_SHAPES[slot] = Block.box(
                minX, BODY_HEIGHT - GASKET_HEIGHT, minZ, maxX, BODY_HEIGHT, maxZ);
        LOWER_GASKET_SHAPES[slot] = Block.box(minX, 0, minZ, maxX, GASKET_HEIGHT, maxZ);
    }

    /** Creates a new canister block with the given properties. */
    public CanisterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Returns the VoxelShape for a specific slot index. */
    public static VoxelShape slotShape(int slot) {
        return (slot < 0 || slot >= SLOT_COUNT) ? SLOT_SHAPES[CENTER_SLOT] : SLOT_SHAPES[slot];
    }

    /** Returns the upper gasket (receiver/cap) VoxelShape for a slot. */
    public static VoxelShape upperGasketShape(int slot) {
        return (slot < 0 || slot >= SLOT_COUNT) ? UPPER_GASKET_SHAPES[CENTER_SLOT] : UPPER_GASKET_SHAPES[slot];
    }

    /** Returns the lower gasket (transmitter/base) VoxelShape for a slot. */
    public static VoxelShape lowerGasketShape(int slot) {
        return (slot < 0 || slot >= SLOT_COUNT) ? LOWER_GASKET_SHAPES[CENTER_SLOT] : LOWER_GASKET_SHAPES[slot];
    }

    /**
     * Selection shape: only occupied slots are targetable.
     * Custom outline rendering is handled by {@link com.mercuriusxeno.goo.client.SlotOutlineRenderer}.
     */
    @Override
    protected @NonNull VoxelShape getShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return getCachedShapeOrDefault(level, pos);
    }

    /** Returns the block entity's cached shape, or the center slot shape if no entity exists. */
    private static VoxelShape getCachedShapeOrDefault(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CanisterBlockEntity canister
            ? canister.getCachedShape() : SLOT_SHAPES[CENTER_SLOT];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new CanisterBlockEntity(pos, state);
    }

    /** Registers the server-side tick dispatcher for per-slot gasket push. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, GooBlockEntities.CANISTER.get(), CanisterBlockEntity::serverTick);
    }

    // --- Hit detection ---

    /**
     * Determines which slot the player clicked, using nearest-center detection.
     * Returns -1 if no slot is within threshold.
     */
    public static int hitSlot(BlockHitResult hit, BlockPos pos) {
        double hitPixelX = (hit.getLocation().x - pos.getX()) * ShapeHitCheck.PIXELS_PER_BLOCK;
        double hitPixelZ = (hit.getLocation().z - pos.getZ()) * ShapeHitCheck.PIXELS_PER_BLOCK;
        return CanisterSlotLayout.nearestSlot((float) hitPixelX, (float) hitPixelZ);
    }

    /** Returns true if the slot index is within the valid range [0, SLOT_COUNT). */
    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    // --- Interactions ---

    /** Result of a successful goo extraction from a canister slot. */
    private record ExtractedGoo(GooType type, long volume) {}

    /** Classifies the held item and dispatches to the appropriate canister interaction handler. */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                CanisterBlockEntity.class, t -> t == null, this::dispatchCanister);
    }

    /** Dispatches a validated interaction to the appropriate handler method. */
    private InteractionResult dispatchCanister(
            GooInteractionType interaction, CanisterBlockEntity canister, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        return switch (interaction) {
            case TUNER_PASS       -> throw new IllegalStateException("TUNER_PASS handled in validate");
            case CANISTER_INSERT  -> handleCanisterInsert(canister, hitResult, pos, stack, player, level);
            case BLOB_INSERT      -> handleBlobInsert(canister, hitResult, pos, stack, player, level);
            case BUCKET_INSERT    -> handleBucketInsert(canister, hitResult, pos, stack, player, hand, level);
            case BUCKET_EXTRACT   -> handleBucketExtract(canister, hitResult, pos, stack, player, level);
        };
    }

    /** Empty-hand interaction: sneak removes per-slot gasket, otherwise removes canister. */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {

        int slot = hitSlot(hitResult, pos);
        if (slot < 0) return InteractionResult.PASS;
        InteractionResult earlyOut = GooBlockInteraction.validateEmptyHand(level, pos, player);
        if (earlyOut != null) return earlyOut;
        if (!(level.getBlockEntity(pos) instanceof CanisterBlockEntity canister)) return InteractionResult.PASS;

        // Sneak + empty hand → remove per-slot gasket if present
        if (player.isShiftKeyDown()) {
            return handleSlotGasketRemove(canister, slot, hitResult, pos, level);
        }

        return handleCanisterRemove(canister, slot, player, level, pos);
    }

    /** Removes the gasket on the targeted face of a canister slot, if installed. */
    private static InteractionResult handleSlotGasketRemove(
            CanisterBlockEntity canister, int slot,
            BlockHitResult hitResult, BlockPos pos, Level level) {
        double localY = hitResult.getLocation().y - pos.getY();
        GasketRole role = GasketRegionResolver.resolveCanisterSlotRole(localY, 0.0, 1.0);
        CanisterMetadata meta = canister.getSlotMetadata(slot);
        java.util.UUID gasketId = role == GasketRole.RECEIVER
                ? meta.topGasketId() : meta.bottomGasketId();
        if (gasketId == null) return InteractionResult.PASS;

        GasketInstallation.popGasket(level, pos, gasketId);
        CanisterMetadata cleared = role == GasketRole.RECEIVER
                ? meta.withoutTopGasket() : meta.withoutBottomGasket();
        canister.setSlotMetadata(slot, cleared);
        return InteractionResult.SUCCESS;
    }

    // --- Handlers ---

    /** Inserts a canister item into the best empty slot resolved from the hit result. */
    private InteractionResult handleCanisterInsert(
            CanisterBlockEntity canister, BlockHitResult hitResult, BlockPos pos,
            ItemStack stack, Player player, Level level) {
        if (!tryInsertCanister(canister, hitResult, pos, stack, player.isCreative())) {
            return InteractionResult.PASS;
        }
        stack.consume(1, player);
        InteractionCooldown.markInteraction(player.getUUID(), level.getGameTime());
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to insert a canister item into the resolved slot.
     *
     * @param canister      the canister block entity
     * @param hitResult     the block hit result for slot targeting
     * @param pos           the block position
     * @param stack         the canister item stack
     * @param stripGaskets  true to clear gasket UUIDs (creative-mode duplication)
     * @return true if the canister was inserted
     */
    private static boolean tryInsertCanister(
            CanisterBlockEntity canister, BlockHitResult hitResult,
            BlockPos pos, ItemStack stack, boolean stripGaskets) {
        int slot = CanisterItem.resolveInsertionSlot(
                hitResult.getLocation(), pos, hitResult.getDirection(), canister);
        return slot >= 0 && canister.insertCanister(slot, stack, stripGaskets);
    }

    /** Removes a canister from the targeted slot, dropping the block if it was the last. */
    private InteractionResult handleCanisterRemove(
            CanisterBlockEntity canister, int slot, Player player, Level level, BlockPos pos) {
        ItemStack removed = canister.removeCanister(slot);
        if (removed.isEmpty()) return InteractionResult.PASS;

        PlayerUtils.addOrDrop(player, removed);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        InteractionCooldown.markInteraction(player.getUUID(), level.getGameTime());
        removeBlockIfEmpty(level, canister, pos);
        return InteractionResult.SUCCESS;
    }

    /** Removes the canister block from the world if no canisters remain in any slot. */
    private static void removeBlockIfEmpty(Level level, CanisterBlockEntity canister, BlockPos pos) {
        if (!canister.hasAnyCanister()) {
            level.removeBlock(pos, false);
        }
    }

    /** Inserts goo from a blob or omniblob item into the first accepting slot. */
    private InteractionResult handleBlobInsert(
            CanisterBlockEntity canister, BlockHitResult hitResult, BlockPos pos,
            ItemStack stack, Player player, Level level) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) return InteractionResult.PASS;
        long accepted = tryInsertBlobGoo(canister, hitSlot(hitResult, pos), type, BlobStacks.volumeOf(stack));
        if (accepted <= 0) return InteractionResult.PASS;

        BlobStacks.deplete(stack, accepted, player);
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to pour goo from a blob/omniblob into an accepting slot.
     *
     * @param canister the canister block entity
     * @param hitSlot  the slot the player targeted, or -1
     * @param type     the goo type to insert
     * @param volume   the volume in microblobs to insert
     * @return accepted volume in microblobs, or 0 if nothing was inserted
     */
    private static long tryInsertBlobGoo(
            CanisterBlockEntity canister, int hitSlot,
            GooType type, long volume) {
        int slot = GooBlockInteraction.findSlot(hitSlot, CanisterBlockEntity.MAX_SLOTS, canister::canAccept);
        if (slot < 0) return 0;
        return canister.insertGoo(slot, type, volume);
    }

    /** Pours goo from a bucket of goo into matching canister slots. */
    private InteractionResult handleBucketInsert(
            CanisterBlockEntity canister, BlockHitResult hitResult, BlockPos pos,
            ItemStack stack, Player player, InteractionHand hand, Level level) {
        GooContents updatedContents = tryPourBucket(
                canister, hitSlot(hitResult, pos), BucketOfGooItem.getContents(stack));
        if (updatedContents == null) return InteractionResult.PASS;

        BucketOfGooItem.setOrRevert(stack, updatedContents, player, hand);
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to pour a bucket's goo contents into accepting canister slots.
     *
     * @param canister  the canister block entity
     * @param hitSlot   the slot the player targeted, or -1
     * @param bucketGoo the bucket's goo contents
     * @return updated goo contents after pouring, or null if nothing was inserted
     */
    private static @Nullable GooContents tryPourBucket(
            CanisterBlockEntity canister, int hitSlot, GooContents bucketGoo) {
        if (bucketGoo.isEmpty()) return null;

        boolean inserted = false;
        for (var entry : bucketGoo.getAll().entrySet()) {
            GooType type = entry.getKey();
            long volume = entry.getValue();
            int slot = GooBlockInteraction.findSlot(hitSlot, CanisterBlockEntity.MAX_SLOTS, canister::canAccept);
            if (slot < 0) continue;
            long accepted = canister.insertGoo(slot, type, volume);
            if (accepted > 0) {
                bucketGoo = bucketGoo.withRemoved(type, accepted);
                inserted = true;
            }
        }
        return inserted ? bucketGoo : null;
    }

    /** Extracts goo from the first non-empty slot into an empty bucket. */
    private InteractionResult handleBucketExtract(
            CanisterBlockEntity canister, BlockHitResult hitResult, BlockPos pos,
            ItemStack stack, Player player, Level level) {
        ExtractedGoo extracted = tryExtractGoo(canister, hitSlot(hitResult, pos));
        if (extracted == null) return InteractionResult.PASS;

        ItemStack filledBucket = BucketOfGooItem.createWithGoo(extracted.type(), extracted.volume());
        stack.shrink(1);
        PlayerUtils.addOrDrop(player, filledBucket);
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to extract goo from the targeted or first filled canister slot.
     *
     * @param canister the canister block entity
     * @param hitSlot  the slot the player targeted, or -1
     * @return extracted goo type and volume, or null if nothing could be extracted
     */
    private static @Nullable ExtractedGoo tryExtractGoo(
            CanisterBlockEntity canister, int hitSlot) {
        int slot = GooBlockInteraction.findSlot(hitSlot, CanisterBlockEntity.MAX_SLOTS, i -> !canister.getSlotGooContents(i).isEmpty());
        if (slot < 0) return null;

        GooContents slotGoo = canister.getSlotGooContents(slot);
        GooType type = slotGoo.largestType();
        if (type == null) return null;
        long extracted = canister.extractGoo(slot, type, slotGoo.getVolume(type));
        if (extracted <= 0) return null;
        return new ExtractedGoo(type, extracted);
    }


}
