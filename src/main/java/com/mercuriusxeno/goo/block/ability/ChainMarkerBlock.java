package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.ChainBehaviors;
import com.mercuriusxeno.goo.ability.world.NetherBehavior;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Short-lived fuse block placed by chain world effects (Blaze, Frost,
 * Nether, Rock). No collision, no selection shape - purely visual.
 * The block entity ticks the fuse and fires the executor on expiry.
 *
 * <p>Implements {@link SimpleWaterloggedBlock} so chain markers can occupy
 * water blocks without displacing them. This is required for effects that
 * operate underwater (notably leaf goo's chain effect) and is harmless for
 * effects that do not interact with water.</p>
 */
public class ChainMarkerBlock extends AbstractEffectBlock implements SimpleWaterloggedBlock {

    /**
     * Waterlogged state property: true when this marker co-occupies a water block.
     */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final MapCodec<ChainMarkerBlock> CODEC = simpleCodec(ChainMarkerBlock::new);
    /**
     * Base ambient particle spread radius.
     */
    private static final double BASE_SPREAD = 0.25;
    /**
     * Additional spread per stack.
     */
    private static final double SPREAD_PER_STACK = 0.1;
    /**
     * Base particle count for blaze effects.
     */
    private static final int BLAZE_BASE_PARTICLES = 2;
    /**
     * Upward particle velocity for flame particles.
     */
    private static final double FLAME_RISE_SPEED = 0.02;
    /**
     * Downward particle velocity for dust plume particles.
     */
    private static final double DUST_FALL_SPEED = -0.02;
    /**
     * Lava particle spawn chance denominator (1 in N).
     */
    private static final int LAVA_CHANCE = 3;
    /**
     * Base core half-size in pixels (matches BER CORE_BASE).
     */
    private static final float SHAPE_CORE_PX = 2f;
    /**
     * Shell margin in pixels (matches BER SHELL_MARGIN).
     */
    private static final float SHAPE_SHELL_PX = 1f;
    /**
     * Core growth per stack in pixels (matches BER CORE_GROWTH * 16).
     */
    private static final float SHAPE_GROWTH_PX = 0.5f;
    /**
     * Splat width multiplier (sqrt 2).
     */
    private static final float SHAPE_SPLAT_WIDE = 1.414f;
    /**
     * Splat height multiplier (half).
     */
    private static final float SHAPE_SPLAT_THIN = 0.5f;
    /**
     * Center of a block in pixels (for shape positioning).
     */
    private static final float SHAPE_CENTER_PX = 8f;
    /**
     * Base soul particle count for nether effects.
     */
    private static final int NETHER_BASE_PARTICLES = 2;
    /**
     * Downward drift speed for soul particles.
     */
    private static final double SOUL_DRIFT_SPEED = -0.01;
    /**
     * Smoke particle spawn chance denominator (1 in N) for nether.
     */
    private static final int NETHER_SMOKE_CHANCE = 4;

    /**
     * Maps goo types to their particle emitter; types without particles are absent.
     */
    private static final Map<GooType, ParticleEmitter> PARTICLE_EMITTERS;

    static {
        Map<GooType, ParticleEmitter> m = new EnumMap<>(GooType.class);
        m.put(GooType.BLAZE, ChainMarkerBlock::spawnBlazeParticles);
        m.put(GooType.ROCK, ChainMarkerBlock::spawnRockParticles);
        m.put(GooType.NETHER, ChainMarkerBlock::spawnNetherParticles);
        m.put(GooType.METAL, ChainMarkerBlock::spawnMetalParticles);
        // Crystal uses shard cloud BER visual instead of ambient particles.
        PARTICLE_EMITTERS = Map.copyOf(m);
    }

    /**
     * Creates a chain marker block with the given properties.
     *
     * @param properties the block properties
     */
    public ChainMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false));
    }

    /**
     * Checks whether the destroy action should fall through to default block removal.
     *
     * @param level the current level
     * @param pos   the block position
     * @return true if the block should be removed normally
     */
    private static boolean shouldDeferToSuper(Level level, BlockPos pos) {
        return !(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)
                || (be.getBehavior() != null && !be.getBehavior().allowsTopOff());
    }

    /**
     * Builds a voxel shape matching the BER orb at the face boundary.
     * Splatted (squished) only when in flat mode.
     *
     * @param stacks   the current stack count
     * @param face     the placed face direction
     * @param flatMode true to apply splat deformation
     * @return the computed voxel shape
     */
    private static VoxelShape computeOrbShape(int stacks, Direction face, boolean flatMode) {
        float coreHalf = SHAPE_CORE_PX + (stacks - 1) * SHAPE_GROWTH_PX;
        float shellHalf = coreHalf + SHAPE_SHELL_PX;

        float hPerp;
        float hFace;
        if (flatMode) {
            hPerp = shellHalf * SHAPE_SPLAT_WIDE;
            hFace = shellHalf * SHAPE_SPLAT_THIN;
        } else {
            hPerp = shellHalf;
            hFace = shellHalf;
        }

        float cx = SHAPE_CENTER_PX - face.getStepX() * SHAPE_CENTER_PX;
        float cy = SHAPE_CENTER_PX - face.getStepY() * SHAPE_CENTER_PX;
        float cz = SHAPE_CENTER_PX - face.getStepZ() * SHAPE_CENTER_PX;

        float hx = face.getAxis() == Direction.Axis.X ? hFace : hPerp;
        float hy = face.getAxis() == Direction.Axis.Y ? hFace : hPerp;
        float hz = face.getAxis() == Direction.Axis.Z ? hFace : hPerp;

        return box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    /**
     * Computes a voxel shape that exactly matches the glow crystal
     * that will replace this chain marker on fuse expiry.
     *
     * @param stacks   the current stack count
     * @param face     the placed face direction
     * @param flatMode true for flat, false for bump
     * @return the crystal-matched voxel shape
     */
    private static VoxelShape computeGlowShape(int stacks, Direction face, boolean flatMode) {
        GlowCrystalBlock.CrystalSize cs = GlowCrystalBlock.CrystalSize.fromStacks(stacks);
        double depth = flatMode ? GlowCrystalBlock.FLAT_DEPTH : GlowCrystalBlock.BUMP_DEPTH;
        return GlowCrystalBlock.shapeFor(face, cs.min, cs.max, depth);
    }

    /**
     * Returns true if the marker at pos is in fuse phase and its support block is air.
     *
     * @param level the current level
     * @param pos   the marker block position
     * @return true if the marker is fusing and has no support
     */
    private static boolean isFusingMarkerWithNoSupport(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return false;
        }
        if (be.getBehavior() != null) {
            return false;
        }
        BlockPos supportPos = pos.relative(be.getPlacedFace().getOpposite());
        return level.getBlockState(supportPos).isAir();
    }

    /**
     * Removes the marker and schedules a fall to the landing position.
     *
     * @param state the block state
     * @param level the server level
     * @param pos   the marker block position
     */
    private static void initiateFall(BlockState state, ServerLevel level, BlockPos pos) {
        BlockPos landing = findLandingBelow(level, pos);
        if (landing == null) {
            return;
        }
        ChainMarkerBlockEntity be = (ChainMarkerBlockEntity) level.getBlockEntity(pos);
        level.removeBlock(pos, false);
        ChainMarkerFallScheduler.scheduleFall(level, pos, landing,
                state.getBlock(), be.getGooType(), be.getStackCount(),
                be.getMaxStacks(), be.getFuseRemaining(), be.getPlacedFace(),
                be.getBlobShape(), be.getAreaMode());
    }

    /**
     * Raycasts straight down from the marker to find the first solid
     * surface. The landing position is the air block adjacent to that
     * surface (where the marker will be re-placed).
     *
     * @param level the current level
     * @param from  the starting position
     * @return the landing block position, or null if no surface found
     */
    @Nullable
    private static BlockPos findLandingBelow(Level level, BlockPos from) {
        BlockPos.MutableBlockPos cursor = from.mutable();
        int minY = level.getMinY();
        while (cursor.getY() > minY) {
            cursor.move(Direction.DOWN);
            if (!level.getBlockState(cursor).isAir()) {
                return cursor.above().immutable();
            }
        }
        return null;
    }

    /**
     * Returns true if this marker should be unbreakable during fuse phase
     * or if its behavior supports top-off (metal, crystal).
     *
     * @param be the chain marker block entity
     * @return true if breaking should be prevented
     */
    private static boolean isProtectedFromBreaking(ChainMarkerBlockEntity be) {
        return be.getBehavior() == null || be.getBehavior().allowsTopOff();
    }

    /**
     * Drops the accumulator contents at {@code pos} when a mid-implosion
     * chain marker is broken. No-op on the client, for non-nether markers,
     * for empty accumulators, or if the block entity is missing.
     *
     * @param level the current level
     * @param pos   the marker position
     */
    private static void dropInterruptedAccumulator(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!(server.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return;
        }
        NetherBehavior nether = ChainBehaviors.findFirst(be.getBehavior(), NetherBehavior.class);
        if (nether == null) {
            return;
        }
        GooContents accumulator = nether.getAccumulator();
        if (accumulator.isEmpty()) {
            return;
        }
        BlobStacks.dropAll(accumulator, server, pos);
    }

    /**
     * Emits goo-type-specific ambient particles scaled by stack count.
     *
     * @param type   the goo type
     * @param stacks the stack count
     * @param pos    the block position
     * @param level  the current level
     * @param random the random source
     */
    private static void spawnAmbientParticles(GooType type, int stacks,
                                              BlockPos pos, Level level, RandomSource random) {
        double cx = pos.getX() + BLOCK_CENTER;
        double cy = pos.getY() + BLOCK_CENTER;
        double cz = pos.getZ() + BLOCK_CENTER;
        dispatchParticles(type, stacks, cx, cy, cz, level, random);
    }

    /**
     * Dispatches to the type-specific particle emitter.
     *
     * @param type   the goo type determining which particles to spawn
     * @param stacks the current stack count (scales particle density)
     * @param cx     block center X coordinate
     * @param cy     block center Y coordinate
     * @param cz     block center Z coordinate
     * @param level  the current level
     * @param random the random source for particle offsets
     */
    private static void dispatchParticles(GooType type, int stacks,
                                          double cx, double cy, double cz, Level level, RandomSource random) {
        ParticleEmitter emitter = PARTICLE_EMITTERS.get(type);
        if (emitter == null) {
            return;
        }
        double spread = BASE_SPREAD + SPREAD_PER_STACK * stacks;
        emitter.emit(stacks, cx, cy, cz, spread, level, random);
    }

    /**
     * Emits flame particles and occasional lava drips for blaze chain markers.
     *
     * @param stacks the current stack count (scales particle count)
     * @param cx     block center X coordinate
     * @param cy     block center Y coordinate
     * @param cz     block center Z coordinate
     * @param spread the particle offset radius
     * @param level  the current level
     * @param random the random source for particle offsets
     */
    private static void spawnBlazeParticles(int stacks, double cx, double cy, double cz,
                                            double spread, Level level, RandomSource random) {
        for (int i = 0; i < BLAZE_BASE_PARTICLES + stacks; i++) {
            emitFlameParticle(cx, cy, cz, spread, level, random);
        }
        if (random.nextInt(LAVA_CHANCE) == 0) {
            level.addParticle(ParticleTypes.LAVA, cx, cy, cz, 0, 0, 0);
        }
    }

    /**
     * Emits a single flame particle with random offset within the spread radius.
     *
     * @param cx     block center X coordinate
     * @param cy     block center Y coordinate
     * @param cz     block center Z coordinate
     * @param spread the particle offset radius
     * @param level  the current level
     * @param random the random source for particle offsets
     */
    private static void emitFlameParticle(double cx, double cy, double cz,
                                          double spread, Level level, RandomSource random) {
        double ox = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
        double oy = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
        double oz = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
        level.addParticle(ParticleTypes.FLAME, cx + ox, cy + oy, cz + oz,
                0, FLAME_RISE_SPEED, 0);
    }

    /**
     * Emits dust plume particles for rock chain markers.
     *
     * @param stacks the current stack count (scales particle count)
     * @param cx     block center X coordinate
     * @param cy     block center Y coordinate
     * @param cz     block center Z coordinate
     * @param spread the particle offset radius
     * @param level  the current level
     * @param random the random source for particle offsets
     */
    private static void spawnRockParticles(int stacks, double cx, double cy, double cz,
                                           double spread, Level level, RandomSource random) {
        for (int i = 0; i < 1 + stacks; i++) {
            double ox = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            double oy = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            double oz = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            level.addParticle(ParticleTypes.DUST_PLUME, cx + ox, cy + oy, cz + oz,
                    0, DUST_FALL_SPEED, 0);
        }
    }

    /**
     * Emits drifting soul particles and occasional smoke for nether chain markers.
     *
     * @param stacks the current stack count (scales particle count)
     * @param cx     block center X coordinate
     * @param cy     block center Y coordinate
     * @param cz     block center Z coordinate
     * @param spread the particle offset radius
     * @param level  the current level
     * @param random the random source for particle offsets
     */
    private static void spawnNetherParticles(int stacks, double cx, double cy, double cz,
                                             double spread, Level level, RandomSource random) {
        for (int i = 0; i < NETHER_BASE_PARTICLES + stacks; i++) {
            double ox = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            double oy = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            double oz = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            level.addParticle(ParticleTypes.SOUL, cx + ox, cy + oy, cz + oz,
                    0, SOUL_DRIFT_SPEED, 0);
        }
        if (random.nextInt(NETHER_SMOKE_CHANCE) == 0) {
            level.addParticle(ParticleTypes.SMOKE, cx, cy, cz, 0, 0, 0);
        }
    }

    /**
     * Emits metallic crit particles for metal chain markers.
     *
     * @param stacks the current stack count
     * @param cx     block center X coordinate
     * @param cy     block center Y coordinate
     * @param cz     block center Z coordinate
     * @param spread the particle offset radius
     * @param level  the current level
     * @param random the random source for particle offsets
     */
    private static void spawnMetalParticles(int stacks, double cx, double cy, double cz,
                                            double spread, Level level, RandomSource random) {
        if (random.nextInt(LAVA_CHANCE) == 0) {
            double ox = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            double oy = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            double oz = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            level.addParticle(ParticleTypes.CRIT, cx + ox, cy + oy, cz + oz, 0, 0, 0);
        }
    }

    /**
     * Emits enchantment sparkle particles for crystal chain markers.
     *
     * @param stacks the current stack count
     * @param cx     block center X coordinate
     * @param cy     block center Y coordinate
     * @param cz     block center Z coordinate
     * @param spread the particle offset radius
     * @param level  the current level
     * @param random the random source for particle offsets
     */
    private static void spawnCrystalParticles(int stacks, double cx, double cy, double cz,
                                              double spread, Level level, RandomSource random) {
        for (int i = 0; i < 1 + stacks; i++) {
            double ox = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            double oy = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            double oz = (random.nextDouble() - BLOCK_CENTER) * spread * SPREAD_DIAMETER;
            level.addParticle(ParticleTypes.ENCHANT, cx + ox, cy + oy, cz + oz,
                    0, FLAME_RISE_SPEED, 0);
        }
    }

    /**
     * Registers the WATERLOGGED property in the state definition.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    /**
     * Returns a water fluid state when waterlogged, otherwise empty.
     *
     * @param state the current block state
     * @return water source fluid state when waterlogged, empty otherwise
     */
    @Override
    protected @NonNull FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /**
     * Schedules a water fluid tick when waterlogged so water flows correctly
     * into and around the marker, matching the standard vanilla waterlogged idiom.
     *
     * @param state         the current block state
     * @param level         the level reader
     * @param ticks         scheduled tick access for fluid updates
     * @param pos           the block position
     * @param direction     the neighbor direction
     * @param neighborPos   the neighbor position
     * @param neighborState the neighbor state
     * @param random        the random source
     * @return the (possibly updated) block state
     */
    @Override
    protected @NonNull BlockState updateShape(BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos, @NonNull Direction direction,
                                              @NonNull BlockPos neighborPos, @NonNull BlockState neighborState,
                                              @NonNull RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    /**
     * Returns a splatted shape matching the BER orb, positioned at the
     * placed face. Falls back to the parent selection shape if no BE.
     *
     * @param state   the block state
     * @param level   the block getter
     * @param pos     the block position
     * @param context the collision context
     * @return the splatted voxel shape
     */
    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return SELECTION_SHAPE;
        }
        if (be.getGooType() == GooType.GLOW) {
            return computeGlowShape(be.getStackCount(), be.getPlacedFace(), be.isFlatBlob());
        }
        return computeOrbShape(be.getStackCount(), be.getPlacedFace(), be.isFlatBlob());
    }

    /**
     * Prevents breaking chain markers during fuse phase so punches
     * only toggle flat mode. Post-fuse markers break normally.
     *
     * @param state  the block state
     * @param player the player
     * @param level  the block getter
     * @param pos    the block position
     * @return 0 during fuse (unbreakable), normal otherwise
     */
    @Override
    protected float getDestroyProgress(@NonNull BlockState state, @NonNull Player player,
                                       @NonNull BlockGetter level, @NonNull BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be
                && isProtectedFromBreaking(be)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * Prevents block removal during fuse phase. Covers creative mode
     * which bypasses getDestroyProgress entirely.
     *
     * @param level      the server level
     * @param pos        the block position
     * @param player     the player breaking the block
     * @param toolStack  the tool used
     * @param canHarvest whether the player can harvest drops
     * @param fluidState the fluid state at the position
     * @return false during fuse (block stays), true otherwise
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos,
                                       Player player, ItemStack toolStack, boolean canHarvest, FluidState fluidState) {
        if (shouldDeferToSuper(level, pos)) {
            return super.onDestroyedByPlayer(state, level, pos, player, toolStack, canHarvest, fluidState);
        }
        ChainMarkerBlockEntity be = (ChainMarkerBlockEntity) level.getBlockEntity(pos);
        if (be.getGooType() == GooType.UNSTABLE && !level.isClientSide()) {
            be.instantDetonate();
        }
        return false;
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
     * Creates the chain marker block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ChainMarkerBlockEntity(pos, state);
    }

    /**
     * Registers the server-side fuse tick dispatcher.
     *
     * @param level the current level
     * @param state the block state
     * @param type  the goo type
     * @return the ticker
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state,
            @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, GooBlockEntities.CHAIN_MARKER.get(),
                ChainMarkerBlockEntity::serverTick);
    }

    /**
     * Detects when the support block (along placedFace direction) is
     * removed. When this happens during fuse phase, initiates a fall:
     * removes the marker, broadcasts a flight animation, and schedules
     * re-placement at the landing position.
     *
     * @param state         the current block state
     * @param level         the current level
     * @param pos           the block position
     * @param neighborBlock the block that changed
     * @param orientation   the redstone orientation, or null
     * @param movedByPiston true if moved by piston
     */
    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level,
                                   @NonNull BlockPos pos, @NonNull Block neighborBlock,
                                   @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        if (!isFusingMarkerWithNoSupport(level, pos)) {
            return;
        }
        initiateFall(state, (ServerLevel) level, pos);
    }

    /**
     * Drops the partial accumulator at the marker position if the player
     * breaks the block mid-implosion or mid-popping. Non-nether phases and
     * empty accumulators fall through to vanilla handling unchanged.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param state  the block state being destroyed
     * @param player the player breaking the block
     * @return the (possibly updated) block state, forwarded to super
     */
    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos,
                                                 @NonNull BlockState state, @NonNull Player player) {
        dropInterruptedAccumulator(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Spawns ambient particles based on the chain marker's goo type.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param random the random source
     */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
                            @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return;
        }
        spawnAmbientParticles(be.getGooType(), be.getStackCount(),
                pos, level, random);
    }

    /**
     * Functional interface for type-specific particle emitters.
     */
    @FunctionalInterface
    private interface ParticleEmitter {
        void emit(int stacks, double cx, double cy, double cz,
                  double spread, Level level, RandomSource random);
    }
}
