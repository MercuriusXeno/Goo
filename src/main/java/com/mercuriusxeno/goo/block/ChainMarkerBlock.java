package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Short-lived fuse block placed by chain world effects (Blaze, Frost,
 * Nether, Rock). No collision, no selection shape - purely visual.
 * The block entity ticks the fuse and fires the executor on expiry.
 */
public class ChainMarkerBlock extends BaseEntityBlock {

    public static final MapCodec<ChainMarkerBlock> CODEC = simpleCodec(ChainMarkerBlock::new);

    /** Outline shape: small centered cube so the block is barely selectable. */
    private static final VoxelShape SHAPE = box(5, 5, 5, 11, 11, 11);

    /** Block center offset (0.5 blocks). */
    private static final double BLOCK_CENTER = 0.5;
    /** Base ambient particle spread radius. */
    private static final double BASE_SPREAD = 0.25;
    /** Additional spread per stack. */
    private static final double SPREAD_PER_STACK = 0.1;
    /** Base particle count for blaze effects. */
    private static final int BLAZE_BASE_PARTICLES = 2;
    /** Spread multiplier applied to random offset range. */
    private static final double SPREAD_DIAMETER = 2;
    /** Upward particle velocity for flame particles. */
    private static final double FLAME_RISE_SPEED = 0.02;
    /** Downward particle velocity for dust plume particles. */
    private static final double DUST_FALL_SPEED = -0.02;
    /** Lava particle spawn chance denominator (1 in N). */
    private static final int LAVA_CHANCE = 3;
    /** Base soul particle count for nether effects. */
    private static final int NETHER_BASE_PARTICLES = 2;
    /** Downward drift speed for soul particles. */
    private static final double SOUL_DRIFT_SPEED = -0.01;
    /** Smoke particle spawn chance denominator (1 in N) for nether. */
    private static final int NETHER_SMOKE_CHANCE = 4;

    /** Creates a chain marker block with the given properties.
     *
     * @param properties the block properties
     */
    public ChainMarkerBlock(Properties properties) {
        super(properties);
    }

    /** Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Rendered entirely by the BER - no block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** No collision - energy-type effects don't impede movement.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return the collision shape
     */
    @Override
    protected @NonNull VoxelShape getCollisionShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.empty();
    }

    /** Small outline for selection/targeting.
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
        return SHAPE;
    }

    /** Creates the chain marker block entity for this position.
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

    /** Registers the server-side fuse tick dispatcher.
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
        if (level.isClientSide()) { return null; }
        return createTickerHelper(type, GooBlockEntities.CHAIN_MARKER.get(),
                ChainMarkerBlockEntity::serverTick);
    }

    /** Spawns ambient particles based on the chain marker's goo type.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param random the random source
     */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
            @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) { return; }
        spawnAmbientParticles(be.getGooType(), be.getStackCount(),
                pos, level, random);
    }

    /** Emits goo-type-specific ambient particles scaled by stack count.
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
     * @param type the goo type determining which particles to spawn
     * @param stacks the current stack count (scales particle density)
     * @param cx block center X coordinate
     * @param cy block center Y coordinate
     * @param cz block center Z coordinate
     * @param level the current level
     * @param random the random source for particle offsets
     */
    private static void dispatchParticles(GooType type, int stacks,
            double cx, double cy, double cz, Level level, RandomSource random) {
        double spread = BASE_SPREAD + SPREAD_PER_STACK * stacks;
        switch (type) {
            case BLAZE -> spawnBlazeParticles(stacks, cx, cy, cz, spread, level, random);
            case ROCK -> spawnRockParticles(stacks, cx, cy, cz, spread, level, random);
            case NETHER -> spawnNetherParticles(stacks, cx, cy, cz, spread, level, random);
            default -> {}
        }
    }

    /**
     * Emits flame particles and occasional lava drips for blaze chain markers.
     * @param stacks the current stack count (scales particle count)
     * @param cx block center X coordinate
     * @param cy block center Y coordinate
     * @param cz block center Z coordinate
     * @param spread the particle offset radius
     * @param level the current level
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
     * @param cx block center X coordinate
     * @param cy block center Y coordinate
     * @param cz block center Z coordinate
     * @param spread the particle offset radius
     * @param level the current level
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
     * @param stacks the current stack count (scales particle count)
     * @param cx block center X coordinate
     * @param cy block center Y coordinate
     * @param cz block center Z coordinate
     * @param spread the particle offset radius
     * @param level the current level
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
     * @param stacks the current stack count (scales particle count)
     * @param cx block center X coordinate
     * @param cy block center Y coordinate
     * @param cz block center Z coordinate
     * @param spread the particle offset radius
     * @param level the current level
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
}
