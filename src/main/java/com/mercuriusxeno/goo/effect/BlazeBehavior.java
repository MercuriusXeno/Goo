package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Blaze chain behavior: instant explosion + flame particles + scattered
 * fire on fuse expiry. All work happens in
 * {@link #onFuseExpired}; {@link #isActive} is false immediately
 * afterward, so the chain marker BE removes itself on the same tick.
 *
 * <p>This behavior has no persistent state - the explosion is a single
 * TNT-style blast with a particle burst and a random fire scatter
 * across the blast footprint. Stack count scales both the explosion
 * radius (via {@link EffectMath#computeExplosionRadius}) and the
 * particle/fire density.
 */
public final class BlazeBehavior implements ChainBehavior {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Particles per stack for the flame burst. */
    private static final int FLAME_PARTICLES_PER_STACK = 20;
    /** Spread multiplier applied to the explosion range for particle distribution. */
    private static final double FLAME_SPREAD_FACTOR = 0.6;
    /** Upward velocity for flame particles. */
    private static final double FLAME_PARTICLE_SPEED = 0.05;
    /** Lava particle count divisor relative to flame count. */
    private static final int LAVA_PARTICLE_DIVISOR = 2;
    /** Smoke particle count divisor relative to flame count. */
    private static final int SMOKE_PARTICLE_DIVISOR = 3;
    /** Vertical spread multiplier for the smoke plume. */
    private static final double SMOKE_SPREAD_MULTIPLIER = 1.5;
    /** Upward velocity for smoke particles. */
    private static final double SMOKE_PARTICLE_SPEED = 0.02;
    /** Fires placed per stack in the scatter phase. */
    private static final int FIRES_PER_STACK = 3;
    /** Attempts per desired fire placement to account for misses. */
    private static final int FIRE_ATTEMPT_MULTIPLIER = 4;
    /** Vertical range divisor for the fire scatter (half the horizontal range). */
    private static final int FIRE_VERTICAL_RANGE_DIVISOR = 2;
    /** Block update flags for setBlock calls. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        int stackCount = be.getStackCount();
        int range = (int) EffectMath.computeExplosionRadius(stackCount);
        double cx = pos.getX() + BLOCK_CENTER_OFFSET;
        double cy = pos.getY() + BLOCK_CENTER_OFFSET;
        double cz = pos.getZ() + BLOCK_CENTER_OFFSET;

        level.explode(null, cx, cy, cz, (float) range,
                Level.ExplosionInteraction.TNT);

        emitParticles(level, cx, cy, cz, range, stackCount);
        scatterFires(level, pos, range, stackCount);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        // Instant behavior: never ticks.
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        // No state.
    }

    @Override
    public void loadAdditional(ValueInput input) {
        // No state.
    }

    /** Sends flame, lava, and smoke particles scaled by explosion range and stack count.
     *
     * @param level      the server level to spawn particles in
     * @param cx         the explosion center X coordinate
     * @param cy         the explosion center Y coordinate
     * @param cz         the explosion center Z coordinate
     * @param range      the explosion radius controlling particle spread
     * @param stackCount the number of stacked blobs controlling particle density
     */
    private static void emitParticles(ServerLevel level,
            double cx, double cy, double cz, int range, int stackCount) {
        int particleCount = FLAME_PARTICLES_PER_STACK * stackCount;
        double spread = range * FLAME_SPREAD_FACTOR;
        level.sendParticles(ParticleTypes.FLAME,
                cx, cy, cz, particleCount, spread, spread, spread, FLAME_PARTICLE_SPEED);
        level.sendParticles(ParticleTypes.LAVA,
                cx, cy, cz, particleCount / LAVA_PARTICLE_DIVISOR, spread, spread, spread, 0.0);
        level.sendParticles(ParticleTypes.SMOKE,
                cx, cy + BLOCK_CENTER_OFFSET, cz, particleCount / SMOKE_PARTICLE_DIVISOR,
                spread, spread * SMOKE_SPREAD_MULTIPLIER, spread, SMOKE_PARTICLE_SPEED);
    }

    /** Places fire on random air blocks above solid surfaces in the blast radius.
     *
     * @param level      the server level
     * @param center     the explosion center position
     * @param range      the blast radius
     * @param stackCount the raw stack count
     */
    private static void scatterFires(ServerLevel level, BlockPos center,
                                     int range, int stackCount) {
        int fireCount = FIRES_PER_STACK * stackCount;
        var random = level.getRandom();
        int vertRange = range / FIRE_VERTICAL_RANGE_DIVISOR;
        for (int i = 0; i < fireCount * FIRE_ATTEMPT_MULTIPLIER; i++) {
            if (fireCount <= 0) { break; }
            BlockPos target = center.offset(
                    random.nextIntBetweenInclusive(-range, range),
                    random.nextIntBetweenInclusive(-vertRange, vertRange),
                    random.nextIntBetweenInclusive(-range, range));
            if (tryPlaceFire(level, target)) {
                fireCount--;
            }
        }
    }

    /** Places fire at the target if it is air above a solid surface.
     *
     * @param level  the server level to place fire in
     * @param target the candidate position for fire placement
     * @return true if fire was successfully placed
     */
    private static boolean tryPlaceFire(ServerLevel level, BlockPos target) {
        if (!level.getBlockState(target).isAir()) { return false; }
        if (!level.getBlockState(target.below()).isSolidRender()) { return false; }
        level.setBlock(target, Blocks.FIRE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
        return true;
    }
}
