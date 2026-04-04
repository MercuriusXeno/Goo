package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

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

    private ChainProfiles() {}

    /** Called once from {@link com.mercuriusxeno.goo.Goo#commonSetup}. */
    public static void registerAll() {
        ChainProfile.register(GooType.BLAZE, new ChainProfile(
                BLAZE_FUSE_TICKS,
                BLAZE_MAX_STACKS,
                stacks -> (int) EffectMath.computeExplosionRadius(stacks),
                ChainProfiles::blazeExecutor
        ));
        ChainProfile.register(GooType.ROCK, new ChainProfile(
                ROCK_FUSE_TICKS,
                ROCK_MAX_STACKS,
                EffectMath::computeImplosionDepth,
                RockExecutor::execute
        ));
    }

    // ── Blaze executor ──────────────────────────────────────────────────

    /** Explosion + fiery aftermath scaled by stack count. */
    private static void blazeExecutor(ServerLevel level, BlockPos pos,
                                      int range, int stackCount,
                                      Direction placedFace) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        // Core explosion
        level.explode(null, cx, cy, cz, (float) range,
                Level.ExplosionInteraction.TNT);

        // Flame particles scaled by range
        int particleCount = 20 * stackCount;
        double spread = range * 0.6;
        level.sendParticles(ParticleTypes.FLAME,
                cx, cy, cz, particleCount, spread, spread, spread, 0.05);
        level.sendParticles(ParticleTypes.LAVA,
                cx, cy, cz, particleCount / 2, spread, spread, spread, 0.0);
        level.sendParticles(ParticleTypes.SMOKE,
                cx, cy + 0.5, cz, particleCount / 3, spread, spread * 1.5, spread, 0.02);

        // Scatter fires on surviving air blocks in the blast zone
        scatterFires(level, pos, range, stackCount);
    }

    /** Places fire on random air blocks above solid surfaces in the blast radius. */
    private static void scatterFires(ServerLevel level, BlockPos center,
                                     int range, int stackCount) {
        int fireCount = 3 * stackCount;
        var random = level.getRandom();
        for (int i = 0; i < fireCount * 4; i++) {
            if (fireCount <= 0) break;
            int dx = random.nextIntBetweenInclusive(-range, range);
            int dy = random.nextIntBetweenInclusive(-range / 2, range / 2);
            int dz = random.nextIntBetweenInclusive(-range, range);
            BlockPos target = center.offset(dx, dy, dz);
            BlockState state = level.getBlockState(target);
            if (state.isAir() && level.getBlockState(target.below()).isSolidRender()) {
                level.setBlock(target, Blocks.FIRE.defaultBlockState(), 3);
                fireCount--;
            }
        }
    }

    // ── Profile definition ────────────────────────────────────────────────

    /**
     * Defines the behavior of a chain effect for a specific goo type.
     *
     * @param fuseTicks    how long the fuse window lasts
     * @param maxStacks    maximum stack count (additional blobs during fuse)
     * @param rangeFormula computes range/depth from stack count
     * @param executor     fires the actual effect on fuse expiry
     */
    public record ChainProfile(
            int fuseTicks,
            int maxStacks,
            IntUnaryOperator rangeFormula,
            ChainExecutor executor
    ) {
        private static final Map<GooType, ChainProfile> PROFILES = new EnumMap<>(GooType.class);

        /** Registers a chain profile for a goo type. Called during mod init. */
        public static void register(GooType type, ChainProfile profile) {
            PROFILES.put(type, profile);
        }

        /** Looks up the profile for a goo type. Returns null if unregistered. */
        public static ChainProfile forType(GooType type) {
            return PROFILES.get(type);
        }

        /** Returns true if the given goo type has a registered chain profile. */
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
}
