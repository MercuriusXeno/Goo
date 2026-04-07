package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.BucketOfGooItem;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tap block: a faucet with a canister slot that auto-drips goo blobs on a timer.
 * FACING indicates the direction the spigot points. Right-clicking the valve
 * toggles dripping; right-clicking the body inserts/removes the canister.
 */
public class TapBlock extends BaseEntityBlock {

    public static final MapCodec<TapBlock> CODEC = simpleCodec(TapBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    /** Block update flags: notify neighbors + send to clients. */
    /** Error message for TUNER_PASS reaching dispatch. */
    private static final String ERR_TUNER_PASS = "TUNER_PASS handled in validate";

    private static final int BLOCK_UPDATE_FLAGS = 3;
    /** 180-degree rotation (2 CW steps). */
    private static final int ROTATION_HALF = 2;
    /** 270-degree rotation (3 CW steps). */
    private static final int ROTATION_THREE_QUARTER = 3;
    /** Pixels per block for coordinate conversion. */
    private static final double PIXELS_PER_BLOCK = 16;

    // Array indices for the [x1, z1, x2, z2] rotation tuple.
    /** Index of min-X in the XZ rotation tuple. */
    private static final int XZ_X1 = 0;
    /** Index of min-Z in the XZ rotation tuple. */
    private static final int XZ_Z1 = 1;
    /** Index of max-X in the XZ rotation tuple. */
    private static final int XZ_X2 = 2;
    /** Index of max-Z in the XZ rotation tuple. */
    private static final int XZ_Z2 = 3;

    /** Whether a choral gasket is installed on this tap. */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");

    /** Whether the tap valve is open (dripping). */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    // --- Sub-region shapes for hit detection ---

    /** South-facing body shape (attachment region). */
    private static final VoxelShape SOUTH_BODY = box(5, 0, 0, 11, 4, 6);
    /** South-facing valve shape (toggle region). */
    private static final VoxelShape SOUTH_VALVE = box(6.5, 4, 6.5, 9.5, 6.5, 9.5);

    /** South-facing canister slot shape (wireframe preview + BER position). */
    private static final VoxelShape SOUTH_CANISTER_SLOT = box(6, 4, 1, 10, 16, 5);
    /** South-facing spigot nozzle shape. */
    private static final VoxelShape SOUTH_SPIGOT = box(6, 2, 6, 10, 4, 10);

    /** Per-facing body shapes for hit detection. */
    private static final Map<Direction, VoxelShape> BODY_SHAPES = buildSubShapes(SOUTH_BODY);
    /** Per-facing valve shapes for hit detection. */
    private static final Map<Direction, VoxelShape> VALVE_SHAPES = buildSubShapes(SOUTH_VALVE);
    /** Per-facing canister slot shapes for wireframe preview. */
    private static final Map<Direction, VoxelShape> CANISTER_SLOT_SHAPES = buildSubShapes(SOUTH_CANISTER_SLOT);
    /** Per-facing composite collision shapes (no canister). */
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();
    /** Per-facing composite shapes with canister slot included. */
    private static final Map<Direction, VoxelShape> SHAPES_WITH_CANISTER = buildShapesWithCanister();

    /** Constructs a new tap block with default south-facing state.
     *
     * @param properties the block properties
     */
    public TapBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.SOUTH)
            .setValue(HAS_GASKET, false)
            .setValue(OPEN, true));
    }

    /** Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    /** Returns MODEL render shape since the tap uses a block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    /** Registers all tap blockstate properties.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_GASKET, OPEN);
    }

    /**
     * Places the tap facing toward the clicked block face. The tap's FACING
     * is the direction the spigot points (away from the container it attaches to).
     *
     * @param context the collision context
     * @return the state for placement
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isVertical()) {
            clickedFace = context.getHorizontalDirection().getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, clickedFace);
    }

    /** Returns the composite shape, including the canister slot when a canister is inserted.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return the shape
     */
    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean hasCanister = level.getBlockEntity(pos) instanceof TapBlockEntity tap
                && !tap.getCanister().isEmpty();
        Map<Direction, VoxelShape> map = hasCanister ? SHAPES_WITH_CANISTER : SHAPES;
        return map.getOrDefault(facing, map.get(Direction.SOUTH));
    }

    /** Creates the tap block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TapBlockEntity(pos, state);
    }

    /** Registers the server-side drip tick dispatcher.
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
        return createTickerHelper(type, GooBlockEntities.TAP.get(), TapBlockEntity::serverTick);
    }

    /** Drops gasket and canister items on break.
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
        if (!level.isClientSide()) {
            dropGasketOnBreak(level, pos, state);
            dropCanisterOnBreak(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Drops the gasket item if one is installed.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     */
    private static void dropGasketOnBreak(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(HAS_GASKET)) {
            popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        }
    }

    /** Drops the canister item if one is inserted.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private static void dropCanisterOnBreak(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TapBlockEntity tap) {
            ItemStack canister = tap.removeCanister();
            if (!canister.isEmpty()) {
                popResource(level, pos, canister);
            }
        }
    }

    // --- Interactions ---

    /**
     * Classifies the held item via GooBlockInteraction and dispatches to
     * tap-specific handlers. Hits on the canister region are routed directly
     * to canister operations, bypassing tap body/valve logic.
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
        GooBlockInteraction.Dispatcher<TapBlockEntity> handler =
                hitCanister(hitResult, pos, state.getValue(FACING))
                ? this::dispatchCanisterRegion : this::dispatchTap;
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                TapBlockEntity.class,
                t -> t == null,
                handler);
    }

    /** Routes canister-region interactions - only blob/bucket ops, no canister insert.
     *
     * @param interaction the classified interaction type
     * @param tap         the tap block entity
     * @param stack       the item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @return the interaction result
     */
    private InteractionResult dispatchCanisterRegion(
            GooInteractionType interaction, TapBlockEntity tap, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        return switch (interaction) {
            case TUNER_PASS      -> throw new IllegalStateException(ERR_TUNER_PASS);
            case CANISTER_INSERT -> InteractionResult.TRY_WITH_EMPTY_HAND;
            case BLOB_INSERT     -> handleBlobInsert(tap, stack, player);
            case BUCKET_INSERT   -> handleBucketInsert(tap, stack, player, hand);
            case BUCKET_EXTRACT  -> handleBucketExtract(tap, stack, player);
        };
    }

    /** Routes a classified interaction to the appropriate tap handler.
     *
     * @param interaction the classified interaction type
     * @param tap         the tap block entity
     * @param stack       the item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @return the interaction result
     */
    private InteractionResult dispatchTap(
            GooInteractionType interaction, TapBlockEntity tap, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        return switch (interaction) {
            case TUNER_PASS      -> throw new IllegalStateException(ERR_TUNER_PASS);
            case CANISTER_INSERT -> handleCanisterInsert(tap, stack, player);
            case BLOB_INSERT     -> handleBlobInsert(tap, stack, player);
            case BUCKET_INSERT   -> handleBucketInsert(tap, stack, player, hand);
            case BUCKET_EXTRACT  -> handleBucketExtract(tap, stack, player);
        };
    }

    /**
     * Empty-hand interactions dispatched by sub-region: canister hit → remove
     * canister; valve hit → toggle open/closed; body hit → remove gasket.
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

        if (!(level.getBlockEntity(pos) instanceof TapBlockEntity tap)) { return InteractionResult.PASS; }

        Direction facing = state.getValue(FACING);
        return dispatchEmptyHand(state, level, pos, player, hitResult, tap, facing);
    }

    /** Dispatches empty-hand interactions by sub-region: canister, valve, body, gasket.
     *
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @param tap       the tap block entity
     * @param facing    the tap facing direction
     * @return the interaction result
     */
    private InteractionResult dispatchEmptyHand(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult, TapBlockEntity tap, Direction facing) {
        if (hitCanister(hitResult, pos, facing) && !tap.getCanister().isEmpty()) {
            return removeCanister(tap, level, pos, player);
        }
        if (hitValve(hitResult, pos, facing)) {
            return toggleValve(state, level, pos);
        }
        return tryCanisterOrGasket(state, level, pos, player, tap);
    }

    /** Removes the canister if present, otherwise tries to remove the gasket.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @param tap    the tap block entity
     * @return the interaction result
     */
    private static InteractionResult tryCanisterOrGasket(
            BlockState state, Level level, BlockPos pos, Player player, TapBlockEntity tap) {
        if (!tap.getCanister().isEmpty()) {
            return removeCanister(tap, level, pos, player);
        }
        return tryRemoveGasket(state, level, pos, player, tap);
    }

    /** Removes the canister from the tap and gives it to the player.
     *
     * @param tap    the tap block entity
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @return SUCCESS
     */
    private static InteractionResult removeCanister(
            TapBlockEntity tap, Level level, BlockPos pos, Player player) {
        ItemStack removed = tap.removeCanister();
        PlayerUtils.addOrDrop(player, removed);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT,
            SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Toggles the valve open/closed and plays the appropriate sound.
     *
     * @param state the block state
     * @param level the current level
     * @param pos   the block position
     * @return SUCCESS
     */
    private static InteractionResult toggleValve(BlockState state, Level level, BlockPos pos) {
        boolean nowOpen = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, nowOpen), BLOCK_UPDATE_FLAGS);
        level.playSound(null, pos,
            nowOpen ? SoundEvents.COPPER_TRAPDOOR_OPEN : SoundEvents.COPPER_TRAPDOOR_CLOSE,
            SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Removes the gasket if the player is sneaking and one is installed.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @param tap    the tap block entity
     * @return SUCCESS if removed, PASS otherwise
     */
    private static InteractionResult tryRemoveGasket(
            BlockState state, Level level, BlockPos pos, Player player, TapBlockEntity tap) {
        if (player.isShiftKeyDown() && state.getValue(HAS_GASKET)) {
            GasketInstallation.popGasket(level, pos, tap.getGasketId(GasketRole.RECEIVER));
            tap.clearGasket(GasketRole.RECEIVER);
            level.setBlock(pos, state.setValue(HAS_GASKET, false), BLOCK_UPDATE_FLAGS);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // --- Handlers ---

    /** Inserts a canister into the tap's slot if empty.
     *
     * @param tap    the tap block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleCanisterInsert(
            TapBlockEntity tap, ItemStack stack, Player player) {
        if (!tap.insertCanister(stack.copyWithCount(1))) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        stack.consume(1, player);
        tap.getLevel().playSound(null, tap.getBlockPos(), SoundEvents.DECORATED_POT_INSERT,
            SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Pours goo from a blob or omniblob into the tap's canister.
     *
     * @param tap    the tap block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleBlobInsert(
            TapBlockEntity tap, ItemStack stack, Player player) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null || !tap.canAcceptGoo()) { return InteractionResult.PASS; }

        long volume = BlobStacks.volumeOf(stack);
        long accepted = tap.insertGoo(type, volume);
        if (accepted <= 0) { return InteractionResult.PASS; }

        BlobStacks.deplete(stack, accepted, player);
        tap.getLevel().playSound(null, tap.getBlockPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Pours goo from a bucket of goo into the tap's canister.
     *
     * @param tap    the tap block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @param hand   the hand used
     * @return the interaction result
     */
    private static InteractionResult handleBucketInsert(
            TapBlockEntity tap, ItemStack stack, Player player, InteractionHand hand) {
        GooContents bucketGoo = BucketOfGooItem.getContents(stack);
        if (bucketGoo.isEmpty() || !tap.canAcceptGoo()) { return InteractionResult.PASS; }

        GooContents remaining = pourBucketEntries(tap, bucketGoo);
        if (remaining == bucketGoo) { return InteractionResult.PASS; }

        BucketOfGooItem.setOrRevert(stack, remaining, player, hand);
        tap.getLevel().playSound(null, tap.getBlockPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Pours each goo entry from a bucket into the tap, returning the leftover contents.
     *
     * @param tap       the tap block entity to pour into
     * @param bucketGoo the bucket goo contents
     * @return the remaining contents after pouring, or the original if nothing was accepted
     */
    private static GooContents pourBucketEntries(TapBlockEntity tap, GooContents bucketGoo) {
        GooContents remaining = bucketGoo;
        for (var entry : bucketGoo.getAll().entrySet()) {
            long accepted = tap.insertGoo(entry.getKey(), entry.getValue());
            if (accepted > 0) {
                remaining = remaining.withRemoved(entry.getKey(), accepted);
            }
        }
        return remaining;
    }

    /** Extracts goo from the tap's canister into an empty bucket.
     *
     * @param tap    the tap block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleBucketExtract(
            TapBlockEntity tap, ItemStack stack, Player player) {
        GooContents contents = tap.getGooContents();
        GooType type = contents.largestType();
        if (type == null) { return InteractionResult.PASS; }

        long extracted = tap.extractGoo(type, contents.getVolume(type));
        if (extracted <= 0) { return InteractionResult.PASS; }

        giveFilled(tap, stack, player, type, extracted);
        return InteractionResult.SUCCESS;
    }

    /** Shrinks the empty bucket, creates a filled bucket, and gives it to the player.
     *
     * @param tap       the tap block entity (for sound)
     * @param stack     the empty bucket stack to shrink
     * @param player    the receiving player
     * @param type      the goo type to fill with
     * @param extracted the volume extracted in microblobs
     */
    private static void giveFilled(
            TapBlockEntity tap, ItemStack stack, Player player, GooType type, long extracted) {
        ItemStack filledBucket = BucketOfGooItem.createWithGoo(type, extracted);
        stack.shrink(1);
        PlayerUtils.addOrDrop(player, filledBucket);
        tap.getLevel().playSound(null, tap.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    // --- Sub-region hit detection ---

    /** Returns true if the hit point is within the valve sub-region.
     *
     * @param hit    the ray trace hit result
     * @param pos    the block position
     * @param facing the facing direction
     * @return true if the condition is met
     */
    private static boolean hitValve(BlockHitResult hit, BlockPos pos, Direction facing) {
        VoxelShape valve = VALVE_SHAPES.getOrDefault(facing, VALVE_SHAPES.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, valve);
    }

    /** Returns true if the hit point is within the canister slot sub-region.
     *
     * @param hit    the ray trace hit result
     * @param pos    the block position
     * @param facing the facing direction
     * @return true if the condition is met
     */
    private static boolean hitCanister(BlockHitResult hit, BlockPos pos, Direction facing) {
        VoxelShape slot = CANISTER_SLOT_SHAPES.getOrDefault(facing, CANISTER_SLOT_SHAPES.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, slot);
    }

    /** Returns true if the hit point is within the body sub-region.
     *
     * @param hit    the ray trace hit result
     * @param pos    the block position
     * @param facing the facing direction
     * @return true if the condition is met
     */
    private static boolean hitBody(BlockHitResult hit, BlockPos pos, Direction facing) {
        VoxelShape body = BODY_SHAPES.getOrDefault(facing, BODY_SHAPES.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, body);
    }

    /** Returns the body VoxelShape for the given facing direction.
     *
     * @param facing the facing direction
     * @return the voxel shape
     */
    public static VoxelShape bodyShape(Direction facing) {
        return BODY_SHAPES.getOrDefault(facing, BODY_SHAPES.get(Direction.SOUTH));
    }

    /** Returns the canister slot VoxelShape for the given facing direction.
     *
     * @param facing the facing direction
     * @return true if ister slot shape
     */
    public static VoxelShape canisterSlotShape(Direction facing) {
        return CANISTER_SLOT_SHAPES.getOrDefault(facing, CANISTER_SLOT_SHAPES.get(Direction.SOUTH));
    }

    // --- Shape building ---

    /** Builds per-facing sub-shapes from a south-facing base.
     *
     * @param southBase the south-facing base shape
     * @return the new sub shapes
     */
    private static Map<Direction, VoxelShape> buildSubShapes(VoxelShape southBase) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, southBase);
        map.put(Direction.NORTH, rotateShapeCw(southBase, ROTATION_HALF));
        map.put(Direction.EAST, rotateShapeCw(southBase, ROTATION_THREE_QUARTER));
        map.put(Direction.WEST, rotateShapeCw(southBase, 1));
        return map;
    }

    /**
     * Builds VoxelShapes for all four horizontal facings. South-facing base shape
     * is the union of body, spigot, and valve.
     *
     * @return the new shapes
     */
    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, southShape());
        map.put(Direction.NORTH, rotateShapeCw(southShape(), ROTATION_HALF));
        map.put(Direction.EAST, rotateShapeCw(southShape(), ROTATION_THREE_QUARTER));
        map.put(Direction.WEST, rotateShapeCw(southShape(), 1));
        return map;
    }

    /** Builds per-facing shapes with canister slot included (for when a canister is inserted).
     *
     * @return the new shapes with canister
     */
    private static Map<Direction, VoxelShape> buildShapesWithCanister() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        VoxelShape south = Shapes.or(southShape(), SOUTH_CANISTER_SLOT);
        map.put(Direction.SOUTH, south);
        map.put(Direction.NORTH, rotateShapeCw(south, ROTATION_HALF));
        map.put(Direction.EAST, rotateShapeCw(south, ROTATION_THREE_QUARTER));
        map.put(Direction.WEST, rotateShapeCw(south, 1));
        return map;
    }

    /** Builds the south-facing composite shape: body + spigot + valve.
     *
     * @return the voxel shape
     */
    private static VoxelShape southShape() {
        return Shapes.or(SOUTH_BODY, SOUTH_SPIGOT, SOUTH_VALVE);
    }

    /**
     * Rotates a VoxelShape clockwise around the Y axis by the given number
     * of 90-degree steps. Decomposes into AABB parts and reassembles.
     *
     * @param shape the VoxelShape to rotate
     * @param steps number of 90-degree clockwise steps
     * @return the voxel shape
     */
    static VoxelShape rotateShapeCw(VoxelShape shape, int steps) {
        if (steps == 0) { return shape; }
        VoxelShape[] result = { Shapes.empty() };
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
            result[0] = Shapes.or(result[0], rotateBox(x1, y1, z1, x2, y2, z2, steps)));
        return result[0];
    }

    /** Rotates a single AABB clockwise around Y by the given 90-degree steps and converts to pixel coords.
     *
     * @param x1    min X in unit coords
     * @param y1    min Y in unit coords
     * @param z1    min Z in unit coords
     * @param x2    max X in unit coords
     * @param y2    max Y in unit coords
     * @param z2    max Z in unit coords
     * @param steps number of 90-degree clockwise steps
     * @return the rotated box as a VoxelShape in pixel coords
     */
    private static VoxelShape rotateBox(
            double x1, double y1, double z1, double x2, double y2, double z2, int steps) {
        double[] xz = rotateXZ(x1, z1, x2, z2, steps);
        return box(
            xz[XZ_X1] * PIXELS_PER_BLOCK, y1 * PIXELS_PER_BLOCK, xz[XZ_Z1] * PIXELS_PER_BLOCK,
            xz[XZ_X2] * PIXELS_PER_BLOCK, y2 * PIXELS_PER_BLOCK, xz[XZ_Z2] * PIXELS_PER_BLOCK);
    }

    /** Rotates XZ coordinates clockwise around Y by the given 90-degree steps.
     *
     * @param x1    min X in unit coords
     * @param z1    min Z in unit coords
     * @param x2    max X in unit coords
     * @param z2    max Z in unit coords
     * @param steps number of 90-degree clockwise steps
     * @return array of [rx1, rz1, rx2, rz2] after rotation
     */
    private static double[] rotateXZ(double x1, double z1, double x2, double z2, int steps) {
        double[] xz = { x1, z1, x2, z2 };
        for (int s = 0; s < steps; s++) {
            rotateCwOnce(xz);
        }
        return xz;
    }

    /** Applies one 90-degree clockwise rotation around Y to XZ coordinates in place.
     *
     * @param xz array of [x1, z1, x2, z2] to rotate in place
     */
    private static void rotateCwOnce(double[] xz) {
        double tmpX1 = 1.0 - xz[XZ_Z2];
        double tmpZ1 = xz[XZ_X1];
        double tmpX2 = 1.0 - xz[XZ_Z1];
        double tmpZ2 = xz[XZ_X2];
        xz[XZ_X1] = tmpX1;
        xz[XZ_Z1] = tmpZ1;
        xz[XZ_X2] = tmpX2;
        xz[XZ_Z2] = tmpZ2;
    }
}
