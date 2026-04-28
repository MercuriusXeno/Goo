package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.block.GooBlockInteraction;
import com.mercuriusxeno.goo.block.ShapeHitCheck;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
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

    /** Error message for TUNER_PASS reaching dispatch. */

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

    /**
     * Builds the body, upper gasket, and lower gasket VoxelShapes for a single slot.
     *
     * @param slot the slot index (0-8)
     */
    private static void buildSlotShapes(int slot) {
        float centerX = SLOT_CENTERS[slot][0];
        float centerZ = SLOT_CENTERS[slot][1];
        float minX = centerX - SLOT_HALF_WIDTH;
        float minZ = centerZ - SLOT_HALF_WIDTH;
        float maxX = centerX + SLOT_HALF_WIDTH;
        float maxZ = centerZ + SLOT_HALF_WIDTH;
        buildSlotBoxes(slot, minX, minZ, maxX, maxZ);
    }

    /**
     * Populates body, upper gasket, and lower gasket VoxelShapes for one slot.
     * @param slot the slot index (0-8)
     * @param minX left edge X in pixel coordinates
     * @param minZ front edge Z in pixel coordinates
     * @param maxX right edge X in pixel coordinates
     * @param maxZ back edge Z in pixel coordinates
     */
    private static void buildSlotBoxes(int slot, float minX, float minZ, float maxX, float maxZ) {
        SLOT_SHAPES[slot] = box(minX, 0, minZ, maxX, BODY_HEIGHT, maxZ);
        UPPER_GASKET_SHAPES[slot] = box(
                minX, BODY_HEIGHT - GASKET_HEIGHT, minZ, maxX, BODY_HEIGHT, maxZ);
        LOWER_GASKET_SHAPES[slot] = box(minX, 0, minZ, maxX, GASKET_HEIGHT, maxZ);
    }

    /**
     * Creates a new canister block with the given properties.
     *
     * @param properties the block properties
     */
    public CanisterBlock(Properties properties) {
        super(properties);
    }

    /** Canisters are never replaceable by fluids despite having partial shapes. */
    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    /**
     * Returns the codec for serialization.
     *
     * @return the canister block codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * Returns INVISIBLE render shape since the canister is rendered by BER.
     *
     * @param state the block state
     * @return {@link RenderShape#INVISIBLE}
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * Returns the VoxelShape for a specific slot index.
     *
     * @param slot the slot index (0-8)
     * @return the slot's body shape, or center slot shape if out of range
     */
    public static VoxelShape slotShape(int slot) {
        return (slot < 0 || slot >= SLOT_COUNT) ? SLOT_SHAPES[CENTER_SLOT] : SLOT_SHAPES[slot];
    }

    /**
     * Returns the upper gasket (receiver/cap) VoxelShape for a slot.
     *
     * @param slot the slot index (0-8)
     * @return the slot's upper gasket shape, or center slot shape if out of range
     */
    public static VoxelShape upperGasketShape(int slot) {
        return (slot < 0 || slot >= SLOT_COUNT) ? UPPER_GASKET_SHAPES[CENTER_SLOT] : UPPER_GASKET_SHAPES[slot];
    }

    /**
     * Returns the lower gasket (transmitter/base) VoxelShape for a slot.
     *
     * @param slot the slot index (0-8)
     * @return the slot's lower gasket shape, or center slot shape if out of range
     */
    public static VoxelShape lowerGasketShape(int slot) {
        return (slot < 0 || slot >= SLOT_COUNT) ? LOWER_GASKET_SHAPES[CENTER_SLOT] : LOWER_GASKET_SHAPES[slot];
    }

    /**
     * Selection shape: only occupied slots are targetable.
     * Custom outline rendering is handled by {@link com.mercuriusxeno.goo.client.SlotOutlineRenderer}.
     */
    /**
     * {@inheritDoc}
     *
     * @param state   the block state
     * @param level   the block getter
     * @param pos     the block position
     * @param context the collision context
     * @return the composite shape of occupied slots
     */
    @Override
    protected @NonNull VoxelShape getShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return getCachedShapeOrDefault(level, pos);
    }

    /**
     * Returns the block entity's cached shape, or the center slot shape if no entity exists.
     *
     * @param level the block getter
     * @param pos   the block position
     * @return the cached composite shape or a fallback
     */
    private static VoxelShape getCachedShapeOrDefault(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CanisterBlockEntity canister
            ? canister.containerState().compositeShape() : SLOT_SHAPES[CENTER_SLOT];
    }

    /**
     * Creates a new canister block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return a new canister block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new CanisterBlockEntity(pos, state);
    }

    /**
     * Registers the server-side tick dispatcher for per-slot gasket push.
     *
     * @param <T>   the block entity type parameter
     * @param level the current level
     * @param state the block state
     * @param type  the block entity type
     * @return the ticker, or null on the client
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) { return null; }
        return createTickerHelper(type, GooBlockEntities.CANISTER.get(), CanisterBlockEntity::serverTick);
    }

    // --- Hit detection ---

    /**
     * Determines which slot the player clicked, using nearest-center detection.
     * Returns -1 if no slot is within threshold.
     *
     * @param hit the block hit result from the interaction
     * @param pos the block position
     * @return the slot index (0-8), or -1 if no slot matched
     */
    public static int hitSlot(BlockHitResult hit, BlockPos pos) {
        double hitPixelX = (hit.getLocation().x - pos.getX()) * ShapeHitCheck.PIXELS_PER_BLOCK;
        double hitPixelZ = (hit.getLocation().z - pos.getZ()) * ShapeHitCheck.PIXELS_PER_BLOCK;
        return CanisterSlotLayout.nearestSlot((float) hitPixelX, (float) hitPixelZ);
    }

    /**
     * Returns true if the slot index is within the valid range [0, SLOT_COUNT).
     *
     * @param slot the slot index to check
     * @return true if the slot is valid
     */
    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    // --- Block lifecycle ---

    /** Drops all canisters as item entities when the block is broken. */
    @Override
    public @NonNull BlockState playerWillDestroy(
            @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof CanisterBlockEntity be) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack stack = be.containerState().getCanister(i);
                if (!stack.isEmpty()) {
                    popResource(level, pos, stack);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // --- Interactions ---

    /**
     * Shift+canister inserts into grid. Holding a canister without shift
     * picks up the targeted canister. Buckets do fluid transfer, blobs
     * insert goo.
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        // Shift+canister: insert canister item into grid
        if (player.isSecondaryUseActive() && stack.getItem() instanceof CanisterItem) {
            return handleCanisterGridInsert(level, pos, player, hitResult, stack);
        }

        // Holding a canister: pick up the targeted canister
        if (stack.getItem() instanceof CanisterItem) {
            return canisterBE(level, pos)
                    .map(be -> be.handleCanisterPickup(player, hitResult))
                    .orElse(InteractionResult.PASS);
        }

        // Fluid container interaction (buckets)
        InteractionResult fluidResult = canisterBE(level, pos)
                .map(be -> be.tryFluidInteraction(player, hand, hitResult))
                .orElse(null);
        if (fluidResult != null) { return fluidResult; }

        // Blob/goo insertion via standard dispatch
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                CanisterBlockEntity.class, t -> t == null,
                (interaction, canister, s, p, h, hit, bpos, lvl) ->
                    canister.dispatch(interaction, s, p, hit));
    }

    private static java.util.Optional<CanisterBlockEntity> canisterBE(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CanisterBlockEntity be
                ? java.util.Optional.of(be) : java.util.Optional.empty();
    }

    /**
     * Inserts a held canister item into the grid via shift+right-click.
     *
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @param stack     the canister item stack
     * @return the interaction result
     */
    private static InteractionResult handleCanisterGridInsert(
            Level level, BlockPos pos, Player player,
            BlockHitResult hitResult, ItemStack stack) {
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }
        return canisterBE(level, pos)
                .map(be -> be.dispatch(GooInteractionType.CANISTER_INSERT, stack, player, hitResult))
                .orElse(InteractionResult.PASS);
    }

    /** Empty-hand right-click picks up the targeted canister. */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {
        return canisterBE(level, pos)
                .map(be -> be.handleCanisterPickup(player, hitResult))
                .orElse(InteractionResult.PASS);
    }

}
