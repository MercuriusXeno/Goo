package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Hub block: central hub with 8 radial pipes for attaching canisters.
 * Canisters are inserted/removed via right-click on a specific slot.
 * Radially symmetrical: N, NE, E, SE, S, SW, W, NW.
 */
public class HubBlock extends BaseEntityBlock {

    /** Codec for serialization. */
    public static final MapCodec<HubBlock> CODEC = simpleCodec(HubBlock::new);

    /** Whether a choral gasket is installed on the hub's intake. */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");

    /** Base slab: full-width, 2px tall. */
    private static final VoxelShape BASE = box(0, 0, 0, 16, 2, 16);

    /** Central spindle: 2x13x2 column. */
    private static final VoxelShape SPINDLE = box(7, 2, 7, 9, 15, 9);

    /** Intake gasket at top of spindle. */
    private static final VoxelShape INTAKE = box(6, 15, 6, 10, 16, 10);

    /** Canister slot 0 (north). */
    private static final VoxelShape SLOT_0 = box(6, 2, 0, 10, 14, 4);

    /** Canister slot 1 (northeast). */
    private static final VoxelShape SLOT_1 = box(11, 2, 1, 15, 14, 5);

    /** Canister slot 2 (east). */
    private static final VoxelShape SLOT_2 = box(12, 2, 6, 16, 14, 10);

    /** Canister slot 3 (southeast). */
    private static final VoxelShape SLOT_3 = box(11, 2, 11, 15, 14, 15);

    /** Canister slot 4 (south). */
    private static final VoxelShape SLOT_4 = box(6, 2, 12, 10, 14, 16);

    /** Canister slot 5 (southwest). */
    private static final VoxelShape SLOT_5 = box(1, 2, 11, 5, 14, 15);

    /** Canister slot 6 (west). */
    private static final VoxelShape SLOT_6 = box(0, 2, 6, 4, 14, 10);

    /** Canister slot 7 (northwest). */
    private static final VoxelShape SLOT_7 = box(1, 2, 1, 5, 14, 5);

    /** Single cuboid enclosing all pipe geometry (y=14-15). Prevents collision jitter. */
    private static final VoxelShape PIPES = box(1, 14, 1, 15, 15, 15);

    /** Canister slot center positions in pixel coordinates (XZ only), indexed by slot. */
    public static final double[][] SLOT_CENTERS = {
        { 8.0,  2.0},  // slot 0 (N)
        {13.0,  3.0},  // slot 1 (NE)
        {14.0,  8.0},  // slot 2 (E)
        {13.0, 13.0},  // slot 3 (SE)
        { 8.0, 14.0},  // slot 4 (S)
        { 3.0, 13.0},  // slot 5 (SW)
        { 2.0,  8.0},  // slot 6 (W)
        { 3.0,  3.0},  // slot 7 (NW)
    };

    /** Max XZ pixel distance from a slot center to count as a hit (4px covers 4x4 canister). */
    private static final double MAX_SLOT_DISTANCE = 4.0;
    /** Sentinel value: no matching slot found. */

    /** Per-slot shapes, indexed 0-7. */
    private static final VoxelShape[] SLOT_SHAPES = {
        SLOT_0, SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7
    };

    /** Base structure shape (always visible): base slab + spindle + intake + pipes. */
    private static final VoxelShape FRAME = Shapes.or(BASE, SPINDLE, INTAKE, PIPES);

    /** Number of canister slots on the hub. */
    public static final int SLOT_COUNT = SLOT_SHAPES.length;

    /** Creates a new hub block.
     *
     * @param properties the block properties
     */
    public HubBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(HAS_GASKET, false));
    }

    /** Registers the has_gasket blockstate property.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_GASKET);
    }

    /** Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    /** Returns MODEL render shape since the hub uses a block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) { return RenderShape.MODEL; }

    /** Returns the VoxelShape for a specific canister slot.
     *
     * @param slot the slot index
     * @return the voxel shape
     */
    public static VoxelShape slotShape(int slot) {
        if (slot < 0 || slot >= SLOT_SHAPES.length) { return Shapes.empty(); }
        return SLOT_SHAPES[slot];
    }

    /** Returns the frame shape (base + spindle + intake + pipes).
     *
     * @return the voxel shape
     */
    public static VoxelShape frameShape() {
        return FRAME;
    }

    /**
     * Selection shape: frame + occupied canister slots only.
     * Custom outline rendering is handled by {@link com.mercuriusxeno.goo.client.overlay.SlotOutlineRenderer}.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return the shape
     */
    @Override
    protected @NonNull VoxelShape getShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof HubBlockEntity be) {
            return be.containerState().getCachedShape();
        }
        return FRAME;
    }

    /**
     * Pick block: if a canister slot is targeted, returns a copy of that canister.
     * Otherwise returns the hub block item.
     *
     * @param level       the current level
     * @param pos         the block position
     * @param state       the block state
     * @param includeData true to include data components
     * @param player      the player requesting the clone
     * @return the clone item stack
     */
    @Override
    public @NonNull ItemStack getCloneItemStack(
            @NonNull LevelReader level, @NonNull BlockPos pos,
            @NonNull BlockState state, boolean includeData, @NonNull Player player) {
        if (!(level.getBlockEntity(pos) instanceof HubBlockEntity hub)) {
            return super.getCloneItemStack(level, pos, state, includeData, player);
        }
        ItemStack targeted = HubBlockHandlers.resolveTargetedCanister(hub, pos);
        if (!targeted.isEmpty()) { return targeted; }
        return super.getCloneItemStack(level, pos, state, includeData, player);
    }

    /** Creates the hub block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new HubBlockEntity(pos, state);
    }

    /** Registers the server-side tick dispatcher for per-slot gasket push.
     *
     * @param level the current level
     * @param state the block state
     * @param type  the goo type
     * @return the ticker
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) { return null; }
        return createTickerHelper(type, GooBlockEntities.HUB.get(), HubBlockEntity::serverTick);
    }

    // --- Interactions ---

    /** Classifies the held item and dispatches to the appropriate hub interaction handler.
     *
     * @param stack     the item stack
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hand      the hand used
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                HubBlockEntity.class,
                t -> t == null,
                HubBlockHandlers::dispatchHub);
    }

    /** Empty-hand interaction: sneak removes gasket, otherwise removes targeted canister.
     *
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {
        InteractionResult earlyOut = GooBlockInteraction.validateEmptyHand(level, pos, player);
        if (earlyOut != null) { return earlyOut; }
        if (!(level.getBlockEntity(pos) instanceof HubBlockEntity hub)) { return InteractionResult.PASS; }

        if (player.isShiftKeyDown() && state.getValue(HAS_GASKET)) {
            return HubBlockHandlers.removeGasket(level, pos, hub);
        }
        return HubBlockHandlers.removeCanister(hub, hitResult, pos, player, level);
    }

    /** Drops the intake gasket item on break if one is installed.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param state  the block state
     * @param player the interacting player
     * @return the block state
     */
    @Override
    public @NonNull BlockState playerWillDestroy(
            @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide() && state.getValue(HAS_GASKET)) {
            popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Determines which canister slot the player clicked by nearest-center detection.
     * Converts the hit position to block-local pixel coords and finds the closest slot.
     *
     * @param hit the block hit result from the interaction
     * @param pos the block position
     * @return slot index 0-7, or -1 if click was too far from any slot
     */
    public static int hitSlot(BlockHitResult hit, BlockPos pos) {
        double pixelX = (hit.getLocation().x - pos.getX()) * ShapeHitCheck.PIXELS_PER_BLOCK;
        double pixelZ = (hit.getLocation().z - pos.getZ()) * ShapeHitCheck.PIXELS_PER_BLOCK;
        return nearestSlot(pixelX, pixelZ);
    }

    /**
     * Finds the nearest canister slot to the given pixel coordinates.
     *
     * @param pixelX x position in block-local pixel space (0-16)
     * @param pixelZ z position in block-local pixel space (0-16)
     * @return slot index 0-7, or -1 if beyond {@link #MAX_SLOT_DISTANCE}
     */
    public static int nearestSlot(double pixelX, double pixelZ) {
        int best = NO_SLOT;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < SLOT_CENTERS.length; i++) {
            double distSq = slotDistanceSq(pixelX, pixelZ, i);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = i;
            }
        }
        return withinThreshold(best, bestDistSq);
    }

    /**
     * Returns the slot index if the squared distance is within range, else NO_SLOT.
     * @param slot the candidate slot index, or NO_SLOT
     * @param distSq the squared distance to the slot center
     * @return the slot index if within range, otherwise NO_SLOT (-1)
     */
    private static int withinThreshold(int slot, double distSq) {
        return distSq <= MAX_SLOT_DISTANCE * MAX_SLOT_DISTANCE ? slot : NO_SLOT;
    }

    /**
     * Computes the squared distance from a pixel coordinate to a slot center.
     *
     * @param pixelX the x position in block-local pixel space
     * @param pixelZ the z position in block-local pixel space
     * @param slot   the slot index
     * @return the squared Euclidean distance
     */
    private static double slotDistanceSq(double pixelX, double pixelZ, int slot) {
        double dx = pixelX - SLOT_CENTERS[slot][0];
        double dz = pixelZ - SLOT_CENTERS[slot][1];
        return dx * dx + dz * dz;
    }
}
