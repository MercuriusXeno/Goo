package com.mercuriusxeno.goo.block.reactor;

import com.mercuriusxeno.goo.CutawayInteractionHelper;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.CutawayShapeHelper;
import com.mercuriusxeno.goo.block.GooBlockInteraction;
import com.mercuriusxeno.goo.block.IGooLightSource;
import com.mercuriusxeno.goo.block.ShapeHitCheck;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reactor block: consumes goo from 4 corner canisters on top and
 * produces output into a canister in the front hollow. Redstone
 * halts processing. Faces the player on placement.
 */
public class ReactorBlock extends BaseEntityBlock {

    /**
     * Horizontal facing - orients the front hollow.
     */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /**
     * Whether the reactor is receiving a redstone signal.
     */
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    /**
     * Whether the reactor is actively processing a reaction.
     */
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;
    public static final MapCodec<ReactorBlock> CODEC = simpleCodec(ReactorBlock::new);
    /**
     * Hollow volume in model space (south-facing): x in [5,11], y in [1,15], z in [0,6].
     */
    private static final double HOLLOW_MIN_X = 5.0 / 16.0;
    private static final double HOLLOW_MAX_X = 11.0 / 16.0;
    private static final double HOLLOW_MIN_Y = 1.0 / 16.0;
    private static final double HOLLOW_MAX_Y = 13.0 / 16.0;

    /**
     * Output canister slot shape (south-facing): 4x12x4 centered in the
     * 12-pixel hollow (y=1 to y=13).
     */
    private static final VoxelShape SOUTH_OUTPUT_SLOT = box(6, 1, 1, 10, 13, 5);
    /**
     * Output slot shapes per facing.
     */
    private static final Map<Direction, VoxelShape> OUTPUT_SLOT_SHAPES =
            CutawayShapeHelper.buildShapes(SOUTH_OUTPUT_SLOT);

    /**
     * South-facing shape pieces: full cube with the 12-pixel hollow
     * (y=1 to y=13) subtracted via composite.
     */
    private static final VoxelShape SOUTH_BACK = box(0, 0, 6, 16, 16, 16);
    private static final VoxelShape SOUTH_BOTTOM = box(0, 0, 0, 16, 1, 6);
    private static final VoxelShape SOUTH_TOP = box(0, 13, 0, 16, 16, 6);
    private static final VoxelShape SOUTH_LEFT = box(0, 1, 0, 5, 13, 6);
    private static final VoxelShape SOUTH_RIGHT = box(11, 1, 0, 16, 13, 6);
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            SOUTH_BACK, SOUTH_BOTTOM, SOUTH_TOP, SOUTH_LEFT, SOUTH_RIGHT);

    /**
     * VoxelShapes per facing, rotated from the south-facing base shape.
     */
    private static final Map<Direction, VoxelShape> SHAPES =
            CutawayShapeHelper.buildShapes(SOUTH_SHAPE);

    /**
     * VoxelShapes per facing with output canister slot included.
     */
    private static final Map<Direction, VoxelShape> SHAPES_WITH_CANISTER =
            buildShapesWithCanister();

    /**
     * Creates a reactor block.
     *
     * @param properties the block properties
     */
    public ReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TRIGGERED, false)
                .setValue(CRAFTING, false));
    }

    /**
     * Returns the output canister slot shape for the given facing.
     *
     * @param facing the block facing direction
     * @return the output slot voxel shape
     */
    public static VoxelShape outputSlotShape(Direction facing) {
        return OUTPUT_SLOT_SHAPES.getOrDefault(facing, OUTPUT_SLOT_SHAPES.get(Direction.SOUTH));
    }

    /**
     * Routes canister clicks to insert or remove based on slot occupancy.
     *
     * @param reactor the reactor block entity
     * @param stack   the held canister item stack
     * @param player  the interacting player
     * @param level   the current level
     * @param pos     the block position
     * @return SUCCESS, PASS, or TRY_WITH_EMPTY_HAND
     */
    private static InteractionResult handleCanisterInteraction(
            ReactorBlockEntity reactor, ItemStack stack, Player player,
            Level level, BlockPos pos) {
        if (reactor.getOutputCanister().isEmpty()) {
            return insertReactorCanister(reactor, stack, player, level, pos);
        }
        if (!player.isSecondaryUseActive()) {
            return removeReactorCanister(reactor, player, level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Inserts a canister into the reactor output slot.
     *
     * @param reactor the reactor block entity
     * @param stack   the item stack to insert
     * @param player  the interacting player
     * @param level   the current level
     * @param pos     the block position
     * @return SUCCESS if inserted, PASS if the slot rejected the item
     */
    private static InteractionResult insertReactorCanister(
            ReactorBlockEntity reactor, ItemStack stack, Player player,
            Level level, BlockPos pos) {
        if (!reactor.insertOutputCanister(stack)) {
            return InteractionResult.PASS;
        }
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Removes the output canister and gives it to the player.
     *
     * @param reactor the reactor block entity
     * @param player  the interacting player
     * @param level   the current level
     * @param pos     the block position
     * @return SUCCESS if removed, PASS if the slot was empty
     */
    private static InteractionResult removeReactorCanister(
            ReactorBlockEntity reactor, Player player, Level level, BlockPos pos) {
        ItemStack removed = reactor.removeOutputCanister();
        if (removed.isEmpty()) {
            return InteractionResult.PASS;
        }
        PlayerUtils.addOrDrop(player, removed);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Returns true if the hit lands within the output canister slot shape.
     *
     * @param state the block state
     * @param pos   the block position
     * @param hit   the ray trace hit result
     * @return true if the hit is on the output slot
     */
    public static boolean hitOutputSlot(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        VoxelShape slot = OUTPUT_SLOT_SHAPES.getOrDefault(facing,
                OUTPUT_SLOT_SHAPES.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, slot);
    }

    /**
     * Builds composite shapes with the output canister slot included.
     *
     * @return shapes with canister keyed by direction
     */
    private static Map<Direction, VoxelShape> buildShapesWithCanister() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        SHAPES.forEach((dir, shape) ->
                map.put(dir, Shapes.or(shape, OUTPUT_SLOT_SHAPES.get(dir))));
        return Map.copyOf(map);
    }

    /**
     * Returns true if the hit lands within the reactor's front hollow region.
     *
     * @param state the block state
     * @param pos   the block position
     * @param hit   the ray trace hit result
     * @return true if the click is in the hollow
     */
    public static boolean isHollowClick(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        double hitX = hit.getLocation().x - pos.getX();
        double hitY = hit.getLocation().y - pos.getY();
        double hitZ = hit.getLocation().z - pos.getZ();
        double modelX = CutawayInteractionHelper.toModelX(facing, hitX, hitZ);
        return isInHollowXY(modelX, hitY);
    }

    /**
     * Returns true if model-space X and Y are within the hollow bounds.
     *
     * @param modelX model-space X coordinate
     * @param hitY   block-local Y coordinate
     * @return true if within hollow X/Y bounds
     */
    private static boolean isInHollowXY(double modelX, double hitY) {
        return modelX >= HOLLOW_MIN_X && modelX <= HOLLOW_MAX_X
                && hitY >= HOLLOW_MIN_Y && hitY <= HOLLOW_MAX_Y;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state,
                                           @NonNull BlockGetter level, @NonNull BlockPos pos,
                                           @NonNull CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean hasCanister = level.getBlockEntity(pos) instanceof ReactorBlockEntity reactor
                && !reactor.getOutputCanister().isEmpty();
        Map<Direction, VoxelShape> map = hasCanister ? SHAPES_WITH_CANISTER : SHAPES;
        return map.getOrDefault(facing, map.get(Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, TRIGGERED, CRAFTING);
    }

    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(pos));
    }

    /**
     * Reactors respond to redstone power on any side, so dust visually
     * connects from any direction.
     *
     * @param state     the block state
     * @param level     the level
     * @param pos       the block position
     * @param direction the side the dust is approaching from, or null
     * @return true: dust connects on every side
     */
    @Override
    public boolean canConnectRedstone(@NonNull BlockState state, @NonNull BlockGetter level,
                                      @NonNull BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ReactorBlockEntity(pos, state);
    }

    /** Routes goo-driven block-light emission through the BE. */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return IGooLightSource.blockEmissionFor(level, pos);
    }

    /** BE-driven emission: tell NeoForge the value depends on position so
     * the engine queries with a real BlockGetter instead of probing with
     * EmptyBlockGetter (which would return 0 and skip the chunk). */
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state,
            @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type,
                    GooBlockEntities.REACTOR.get(),
                    ReactorBlockEntity::clientTick);
        }
        return createTickerHelper(type,
                GooBlockEntities.REACTOR.get(),
                ReactorBlockEntity::serverTick);
    }

    /**
     * Right-click with item: insert a canister into the output hollow.
     *
     * @param stack     the held item
     * @param state     the block state
     * @param level     the level
     * @param pos       the block position
     * @param player    the player
     * @param hand      the hand used
     * @param hitResult the hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state,
            Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof CanisterItem)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!isHollowClick(state, pos, hitResult)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ReactorBlockEntity reactor)) {
            return InteractionResult.PASS;
        }
        return handleCanisterInteraction(reactor, stack, player, level, pos);
    }

    /**
     * Empty-hand right-click: remove the canister from the output hollow.
     *
     * @param state     the block state
     * @param level     the level
     * @param pos       the block position
     * @param player    the player
     * @param hitResult the hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {
        InteractionResult earlyOut = GooBlockInteraction.validateEmptyHand(level, pos, player);
        if (earlyOut != null) {
            return earlyOut;
        }
        if (!hitOutputSlot(state, pos, hitResult)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof ReactorBlockEntity reactor)) {
            return InteractionResult.PASS;
        }
        ItemStack removed = reactor.removeOutputCanister();
        if (removed.isEmpty()) {
            return InteractionResult.PASS;
        }
        PlayerUtils.addOrDrop(player, removed);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Drops the output canister when the block is broken.
     *
     * @param level  the level
     * @param pos    the block position
     * @param state  the block state
     * @param player the player
     * @return the block state
     */
    @Override
    public @NonNull BlockState playerWillDestroy(
            @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof ReactorBlockEntity reactor) {
            ItemStack canister = reactor.removeOutputCanister();
            if (!canister.isEmpty()) {
                popResource(level, pos, canister);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level,
                                   @NonNull BlockPos pos, @NonNull Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        boolean wasTriggered = state.getValue(TRIGGERED);
        if (powered != wasTriggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, powered), UPDATE_CLIENTS);
        }
    }
}
