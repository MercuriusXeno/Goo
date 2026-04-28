package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Permanent glow crystal left behind by glow chain marker detonation.
 * No collision, variable light level by size, breaks like a torch and
 * drops a glow blob. Attaches to any surface (floor, wall, ceiling).
 *
 * <p>Blockstate properties: FACING (6 dirs), SHAPE (bump/flat),
 * SIZE (tiny/small/medium/large).</p>
 */
public class GlowCrystalBlock extends Block {

    public static final EnumProperty<CrystalShape> SHAPE =
            EnumProperty.create("shape", CrystalShape.class);
    public static final EnumProperty<CrystalSize> SIZE =
            EnumProperty.create("size", CrystalSize.class);
    public static final EnumProperty<Direction> FACING =
            EnumProperty.create("facing", Direction.class);
    /**
     * Bump depth in block fractions (2/16).
     */
    public static final double BUMP_DEPTH = 2.0 / 16;
    /**
     * Flat depth in block fractions (matches 0.01 model).
     */
    public static final double FLAT_DEPTH = 0.01;
    /**
     * Precomputed voxel shapes keyed by [facing][shape][size].
     */
    private static final Map<Direction, Map<CrystalShape, Map<CrystalSize, VoxelShape>>> SHAPES =
            buildShapeTable();

    /**
     * Creates a glow crystal block.
     *
     * @param properties the block properties
     */
    public GlowCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(SHAPE, CrystalShape.BUMP)
                .setValue(SIZE, CrystalSize.TINY));
    }

    /**
     * Returns the light level for a given blockstate.
     *
     * @param state the block state
     * @return the light emission level (0-15)
     */
    public static int lightLevel(BlockState state) {
        return state.getValue(SIZE).lightLevel;
    }

    /**
     * Builds the full facing x shape x size to VoxelShape lookup table.
     *
     * @return the precomputed shape table
     */
    private static Map<Direction, Map<CrystalShape, Map<CrystalSize, VoxelShape>>> buildShapeTable() {
        Map<Direction, Map<CrystalShape, Map<CrystalSize, VoxelShape>>> table = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            Map<CrystalShape, Map<CrystalSize, VoxelShape>> byShape = new EnumMap<>(CrystalShape.class);
            for (CrystalShape shape : CrystalShape.values()) {
                Map<CrystalSize, VoxelShape> bySize = new EnumMap<>(CrystalSize.class);
                for (CrystalSize size : CrystalSize.values()) {
                    double depth = shape == CrystalShape.BUMP ? BUMP_DEPTH : FLAT_DEPTH;
                    bySize.put(size, shapeFor(facing, size.min, size.max, depth));
                }
                byShape.put(shape, bySize);
            }
            table.put(facing, byShape);
        }
        return table;
    }

    /**
     * Builds a voxel shape anchored to the given face.
     *
     * @param facing the surface direction
     * @param min    lateral min (block fraction)
     * @param max    lateral max (block fraction)
     * @param depth  depth from the face (block fraction)
     * @return the voxel shape
     */
    public static VoxelShape shapeFor(Direction facing, double min, double max, double depth) {
        return switch (facing.getAxis()) {
            case Y -> shapeAlongY(facing, min, max, depth);
            case Z -> shapeAlongZ(facing, min, max, depth);
            case X -> shapeAlongX(facing, min, max, depth);
        };
    }

    /**
     * Builds a shape anchored to the up or down face.
     *
     * @param facing vertical surface direction (UP or DOWN)
     * @param min    lateral min in block fractions
     * @param max    lateral max in block fractions
     * @param depth  depth from the face in block fractions
     * @return the Y-axis-anchored voxel shape
     */
    private static VoxelShape shapeAlongY(Direction facing, double min, double max, double depth) {
        return facing == Direction.UP
                ? Shapes.box(min, 0, min, max, depth, max)
                : Shapes.box(min, 1 - depth, min, max, 1, max);
    }

    /**
     * Builds a shape anchored to the north or south face.
     *
     * @param facing horizontal Z-axis direction (NORTH or SOUTH)
     * @param min    lateral min in block fractions
     * @param max    lateral max in block fractions
     * @param depth  depth from the face in block fractions
     * @return the Z-axis-anchored voxel shape
     */
    private static VoxelShape shapeAlongZ(Direction facing, double min, double max, double depth) {
        return facing == Direction.NORTH
                ? Shapes.box(min, min, 0, max, max, depth)
                : Shapes.box(min, min, 1 - depth, max, max, 1);
    }

    /**
     * Builds a shape anchored to the west or east face.
     *
     * @param facing horizontal X-axis direction (WEST or EAST)
     * @param min    lateral min in block fractions
     * @param max    lateral max in block fractions
     * @param depth  depth from the face in block fractions
     * @return the X-axis-anchored voxel shape
     */
    private static VoxelShape shapeAlongX(Direction facing, double min, double max, double depth) {
        return facing == Direction.WEST
                ? Shapes.box(0, min, min, depth, max, max)
                : Shapes.box(1 - depth, min, min, 1, max, max);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, SIZE);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state,
                                           @NonNull BlockGetter level, @NonNull BlockPos pos,
                                           @NonNull CollisionContext ctx) {
        return SHAPES
                .get(state.getValue(FACING))
                .get(state.getValue(SHAPE))
                .get(state.getValue(SIZE));
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state,
                                                    @NonNull BlockGetter level, @NonNull BlockPos pos,
                                                    @NonNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return defaultBlockState().setValue(FACING, face);
    }

    /**
     * Checks that the supporting surface is solid.
     */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level,
                                 @NonNull BlockPos pos) {
        Direction face = state.getValue(FACING);
        BlockPos support = pos.relative(face.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, face);
    }

    /**
     * Breaks when the support block is removed (like torches).
     */
    @Override
    public void onNeighborChange(@NonNull BlockState state, @NonNull LevelReader level,
                                 @NonNull BlockPos pos, @NonNull BlockPos neighbor) {
        if (!canSurvive(state, level, pos) && level instanceof ServerLevel sl) {
            sl.destroyBlock(pos, true);
        }
    }

    @Override
    protected @NonNull List<ItemStack> getDrops(@NonNull BlockState state,
                                                LootParams.@NonNull Builder builder) {
        int count = state.getValue(SIZE).ordinal() + 1;
        return List.of(BlobStacks.createBlobStack(GooType.GLOW, count));
    }

    /**
     * Crystal shape: bump has 2px depth, flat has none.
     */
    public enum CrystalShape implements StringRepresentable {
        BUMP("bump"), FLAT("flat");

        private final String name;

        CrystalShape(String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    /**
     * Crystal size determines light level and lateral extent.
     */
    public enum CrystalSize implements StringRepresentable {
        TINY("tiny", 6, 5.0 / 16, 11.0 / 16),
        SMALL("small", 9, 4.0 / 16, 12.0 / 16),
        MEDIUM("medium", 12, 3.0 / 16, 13.0 / 16),
        LARGE("large", 15, 2.0 / 16, 14.0 / 16);

        /**
         * Light emission level.
         */
        public final int lightLevel;
        /**
         * Model min coordinate on the lateral axes (block fraction).
         */
        public final double min;
        /**
         * Model max coordinate on the lateral axes (block fraction).
         */
        public final double max;
        private final String name;

        CrystalSize(String name, int lightLevel, double min, double max) {
            this.name = name;
            this.lightLevel = lightLevel;
            this.min = min;
            this.max = max;
        }

        /**
         * Returns the size matching a 1-based stack count (clamped).
         *
         * @param stacks the 1-based stack count
         * @return the crystal size for that count
         */
        public static CrystalSize fromStacks(int stacks) {
            int idx = Math.max(0, Math.min(stacks - 1, values().length - 1));
            return values()[idx];
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }
}
