package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.CutawayInteractionHelper;
import com.mercuriusxeno.goo.block.CutawayShapeHelper;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
 * Plexer block: reconstitutes items from goo in attached canisters.
 * Faces the player on placement. All 9 copper fittings are always
 * available on the top face (no slot constraints).
 */
public class PlexerBlock extends BaseEntityBlock {

    /**
     * Horizontal facing direction - orients the face opening.
     */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /**
     * Whether the plexer is currently receiving a redstone signal.
     */
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    /**
     * Whether the plexer is actively showing its crafting animation.
     */
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;
    public static final MapCodec<PlexerBlock> CODEC = simpleCodec(PlexerBlock::new);
    /**
     * Ticks between redstone rising edge and craft attempt, matching vanilla Crafter.
     */
    private static final int CRAFTING_TICK_DELAY = 4;

    /**
     * How long the crafting visual persists after a successful reconstitution.
     */
    private static final int CRAFTING_DISPLAY_TICKS = 6;

    // -- Shape pieces (south-facing, cutaway on north face z=[0,4]) --
    /**
     * Top slab of the plexer.
     */
    private static final VoxelShape SOUTH_TOP = box(0, 13, 0, 16, 16, 16);
    /**
     * Base slab of the plexer.
     */
    private static final VoxelShape SOUTH_BASE = box(0, 0, 0, 16, 8, 16);
    /**
     * Middle backwall of the plexer (behind the cutaway).
     */
    private static final VoxelShape SOUTH_MIDDLE = box(0, 8, 4, 16, 13, 16);
    /**
     * West cheek of the plexer cutaway.
     */
    private static final VoxelShape SOUTH_CHEEK_WEST = box(0, 8, 0, 5, 13, 4);
    /**
     * East cheek of the plexer cutaway.
     */
    private static final VoxelShape SOUTH_CHEEK_EAST = box(11, 8, 0, 16, 13, 4);
    /**
     * Composite south-facing shape.
     */
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            SOUTH_TOP, SOUTH_BASE, SOUTH_MIDDLE, SOUTH_CHEEK_WEST, SOUTH_CHEEK_EAST);

    /**
     * VoxelShapes per facing, rotated from the south-facing base shape.
     */
    private static final Map<Direction, VoxelShape> SHAPES = CutawayShapeHelper.buildShapes(SOUTH_SHAPE);

    /**
     * Creates a plexer block and registers default blockstate values.
     *
     * @param properties the block properties
     */
    public PlexerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TRIGGERED, false)
                .setValue(CRAFTING, false));
    }

    /**
     * Returns the facing-rotated outline shape for the plexer.
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
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.SOUTH));
    }

    /**
     * Registers all plexer blockstate properties.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, TRIGGERED, CRAFTING);
    }

    /**
     * Places the plexer facing the player, with initial redstone state.
     *
     * @param context the collision context
     * @return the state for placement
     */
    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(pos));
    }

    /**
     * Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * Returns MODEL render shape since the plexer uses a block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Creates the plexer block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new PlexerBlockEntity(pos, state);
    }

    /**
     * Handles item-in-hand interactions: sets or passes through canister placement.
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
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (CutawayInteractionHelper.shouldPassItemInteraction(stack, state, pos, hitResult)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof PlexerBlockEntity plexer)) {
            return InteractionResult.PASS;
        }
        if (!stack.isEmpty()) {
            return CutawayInteractionHelper.applyTargetItem(plexer, player, stack);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Empty-hand interaction: clears the target item if the cutaway was clicked.
     *
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!CutawayInteractionHelper.isCutawayClick(state, pos, hitResult)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof PlexerBlockEntity plexer)) {
            return InteractionResult.PASS;
        }
        if (!plexer.getTargetItem().isEmpty()) {
            return CutawayInteractionHelper.clearTargetItem(plexer, player);
        }
        return InteractionResult.PASS;
    }

    // -- Redstone-triggered reconstitution --

    /**
     * Detects rising/falling redstone edges; schedules a craft attempt on rising.
     *
     * @param state         the block state
     * @param level         the current level
     * @param pos           the block position
     * @param neighborBlock the neighbor block that changed
     * @param orientation   the redstone orientation, or null
     * @param movedByPiston true if moved by a piston
     */
    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level,
                                   @NonNull BlockPos pos, @NonNull Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        boolean wasTriggered = state.getValue(TRIGGERED);
        if (powered && !wasTriggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, true), UPDATE_CLIENTS);
            level.scheduleTick(pos, this, CRAFTING_TICK_DELAY);
        } else if (!powered && wasTriggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, false), UPDATE_CLIENTS);
        }
    }

    /**
     * Fires one reconstitution attempt, or clears the crafting visual on the second tick.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param random the random source
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level,
                        @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (state.getValue(CRAFTING)) {
            level.setBlock(pos, state.setValue(CRAFTING, false), UPDATE_CLIENTS);
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PlexerBlockEntity plexer) {
            attemptReconstitution(plexer, level, pos, state);
        }
    }

    /**
     * Tries to reconstitute an item and eject it from the cutaway if successful.
     *
     * @param plexer the plexer block entity
     * @param level  the server level
     * @param pos    the block position
     * @param state  the block state
     */
    private void attemptReconstitution(PlexerBlockEntity plexer, ServerLevel level,
                                       BlockPos pos, BlockState state) {
        ItemStack result = plexer.tryReconstitute();
        if (!result.isEmpty()) {
            CutawayInteractionHelper.ejectFromHatch(level, pos, state, result);
            level.setBlock(pos, state.setValue(CRAFTING, true), UPDATE_CLIENTS);
            level.scheduleTick(pos, this, CRAFTING_DISPLAY_TICKS);
        }
    }

    // -- Block break drops --

    /**
     * Drops the target item before the block is removed.
     * Canisters are external (CanisterBlock above) and drop independently.
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
            dropTargetItem(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Drops the plexer's target item if one is set.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void dropTargetItem(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlexerBlockEntity plexer)) {
            return;
        }
        ItemStack target = plexer.getTargetItem();
        if (!target.isEmpty()) {
            popResource(level, pos, target.copy());
        }
    }
}
