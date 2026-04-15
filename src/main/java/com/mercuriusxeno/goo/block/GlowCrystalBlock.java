package com.mercuriusxeno.goo.block;

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

    /** Precomputed voxel shapes keyed by [facing][shape][size]. */
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, SIZE);
    }

    // ── Light level ───────────────────────────────────────────────────

    /**
     * Returns the light level for a given blockstate.
     *
     * @param state the block state
     * @return the light emission level (0-15)
     */
    public static int lightLevel(BlockState state) {
        return state.getValue(SIZE).lightLevel;
    }

    // ── Shapes ────────────────────────────────────────────────────────

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

    // ── Placement ─────────────────────────────────────────────────────

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return defaultBlockState().setValue(FACING, face);
    }

    /** Checks that the supporting surface is solid. */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level,
            @NonNull BlockPos pos) {
        Direction face = state.getValue(FACING);
        BlockPos support = pos.relative(face.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, face);
    }

    /** Breaks when the support block is removed (like torches). */
    @Override
    public void onNeighborChange(@NonNull BlockState state, @NonNull LevelReader level,
            @NonNull BlockPos pos, @NonNull BlockPos neighbor) {
        if (!canSurvive(state, level, pos) && level instanceof ServerLevel sl) {
            sl.destroyBlock(pos, true);
        }
    }

    // ── Drops ─────────────────────────────────────────────────────────

    @Override
    protected @NonNull List<ItemStack> getDrops(@NonNull BlockState state,
            LootParams.@NonNull Builder builder) {
        int count = state.getValue(SIZE).ordinal() + 1;
        return List.of(BlobStacks.createBlobStack(GooType.GLOW, count));
    }

    // ── Enums ─────────────────────────────────────────────────────────

    /** Crystal shape: bump has 2px depth, flat has none. */
    public enum CrystalShape implements StringRepresentable {
        BUMP("bump"), FLAT("flat");

        private final String name;

        CrystalShape(String name) { this.name = name; }

        @Override
        public @NonNull String getSerializedName() { return name; }
    }

    /** Crystal size determines light level and lateral extent. */
    public enum CrystalSize implements StringRepresentable {
        TINY("tiny", 6, 5, 11),
        SMALL("small", 9, 4, 12),
        MEDIUM("medium", 12, 3, 13),
        LARGE("large", 15, 2, 14);

        private final String name;
        /** Light emission level. */
        public final int lightLevel;
        /** Model min coordinate on the lateral axes (in 16ths). */
        public final int min;
        /** Model max coordinate on the lateral axes (in 16ths). */
        public final int max;

        CrystalSize(String name, int lightLevel, int min, int max) {
            this.name = name;
            this.lightLevel = lightLevel;
            this.min = min;
            this.max = max;
        }

        @Override
        public @NonNull String getSerializedName() { return name; }

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
    }

    // ── Shape table construction ──────────────────────────────────────

    /** Bump model depth in 16ths. */
    private static final int BUMP_DEPTH = 2;
    /** Full block extent in 16ths (Minecraft's voxel subdivision). */
    private static final int BLOCK_EXTENT = 16;


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
                    bySize.put(size, computeShape(facing, shape, size));
                }
                byShape.put(shape, bySize);
            }
            table.put(facing, byShape);
        }
        return table;
    }

    /**
     * Computes a single voxel shape for the given combination.
     * The base shape (floor-placed, facing UP) is rotated for other faces.
     *
     * @param facing the surface direction
     * @param shape  bump or flat
     * @param size   crystal size tier
     * @return the computed voxel shape
     */
    private static VoxelShape computeShape(Direction facing, CrystalShape shape, CrystalSize size) {
        int depth = shape == CrystalShape.BUMP ? BUMP_DEPTH : 1;
        int min = size.min;
        int max = size.max;
        return rotateFloorShape(facing, min, max, depth);
    }

    /**
     * Rotates a floor-anchored box (y: 0..depth, xz: min..max) to the given face.
     *
     * @param facing the surface direction
     * @param min    lateral min coordinate in 16ths
     * @param max    lateral max coordinate in 16ths
     * @param depth  depth in 16ths from the anchored face
     * @return the rotated voxel shape
     */
    private static VoxelShape rotateFloorShape(Direction facing, int min, int max, int depth) {
        return switch (facing) {
            case UP    -> Block.box(min, 0, min, max, depth, max);
            case DOWN  -> Block.box(min, BLOCK_EXTENT - depth, min, max, BLOCK_EXTENT, max);
            case NORTH -> Block.box(min, min, 0, max, max, depth);
            case SOUTH -> Block.box(min, min, BLOCK_EXTENT - depth, max, max, BLOCK_EXTENT);
            case WEST  -> Block.box(0, min, min, depth, max, max);
            case EAST  -> Block.box(BLOCK_EXTENT - depth, min, min, BLOCK_EXTENT, max, max);
        };
    }
}
