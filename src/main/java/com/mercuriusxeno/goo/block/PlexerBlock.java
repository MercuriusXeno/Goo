package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.PlayerUtils;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

    /** Ticks between redstone rising edge and craft attempt, matching vanilla Crafter. */
    private static final int CRAFTING_TICK_DELAY = 4;

    /** How long the crafting visual persists after a successful reconstitution. */
    private static final int CRAFTING_DISPLAY_TICKS = 6;

    /** VoxelShapes per facing, rotated from the south-facing base shape. */
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    /** Cutaway volume in model space (south-facing): x∈[5,11], y∈[8,13], z∈[12,16]. */
    private static final double CUTAWAY_MIN_X = 5.0 / 16.0;
    private static final double CUTAWAY_MAX_X = 11.0 / 16.0;
    private static final double CUTAWAY_MIN_Y = 8.0 / 16.0;
    private static final double CUTAWAY_MAX_Y = 13.0 / 16.0;
    private static final double CUTAWAY_MIN_Z = 12.0 / 16.0;

    public PlexerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HAS_GASKET, false)
            .setValue(TRIGGERED, false)
            .setValue(CRAFTING, false));
    }

    /** Builds VoxelShapes for all four horizontal facings from the south-facing base. */
    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        VoxelShape south = southShape();
        map.put(Direction.SOUTH, south);
        map.put(Direction.WEST, rotateShapeCw(south, 1));
        map.put(Direction.NORTH, rotateShapeCw(south, 2));
        map.put(Direction.EAST, rotateShapeCw(south, 3));
        return map;
    }

    /** South-facing composite: top slab, base slab, middle backwall, and two cheeks. */
    private static VoxelShape southShape() {
        VoxelShape top = Block.box(0, 13, 0, 16, 16, 16);
        VoxelShape base = Block.box(0, 0, 0, 16, 8, 16);
        VoxelShape middle = Block.box(0, 8, 0, 16, 13, 12);
        VoxelShape cheekWest = Block.box(0, 8, 12, 5, 13, 16);
        VoxelShape cheekEast = Block.box(11, 8, 12, 16, 13, 16);
        return Shapes.or(top, base, middle, cheekWest, cheekEast);
    }

    /**
     * Rotates a VoxelShape clockwise around the Y axis by the given number
     * of 90-degree steps. Decomposes into AABB parts and reassembles.
     */
    private static VoxelShape rotateShapeCw(VoxelShape shape, int steps) {
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
            result[0] = Shapes.or(result[0], Block.box(
                rx1 * 16, y1 * 16, rz1 * 16,
                rx2 * 16, y2 * 16, rz2 * 16));
        });
        return result[0];
    }

    @Override
    protected @NonNull VoxelShape getShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_GASKET, TRIGGERED, CRAFTING);
    }

    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(pos));
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new PlexerBlockEntity(pos, state);
    }

    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // Let canister placement pass through to CanisterItem.useOn
        if (stack.getItem() instanceof CanisterItem) return InteractionResult.PASS;

        if (!isCutawayClick(state, pos, hitResult)) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlexerBlockEntity plexer)) return InteractionResult.PASS;

        // Set target item (observer slot)
        if (!stack.isEmpty()) {
            plexer.setTargetItem(stack.copy());
            player.sendOverlayMessage(
                Component.literal("Target: " + stack.getHoverName().getString()));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!isCutawayClick(state, pos, hitResult)) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlexerBlockEntity plexer)) return InteractionResult.PASS;

        // Clear target if one is set
        if (!plexer.getTargetItem().isEmpty()) {
            player.sendOverlayMessage(
                Component.literal("Target cleared"));
            plexer.setTargetItem(ItemStack.EMPTY);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    /**
     * Returns true if the hit lands on any of the 5 cutaway interior faces:
     * back wall, left/right cheek inners, top slab underside, base slab top.
     * Transforms hit coords to model space and checks against cutaway volume.
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

    /** Converts world-local XZ to south-facing model X by reversing facing rotation. */
    private static double toModelX(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case SOUTH -> hitX;
            case NORTH -> 1.0 - hitX;
            case EAST  -> hitZ;
            case WEST  -> 1.0 - hitZ;
            default    -> hitX;
        };
    }

    /** Converts world-local XZ to south-facing model Z by reversing facing rotation. */
    private static double toModelZ(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case SOUTH -> hitZ;
            case NORTH -> 1.0 - hitZ;
            case EAST  -> 1.0 - hitX;
            case WEST  -> hitX;
            default    -> hitZ;
        };
    }

    /** Returns true if model-space coords fall within the cutaway volume. */
    private static boolean isInCutaway(double modelX, double modelY, double modelZ) {
        return modelX >= CUTAWAY_MIN_X && modelX <= CUTAWAY_MAX_X
            && modelY >= CUTAWAY_MIN_Y && modelY <= CUTAWAY_MAX_Y
            && modelZ >= CUTAWAY_MIN_Z;
    }

    // -- Redstone-triggered reconstitution --

    /** Detects rising/falling redstone edges; schedules a craft attempt on rising. */
    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level,
            @NonNull BlockPos pos, @NonNull Block neighborBlock,
            @Nullable Orientation orientation, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        boolean wasTriggered = state.getValue(TRIGGERED);
        if (powered && !wasTriggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, CRAFTING_TICK_DELAY);
        } else if (!powered && wasTriggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, false), Block.UPDATE_CLIENTS);
        }
    }

    /** Fires one reconstitution attempt, or clears the crafting visual on the second tick. */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level,
            @NonNull BlockPos pos, @NonNull RandomSource random) {
        // Second tick: clear the crafting display
        if (state.getValue(CRAFTING)) {
            level.setBlock(pos, state.setValue(CRAFTING, false), Block.UPDATE_CLIENTS);
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PlexerBlockEntity plexer)) return;

        ItemStack result = plexer.tryReconstitute();
        if (!result.isEmpty()) {
            ejectFromCutaway(level, pos, state, result);
            level.setBlock(pos, state.setValue(CRAFTING, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, CRAFTING_DISPLAY_TICKS);
        }
    }

    /** Spawns an ItemEntity at the cutaway opening with velocity in the facing direction. */
    private static void ejectFromCutaway(ServerLevel level, BlockPos pos, BlockState state, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        // Cutaway center in model space (south-facing): x=8/16, y=10.5/16, z=15/16
        double cx = 8.0 / 16.0;
        double cy = 10.5 / 16.0;
        double cz = 15.0 / 16.0;
        // Rotate to world space using facing
        double wx = facing == Direction.SOUTH ? cx
                  : facing == Direction.NORTH ? 1.0 - cx
                  : facing == Direction.EAST  ? 1.0 - cz
                  :                             cz;
        double wz = facing == Direction.SOUTH ? cz
                  : facing == Direction.NORTH ? 1.0 - cz
                  : facing == Direction.EAST  ? cx
                  :                             1.0 - cx;
        ItemEntity entity = new ItemEntity(level,
            pos.getX() + wx, pos.getY() + cy, pos.getZ() + wz, stack);
        // Small velocity outward from cutaway face
        double speed = 0.15;
        entity.setDeltaMovement(
            facing.getStepX() * speed,
            0.05,
            facing.getStepZ() * speed);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    // -- Block break drops --

    /**
     * Drops the target item and gasket (if installed) before the block is removed.
     * Canisters are external (CanisterBlock above) and drop independently.
     */
    @Override
    public @NonNull BlockState playerWillDestroy(
            @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PlexerBlockEntity plexer) {
                ItemStack target = plexer.getTargetItem();
                if (!target.isEmpty()) {
                    Block.popResource(level, pos, target.copy());
                }
            }
            if (state.getValue(HAS_GASKET)) {
                Block.popResource(level, pos,
                    new ItemStack(com.mercuriusxeno.goo.registry.GooItems.CHORAL_GASKET.get()));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
