package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.CanisterItem;
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
import java.util.Map;

/**
 * Reactor block: consumes goo from 4 corner canisters on top and
 * produces output into a canister in the front hollow. Redstone
 * halts processing. Faces the player on placement.
 */
public class ReactorBlock extends BaseEntityBlock {

    public static final MapCodec<ReactorBlock> CODEC = simpleCodec(ReactorBlock::new);

    /** Horizontal facing - orients the front hollow. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    /** Whether the reactor is receiving a redstone signal. */
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

    /** Whether the reactor is actively processing a reaction. */
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;

    /** South-facing composite shape (hollow from z=0 to z=6, x=5-11, y=1-15). */
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            box(0, 0, 0, 16, 16, 16),
            Shapes.empty());

    /** VoxelShapes per facing. Full cube for now since the model handles the visual hollow. */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, box(0, 0, 0, 16, 16, 16),
            Direction.SOUTH, box(0, 0, 0, 16, 16, 16),
            Direction.EAST, box(0, 0, 0, 16, 16, 16),
            Direction.WEST, box(0, 0, 0, 16, 16, 16));

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

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state,
            @NonNull BlockGetter level, @NonNull BlockPos pos,
            @NonNull CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.SOUTH));
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
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(pos));
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state,
            @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) { return null; }
        return createTickerHelper(type,
                com.mercuriusxeno.goo.registry.GooBlockEntities.REACTOR.get(),
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
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }
        if (!(stack.getItem() instanceof CanisterItem)) { return InteractionResult.PASS; }
        if (!(level.getBlockEntity(pos) instanceof ReactorBlockEntity reactor)) {
            return InteractionResult.PASS;
        }
        if (!reactor.insertOutputCanister(stack)) { return InteractionResult.PASS; }
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
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
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }
        if (!(level.getBlockEntity(pos) instanceof ReactorBlockEntity reactor)) {
            return InteractionResult.PASS;
        }
        ItemStack removed = reactor.removeOutputCanister();
        if (removed.isEmpty()) { return InteractionResult.PASS; }
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
            if (!canister.isEmpty()) { popResource(level, pos, canister); }
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
