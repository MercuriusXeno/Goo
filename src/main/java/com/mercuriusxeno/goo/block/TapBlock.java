package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.BucketOfGooItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    /** Constructs a new tap block with default south-facing state. */
    public TapBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.SOUTH)
            .setValue(HAS_GASKET, false)
            .setValue(OPEN, true));
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_GASKET, OPEN);
    }

    /**
     * Places the tap facing toward the clicked block face. The tap's FACING
     * is the direction the spigot points (away from the container it attaches to).
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

    /** Returns the composite shape, including the canister slot when a canister is inserted. */
    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean hasCanister = level.getBlockEntity(pos) instanceof TapBlockEntity tap
                && !tap.getCanister().isEmpty();
        Map<Direction, VoxelShape> map = hasCanister ? SHAPES_WITH_CANISTER : SHAPES;
        return map.getOrDefault(facing, map.get(Direction.SOUTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TapBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, GooBlockEntities.TAP.get(), TapBlockEntity::serverTick);
    }

    /** Drops gasket and canister items on break. */
    @Override
    public @NonNull BlockState playerWillDestroy(
            @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide()) {
            if (state.getValue(HAS_GASKET)) {
                Block.popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TapBlockEntity tap) {
                ItemStack canister = tap.removeCanister();
                if (!canister.isEmpty()) {
                    Block.popResource(level, pos, canister);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // --- Interactions ---

    /**
     * Classifies the held item via GooBlockInteraction and dispatches to
     * tap-specific handlers. Hits on the canister region are routed directly
     * to canister operations, bypassing tap body/valve logic.
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        // Hits on the canister region delegate to canister interactions
        if (hitCanister(hitResult, pos, state.getValue(FACING))) {
            return GooBlockInteraction.handleItemInteraction(
                    stack, level, pos, player, hand, hitResult,
                    TapBlockEntity.class,
                    t -> t == null,
                    this::dispatchCanisterRegion);
        }
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                TapBlockEntity.class,
                t -> t == null,
                this::dispatchTap);
    }

    /** Routes canister-region interactions - only blob/bucket ops, no canister insert. */
    private InteractionResult dispatchCanisterRegion(
            GooInteractionType interaction, TapBlockEntity tap, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        return switch (interaction) {
            case TUNER_PASS      -> throw new IllegalStateException("TUNER_PASS handled in validate");
            case CANISTER_INSERT -> InteractionResult.TRY_WITH_EMPTY_HAND;
            case BLOB_INSERT     -> handleBlobInsert(tap, stack, player, level, pos);
            case BUCKET_INSERT   -> handleBucketInsert(tap, stack, player, hand, level, pos);
            case BUCKET_EXTRACT  -> handleBucketExtract(tap, stack, player, level, pos);
        };
    }

    /** Routes a classified interaction to the appropriate tap handler. */
    private InteractionResult dispatchTap(
            GooInteractionType interaction, TapBlockEntity tap, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        return switch (interaction) {
            case TUNER_PASS      -> throw new IllegalStateException("TUNER_PASS handled in validate");
            case CANISTER_INSERT -> handleCanisterInsert(tap, stack, player, level, pos);
            case BLOB_INSERT     -> handleBlobInsert(tap, stack, player, level, pos);
            case BUCKET_INSERT   -> handleBucketInsert(tap, stack, player, hand, level, pos);
            case BUCKET_EXTRACT  -> handleBucketExtract(tap, stack, player, level, pos);
        };
    }

    /**
     * Empty-hand interactions dispatched by sub-region: canister hit → remove
     * canister; valve hit → toggle open/closed; body hit → remove gasket.
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {
        InteractionResult earlyOut = GooBlockInteraction.validateEmptyHand(level, pos, player);
        if (earlyOut != null) return earlyOut;

        if (!(level.getBlockEntity(pos) instanceof TapBlockEntity tap)) return InteractionResult.PASS;

        Direction facing = state.getValue(FACING);

        // Canister region hit → remove canister directly (bypasses valve/gasket)
        if (hitCanister(hitResult, pos, facing) && !tap.getCanister().isEmpty()) {
            ItemStack removed = tap.removeCanister();
            PlayerUtils.addOrDrop(player, removed);
            level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT,
                SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        if (hitValve(hitResult, pos, facing)) {
            boolean nowOpen = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, nowOpen), 3);
            level.playSound(null, pos,
                nowOpen ? SoundEvents.COPPER_TRAPDOOR_OPEN : SoundEvents.COPPER_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // Remove canister if present (fallback for body-region hits)
        ItemStack canister = tap.getCanister();
        if (!canister.isEmpty()) {
            ItemStack removed = tap.removeCanister();
            PlayerUtils.addOrDrop(player, removed);
            level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT,
                SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // Sneak + empty hand with gasket → remove gasket
        if (player.isShiftKeyDown() && state.getValue(HAS_GASKET)) {
            GasketInstallation.popGasket(level, pos, tap.getGasketId(GasketRole.RECEIVER));
            tap.clearGasket(GasketRole.RECEIVER);
            level.setBlock(pos, state.setValue(HAS_GASKET, false), 3);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // --- Handlers ---

    /** Inserts a canister into the tap's slot if empty. */
    private static InteractionResult handleCanisterInsert(
            TapBlockEntity tap, ItemStack stack, Player player, Level level, BlockPos pos) {
        if (!tap.insertCanister(stack.copyWithCount(1))) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT,
            SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Pours goo from a blob or omniblob into the tap's canister. */
    private static InteractionResult handleBlobInsert(
            TapBlockEntity tap, ItemStack stack, Player player, Level level, BlockPos pos) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null || !tap.canAcceptGoo()) return InteractionResult.PASS;

        long volume = BlobStacks.volumeOf(stack);
        long accepted = tap.insertGoo(type, volume);
        if (accepted <= 0) return InteractionResult.PASS;

        BlobStacks.deplete(stack, accepted, player);
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Pours goo from a bucket of goo into the tap's canister. */
    private static InteractionResult handleBucketInsert(
            TapBlockEntity tap, ItemStack stack, Player player, InteractionHand hand,
            Level level, BlockPos pos) {
        GooContents bucketGoo = BucketOfGooItem.getContents(stack);
        if (bucketGoo.isEmpty() || !tap.canAcceptGoo()) return InteractionResult.PASS;

        boolean inserted = false;
        for (var entry : bucketGoo.getAll().entrySet()) {
            GooType type = entry.getKey();
            long volume = entry.getValue();
            long accepted = tap.insertGoo(type, volume);
            if (accepted > 0) {
                bucketGoo = bucketGoo.withRemoved(type, accepted);
                inserted = true;
            }
        }
        if (!inserted) return InteractionResult.PASS;

        BucketOfGooItem.setOrRevert(stack, bucketGoo, player, hand);
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Extracts goo from the tap's canister into an empty bucket. */
    private static InteractionResult handleBucketExtract(
            TapBlockEntity tap, ItemStack stack, Player player, Level level, BlockPos pos) {
        GooContents contents = tap.getGooContents();
        GooType type = contents.largestType();
        if (type == null) return InteractionResult.PASS;

        long extracted = tap.extractGoo(type, contents.getVolume(type));
        if (extracted <= 0) return InteractionResult.PASS;

        ItemStack filledBucket = BucketOfGooItem.createWithGoo(type, extracted);
        stack.shrink(1);
        PlayerUtils.addOrDrop(player, filledBucket);
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    // --- Sub-region hit detection ---

    /** Returns true if the hit point is within the valve sub-region. */
    private static boolean hitValve(BlockHitResult hit, BlockPos pos, Direction facing) {
        VoxelShape valve = VALVE_SHAPES.getOrDefault(facing, VALVE_SHAPES.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, valve);
    }

    /** Returns true if the hit point is within the canister slot sub-region. */
    private static boolean hitCanister(BlockHitResult hit, BlockPos pos, Direction facing) {
        VoxelShape slot = CANISTER_SLOT_SHAPES.getOrDefault(facing, CANISTER_SLOT_SHAPES.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, slot);
    }

    /** Returns true if the hit point is within the body sub-region. */
    private static boolean hitBody(BlockHitResult hit, BlockPos pos, Direction facing) {
        VoxelShape body = BODY_SHAPES.getOrDefault(facing, BODY_SHAPES.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, body);
    }

    /** Returns the body VoxelShape for the given facing direction. */
    public static VoxelShape bodyShape(Direction facing) {
        return BODY_SHAPES.getOrDefault(facing, BODY_SHAPES.get(Direction.SOUTH));
    }

    /** Returns the canister slot VoxelShape for the given facing direction. */
    public static VoxelShape canisterSlotShape(Direction facing) {
        return CANISTER_SLOT_SHAPES.getOrDefault(facing, CANISTER_SLOT_SHAPES.get(Direction.SOUTH));
    }

    // --- Shape building ---

    /** Builds per-facing sub-shapes from a south-facing base. */
    private static Map<Direction, VoxelShape> buildSubShapes(VoxelShape southBase) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, southBase);
        map.put(Direction.NORTH, rotateShapeCw(southBase, 2));
        map.put(Direction.EAST, rotateShapeCw(southBase, 3));
        map.put(Direction.WEST, rotateShapeCw(southBase, 1));
        return map;
    }

    /**
     * Builds VoxelShapes for all four horizontal facings. South-facing base shape
     * is the union of body, spigot, and valve.
     */
    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, southShape());
        map.put(Direction.NORTH, rotateShapeCw(southShape(), 2));
        map.put(Direction.EAST, rotateShapeCw(southShape(), 3));
        map.put(Direction.WEST, rotateShapeCw(southShape(), 1));
        return map;
    }

    /** Builds per-facing shapes with canister slot included (for when a canister is inserted). */
    private static Map<Direction, VoxelShape> buildShapesWithCanister() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        VoxelShape south = Shapes.or(southShape(), SOUTH_CANISTER_SLOT);
        map.put(Direction.SOUTH, south);
        map.put(Direction.NORTH, rotateShapeCw(south, 2));
        map.put(Direction.EAST, rotateShapeCw(south, 3));
        map.put(Direction.WEST, rotateShapeCw(south, 1));
        return map;
    }

    /** Builds the south-facing composite shape: body + spigot + valve. */
    private static VoxelShape southShape() {
        VoxelShape spigot = box(6, 2, 6, 10, 4, 10);
        return Shapes.or(SOUTH_BODY, spigot, SOUTH_VALVE);
    }

    /**
     * Rotates a VoxelShape clockwise around the Y axis by the given number
     * of 90-degree steps. Decomposes into AABB parts and reassembles.
     */
    static VoxelShape rotateShapeCw(VoxelShape shape, int steps) {
        if (steps == 0) return shape;
        VoxelShape[] result = { Shapes.empty() };
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double rx1 = x1, rz1 = z1, rx2 = x2, rz2 = z2;
            for (int s = 0; s < steps; s++) {
                double tmpX1 = 1.0 - rz2;
                double tmpZ1 = rx1;
                double tmpX2 = 1.0 - rz1;
                double tmpZ2 = rx2;
                rx1 = tmpX1; rz1 = tmpZ1;
                rx2 = tmpX2; rz2 = tmpZ2;
            }
            result[0] = Shapes.or(result[0], box(
                rx1 * 16, y1 * 16, rz1 * 16,
                rx2 * 16, y2 * 16, rz2 * 16));
        });
        return result[0];
    }
}
