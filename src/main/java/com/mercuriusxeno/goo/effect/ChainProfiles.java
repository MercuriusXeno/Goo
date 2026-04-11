package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Central registry for chain effect profiles. Each goo type with a chain
 * effect gets a profile that defines fuse duration, max stacks, range
 * formula, and executor. Profiles are registered during mod init and
 * looked up at runtime by the chain marker block entity.
 */
public final class ChainProfiles {

    private static final int BLAZE_FUSE_TICKS = 30;
    private static final int BLAZE_MAX_STACKS = 4;
    private static final int ROCK_FUSE_TICKS = 30;
    private static final int ROCK_MAX_STACKS = 5;
    private static final int NETHER_FUSE_TICKS = 30;
    private static final int NETHER_MAX_STACKS = 4;

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Particles per stack for flame effect. */
    private static final int FLAME_PARTICLES_PER_STACK = 20;
    /** Spread multiplier applied to explosion range for particle distribution. */
    private static final double FLAME_SPREAD_FACTOR = 0.6;
    /** Upward velocity for flame particles. */
    private static final double FLAME_PARTICLE_SPEED = 0.05;
    /** Lava particle count divisor relative to flame count. */
    private static final int LAVA_PARTICLE_DIVISOR = 2;
    /** Smoke particle count divisor relative to flame count. */
    private static final int SMOKE_PARTICLE_DIVISOR = 3;
    /** Vertical spread multiplier for smoke plume. */
    private static final double SMOKE_SPREAD_MULTIPLIER = 1.5;
    /** Upward velocity for smoke particles. */
    private static final double SMOKE_PARTICLE_SPEED = 0.02;
    /** Fires placed per stack count in scatter phase. */
    private static final int FIRES_PER_STACK = 3;
    /** Attempts per desired fire placement to account for misses. */
    private static final int FIRE_ATTEMPT_MULTIPLIER = 4;
    /** Vertical range divisor for fire scatter (half the horizontal range). */
    private static final int FIRE_VERTICAL_RANGE_DIVISOR = 2;
    /** Block update flags for setBlock calls. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private ChainProfiles() {}

    /** Called once from {@link com.mercuriusxeno.goo.Goo#commonSetup}. */
    public static void registerAll() {
        registerBlaze();
        registerRock();
        registerNether();
    }

    /** Registers the blaze chain profile (legacy one-shot executor path). */
    private static void registerBlaze() {
        ChainProfile.register(GooType.BLAZE, new ChainProfile(
                BLAZE_FUSE_TICKS,
                BLAZE_MAX_STACKS,
                stacks -> (int) EffectMath.computeExplosionRadius(stacks),
                ChainProfiles::blazeExecutor,
                null,
                null
        ));
    }

    /** Registers the rock chain profile (legacy per-layer executor path). */
    private static void registerRock() {
        ChainProfile.register(GooType.ROCK, new ChainProfile(
                ROCK_FUSE_TICKS,
                ROCK_MAX_STACKS,
                EffectMath::computeImplosionDepth,
                null,
                (level, pos, step, stacks, face) ->
                        RockExecutor.mineLayer(level, pos, face, step, stacks),
                null
        ));
    }

    /** Registers the nether chain profile (new-style ChainBehavior path). */
    private static void registerNether() {
        ChainProfile.register(GooType.NETHER, new ChainProfile(
                NETHER_FUSE_TICKS,
                NETHER_MAX_STACKS,
                EffectMath::computeNetherRadius,
                null,
                null,
                NetherBehavior::new
        ));
    }

    // ── Blaze executor ──────────────────────────────────────────────────

    /**
     * Explosion + fiery aftermath scaled by stack count.
     *
     * @param level      the server level
     * @param pos        the anchor block position
     * @param range      computed explosion radius
     * @param stackCount the raw stack count
     * @param placedFace the face the marker was attached to
     */
    private static void blazeExecutor(ServerLevel level, BlockPos pos,
                                      int range, int stackCount,
                                      Direction placedFace) {
        double cx = pos.getX() + BLOCK_CENTER_OFFSET;
        double cy = pos.getY() + BLOCK_CENTER_OFFSET;
        double cz = pos.getZ() + BLOCK_CENTER_OFFSET;

        level.explode(null, cx, cy, cz, (float) range,
                Level.ExplosionInteraction.TNT);

        emitBlazeParticles(level, cx, cy, cz, range, stackCount);
        scatterFires(level, pos, range, stackCount);
    }

    /**
     * Sends flame, lava, and smoke particles scaled by explosion range and stack count.
     * @param level the server level to spawn particles in
     * @param cx the explosion center X coordinate
     * @param cy the explosion center Y coordinate
     * @param cz the explosion center Z coordinate
     * @param range the explosion radius controlling particle spread
     * @param stackCount the number of stacked blobs controlling particle density
     */
    private static void emitBlazeParticles(ServerLevel level,
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

    /**
     * Places fire on random air blocks above solid surfaces in the blast radius.
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

    /**
     * Places fire at the target if it is air above a solid surface.
     * @param level the server level to place fire in
     * @param target the candidate position for fire placement
     * @return true if fire was successfully placed
     */
    private static boolean tryPlaceFire(ServerLevel level, BlockPos target) {
        if (!level.getBlockState(target).isAir()) { return false; }
        if (!level.getBlockState(target.below()).isSolidRender()) { return false; }
        level.setBlock(target, Blocks.FIRE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
        return true;
    }

    // ── Profile definition ────────────────────────────────────────────────

    /**
     * Defines the behavior of a chain effect for a specific goo type.
     * Exactly one of {@code executor}, {@code layerExecutor}, or
     * {@code behaviorFactory} should be non-null. The chain marker BE
     * picks the first non-null path at fuse expiry:
     * <ul>
     *   <li>{@code behaviorFactory} - creates a {@link ChainBehavior}
     *       that owns the whole post-fuse lifecycle (preferred for new
     *       types; nether uses this).</li>
     *   <li>{@code executor} - legacy instant one-shot fired on fuse expiry.</li>
     *   <li>{@code layerExecutor} - legacy per-tick progressive effect.</li>
     * </ul>
     *
     * @param fuseTicks       how long the fuse window lasts
     * @param maxStacks       maximum stack count (additional blobs during fuse)
     * @param rangeFormula    computes range/depth from stack count
     * @param executor        instant executor; null for progressive effects or behaviors
     * @param layerExecutor   per-layer executor; null for instant effects or behaviors
     * @param behaviorFactory factory that creates a fresh {@link ChainBehavior}; null for legacy profiles
     */
    public record ChainProfile(
            int fuseTicks,
            int maxStacks,
            IntUnaryOperator rangeFormula,
            @Nullable ChainExecutor executor,
            @Nullable LayerExecutor layerExecutor,
            @Nullable Supplier<ChainBehavior> behaviorFactory
    ) {
        private static final Map<GooType, ChainProfile> PROFILES = new EnumMap<>(GooType.class);

        /**
         * Registers a chain profile for a goo type. Called during mod init.
         *
         * @param type    the goo type
         * @param profile the chain profile definition
         */
        public static void register(GooType type, ChainProfile profile) {
            PROFILES.put(type, profile);
        }

        /**
         * Looks up the profile for a goo type. Returns null if unregistered.
         *
         * @param type the goo type
         * @return the chain profile, or null if none registered
         */
        public static ChainProfile forType(GooType type) {
            return PROFILES.get(type);
        }

        /**
         * Returns true if the given goo type has a registered chain profile.
         *
         * @param type the goo type to check
         * @return true if a chain profile exists
         */
        public static boolean isChainType(GooType type) {
            return PROFILES.containsKey(type);
        }
    }

    /**
     * Functional interface for the chain effect's execute behavior.
     * Receives the server level, anchor position, computed range, stack
     * count, and the face the marker was placed on.
     */
    @FunctionalInterface
    public interface ChainExecutor {
        /**
         * Fires the chain effect.
         *
         * @param level      the server level
         * @param pos        the anchor block position
         * @param range      computed from rangeFormula(stackCount)
         * @param stackCount the raw stack count
         * @param placedFace the face the marker was attached to
         */
        void execute(ServerLevel level, BlockPos pos, int range,
                     int stackCount, Direction placedFace);
    }

    /**
     * Functional interface for per-tick progressive chain effects. Called
     * by {@link com.mercuriusxeno.goo.block.ChainMarkerBlockEntity} once
     * per server tick with increasing {@code stepIndex} values in
     * {@code [0, range)} after the fuse expires.
     */
    @FunctionalInterface
    public interface LayerExecutor {
        /**
         * Fires a single tick of a progressive chain effect.
         *
         * @param level      the server level
         * @param pos        the anchor block position
         * @param stepIndex  zero-based current step in the progression
         * @param stackCount the raw stack count
         * @param placedFace the face the marker was attached to
         */
        void tickLayer(ServerLevel level, BlockPos pos, int stepIndex,
                       int stackCount, Direction placedFace);
    }
}
