package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.CanisterItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
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
import java.util.EnumMap;
import java.util.Map;

/**
 * Plexer block: reconstitutes items from goo in attached canisters.
 * Faces the player on placement. All 9 copper fittings are always
 * available on the top face (no slot constraints).
 */
public class PlexerBlock extends BaseEntityBlock {

    public static final MapCodec<PlexerBlock> CODEC = simpleCodec(PlexerBlock::new);

    /** Horizontal facing direction - orients the face opening. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    /** Whether a choral gasket is installed on this plexer. */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");

    /** Whether the plexer is currently receiving a redstone signal. */
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

    /** Whether the plexer is actively showing its crafting animation. */
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;

    /** Overlay prefix for target-set feedback. */
    private static final String TARGET_PREFIX = "Target: ";
    /** Overlay message when target is cleared. */
    private static final String TARGET_CLEARED = "Target cleared";

    /** Ticks between redstone rising edge and craft attempt, matching vanilla Crafter. */
    private static final int CRAFTING_TICK_DELAY = 4;

    /** How long the crafting visual persists after a successful reconstitution. */
    private static final int CRAFTING_DISPLAY_TICKS = 6;

    /** Pixels per block for coordinate conversion. */
    private static final double PIXELS_PER_BLOCK = 16;
    /** 180-degree rotation (2 CW steps). */
    private static final int ROTATION_HALF = 2;
    /** 270-degree rotation (3 CW steps). */
    private static final int ROTATION_THREE_QUARTER = 3;

    // -- Eject point (model space, south-facing) --
    /** Cutaway center X in block-relative coords. */
    private static final double EJECT_CENTER_X = 8.0 / 16.0;
    /** Cutaway center Y in block-relative coords. */
    private static final double EJECT_CENTER_Y = 10.5 / 16.0;
    /** Cutaway opening Z in block-relative coords. */
    private static final double EJECT_CENTER_Z = 15.0 / 16.0;
    /** Ejected item outward speed. */
    private static final double EJECT_SPEED = 0.15;
    /** Ejected item upward velocity. */
    private static final double EJECT_LIFT = 0.05;

    // -- Shape pieces (south-facing) --
    /** Top slab of the plexer. */
    private static final VoxelShape SOUTH_TOP = box(0, 13, 0, 16, 16, 16);
    /** Base slab of the plexer. */
    private static final VoxelShape SOUTH_BASE = box(0, 0, 0, 16, 8, 16);
    /** Middle backwall of the plexer. */
    private static final VoxelShape SOUTH_MIDDLE = box(0, 8, 0, 16, 13, 12);
    /** West cheek of the plexer cutaway. */
    private static final VoxelShape SOUTH_CHEEK_WEST = box(0, 8, 12, 5, 13, 16);
    /** East cheek of the plexer cutaway. */
    private static final VoxelShape SOUTH_CHEEK_EAST = box(11, 8, 12, 16, 13, 16);
    /** Composite south-facing shape. */
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
        SOUTH_TOP, SOUTH_BASE, SOUTH_MIDDLE, SOUTH_CHEEK_WEST, SOUTH_CHEEK_EAST);

    /** VoxelShapes per facing, rotated from the south-facing base shape. */
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    /** Cutaway volume in model space (south-facing): x∈[5,11], y∈[8,13], z∈[12,16]. */
    private static final double CUTAWAY_MIN_X = 5.0 / 16.0;
    private static final double CUTAWAY_MAX_X = 11.0 / 16.0;
    private static final double CUTAWAY_MIN_Y = 8.0 / 16.0;
    private static final double CUTAWAY_MAX_Y = 13.0 / 16.0;
    private static final double CUTAWAY_MIN_Z = 12.0 / 16.0;

    /** Creates a plexer block and registers default blockstate values.
     *
     * @param properties the block properties
     */
    public PlexerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HAS_GASKET, false)
            .setValue(TRIGGERED, false)
            .setValue(CRAFTING, false));
    }

    /** Builds VoxelShapes for all four horizontal facings from the south-facing base.
     *
     * @return the new shapes
     */
    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, SOUTH_SHAPE);
        map.put(Direction.WEST, rotateShapeCw(SOUTH_SHAPE, 1));
        map.put(Direction.NORTH, rotateShapeCw(SOUTH_SHAPE, ROTATION_HALF));
        map.put(Direction.EAST, rotateShapeCw(SOUTH_SHAPE, ROTATION_THREE_QUARTER));
        return map;
    }

    /**
     * Rotates a VoxelShape clockwise around the Y axis by the given number
     * of 90-degree steps. Decomposes into AABB parts and reassembles.
     *
     * @param shape the VoxelShape to rotate
     * @param steps number of 90-degree clockwise steps
     * @return the voxel shape
     */
    private static VoxelShape rotateShapeCw(VoxelShape shape, int steps) {
        if (steps == 0) { return shape; }
        VoxelShape[] result = { Shapes.empty() };
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double rx1 = x1;
            double rz1 = z1;
            double rx2 = x2;
            double rz2 = z2;
            for (int s = 0; s < steps; s++) {
                double tmpX1 = 1.0 - rz2;
                double tmpZ1 = rx1;
                double tmpX2 = 1.0 - rz1;
                double tmpZ2 = rx2;
                rx1 = tmpX1;
                rz1 = tmpZ1;
                rx2 = tmpX2;
                rz2 = tmpZ2;
            }
            result[0] = Shapes.or(result[0], box(
                rx1 * PIXELS_PER_BLOCK, y1 * PIXELS_PER_BLOCK, rz1 * PIXELS_PER_BLOCK,
                rx2 * PIXELS_PER_BLOCK, y2 * PIXELS_PER_BLOCK, rz2 * PIXELS_PER_BLOCK));
        });
        return result[0];
    }

    /** Returns the facing-rotated outline shape for the plexer.
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

    /** Registers all plexer blockstate properties.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_GASKET, TRIGGERED, CRAFTING);
    }

    /** Places the plexer facing the player, with initial redstone state.
     *
     * @param context the collision context
     * @return the state for placement
     */
    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(pos));
    }

    /** Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    /** Returns MODEL render shape since the plexer uses a block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) { return RenderShape.MODEL; }

    /** Creates the plexer block entity for this position.
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

    /** Handles item-in-hand interactions: sets or passes through canister placement.
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
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }

        // Let canister placement pass through to CanisterItem.useOn
        if (stack.getItem() instanceof CanisterItem) { return InteractionResult.PASS; }

        if (!isCutawayClick(state, pos, hitResult)) { return InteractionResult.PASS; }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlexerBlockEntity plexer)) { return InteractionResult.PASS; }

        // Set target item (observer slot)
        if (!stack.isEmpty()) {
            plexer.setTargetItem(stack.copy());
            player.sendOverlayMessage(
                Component.literal(TARGET_PREFIX + stack.getHoverName().getString()));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Empty-hand interaction: clears the target item if the cutaway was clicked.
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
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }

        if (!isCutawayClick(state, pos, hitResult)) { return InteractionResult.PASS; }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlexerBlockEntity plexer)) { return InteractionResult.PASS; }

        // Clear target if one is set
        if (!plexer.getTargetItem().isEmpty()) {
            player.sendOverlayMessage(
                Component.literal(TARGET_CLEARED));
            plexer.setTargetItem(ItemStack.EMPTY);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    /**
     * Returns true if the hit lands on any of the 5 cutaway interior faces:
     * back wall, left/right cheek inners, top slab underside, base slab top.
     * Transforms hit coords to model space and checks against cutaway volume.
     *
     * @param state the block state
     * @param pos   the block position
     * @param hit   the ray trace hit result
     * @return true if cutaway click
     */
    private static boolean isCutawayClick(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        double hitX = hit.getLocation().x - pos.getX();
        double hitY = hit.getLocation().y - pos.getY();
        double hitZ = hit.getLocation().z - pos.getZ();
        double modelX = toModelX(facing, hitX, hitZ);
        double modelZ = toModelZ(facing, hitX, hitZ);
        return isInCutaway(modelX, hitY, modelZ);
    }

    /** Converts world-local XZ to south-facing model X by reversing facing rotation.
     *
     * @param facing the facing direction
     * @param hitX   block-local X hit coordinate
     * @param hitZ   block-local Z hit coordinate
     * @return the double value
     */
    private static double toModelX(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case SOUTH -> hitX;
            case NORTH -> 1.0 - hitX;
            case EAST  -> hitZ;
            case WEST  -> 1.0 - hitZ;
            default    -> hitX;
        };
    }

    /** Converts world-local XZ to south-facing model Z by reversing facing rotation.
     *
     * @param facing the facing direction
     * @param hitX   block-local X hit coordinate
     * @param hitZ   block-local Z hit coordinate
     * @return the double value
     */
    private static double toModelZ(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case SOUTH -> hitZ;
            case NORTH -> 1.0 - hitZ;
            case EAST  -> 1.0 - hitX;
            case WEST  -> hitX;
            default    -> hitZ;
        };
    }

    /** Returns true if model-space coords fall within the cutaway volume.
     *
     * @param modelX model-space X coordinate
     * @param modelY model-space Y coordinate
     * @param modelZ model-space Z coordinate
     * @return true if in cutaway
     */
    private static boolean isInCutaway(double modelX, double modelY, double modelZ) {
        return isInCutawayXY(modelX, modelY) && modelZ >= CUTAWAY_MIN_Z;
    }

    /**
     * Returns true if model-space X and Y fall within the cutaway horizontal and vertical range.
     *
     * @param modelX model-space X coordinate
     * @param modelY model-space Y coordinate
     * @return true if within cutaway X/Y bounds
     */
    private static boolean isInCutawayXY(double modelX, double modelY) {
        return modelX >= CUTAWAY_MIN_X && modelX <= CUTAWAY_MAX_X
            && modelY >= CUTAWAY_MIN_Y && modelY <= CUTAWAY_MAX_Y;
    }

    // -- Redstone-triggered reconstitution --

    /** Detects rising/falling redstone edges; schedules a craft attempt on rising.
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

    /** Fires one reconstitution attempt, or clears the crafting visual on the second tick.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param random the random source
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level,
            @NonNull BlockPos pos, @NonNull RandomSource random) {
        // Second tick: clear the crafting display
        if (state.getValue(CRAFTING)) {
            level.setBlock(pos, state.setValue(CRAFTING, false), UPDATE_CLIENTS);
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlexerBlockEntity plexer)) { return; }

        ItemStack result = plexer.tryReconstitute();
        if (!result.isEmpty()) {
            ejectFromCutaway(level, pos, state, result);
            level.setBlock(pos, state.setValue(CRAFTING, true), UPDATE_CLIENTS);
            level.scheduleTick(pos, this, CRAFTING_DISPLAY_TICKS);
        }
    }

    /** Spawns an ItemEntity at the cutaway opening with velocity in the facing direction.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param stack the item stack
     */
    private static void ejectFromCutaway(ServerLevel level, BlockPos pos, BlockState state, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        // Rotate to world space using facing
        double wx = facing == Direction.SOUTH ? EJECT_CENTER_X
                  : facing == Direction.NORTH ? 1.0 - EJECT_CENTER_X
                  : facing == Direction.EAST  ? 1.0 - EJECT_CENTER_Z
                  :                             EJECT_CENTER_Z;
        double wz = facing == Direction.SOUTH ? EJECT_CENTER_Z
                  : facing == Direction.NORTH ? 1.0 - EJECT_CENTER_Z
                  : facing == Direction.EAST  ? EJECT_CENTER_X
                  :                             1.0 - EJECT_CENTER_X;
        ItemEntity entity = new ItemEntity(level,
            pos.getX() + wx, pos.getY() + EJECT_CENTER_Y, pos.getZ() + wz, stack);
        entity.setDeltaMovement(
            facing.getStepX() * EJECT_SPEED,
            EJECT_LIFT,
            facing.getStepZ() * EJECT_SPEED);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    // -- Block break drops --

    /**
     * Drops the target item and gasket (if installed) before the block is removed.
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
            dropGasket(level, pos, state);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Drops the plexer's target item if one is set.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void dropTargetItem(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlexerBlockEntity plexer)) { return; }
        ItemStack target = plexer.getTargetItem();
        if (!target.isEmpty()) {
            popResource(level, pos, target.copy());
        }
    }

    /** Drops a gasket item if one is installed.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     */
    private void dropGasket(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(HAS_GASKET)) {
            popResource(level, pos,
                new ItemStack(com.mercuriusxeno.goo.registry.GooItems.CHORAL_GASKET.get()));
        }
    }
}
