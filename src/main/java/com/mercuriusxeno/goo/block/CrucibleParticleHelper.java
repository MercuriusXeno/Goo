package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Static helper for all crucible particle and sound effects.
 * Keeps CrucibleBlockEntity and CrucibleBlock free of spawn logic.
 */
public final class CrucibleParticleHelper {

    /** Volume at which the logarithmic fill curve reaches 1.0 (matches BER). */
    private static final long LIQUID_LOG_CAP = 64_000L;
    /** Basin floor Y in block-relative coords. */
    private static final float LIQUID_MIN_Y = 10f / 16f;
    /** Basin rim Y (just below top) in block-relative coords. */
    private static final float LIQUID_MAX_Y = 15f / 16f;

    /** Minimum squared XZ distance between recent bubble spawns (in blocks). */
    private static final double MIN_SPACING_SQ = 0.0156;
    /** Number of recent spawn positions to track per crucible. */
    private static final int HISTORY_SIZE = 16;
    /** Maximum attempts to find a non-overlapping spawn position. */
    private static final int MAX_PLACEMENT_TRIES = 8;

    /** Ring buffer of recent bubble spawn XZ positions (pairs: x, z). */
    private static final double[] recentX = new double[HISTORY_SIZE];
    private static final double[] recentZ = new double[HISTORY_SIZE];
    /** Next write index in the ring buffer. */
    private static int recentIndex = 0;

    private CrucibleParticleHelper() {}

    /**
     * Spawns 8-12 lava particles at the rod-basin contact point.
     * Sparks spray laterally outward and fall down.
     */
    public static void spawnSparkShower(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 8.0 / 16.0;
        double z = pos.getZ() + 0.5;
        RandomSource random = level.getRandom();
        int count = 8 + random.nextInt(5);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.06 + random.nextDouble() * 0.02;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = -0.005 - random.nextDouble() * 0.01;
            level.sendParticles(GooParticles.GOO_SPARK.get(), x, y, z, 0,
                vx, vy, vz, 1.0);
        }
    }

    /**
     * Spawns 3-4 sparks in random directions at the rod-basin contact point.
     * Used by the ignition spray for a burst over 6-8 ticks.
     */
    public static void spawnIgnitionSparks(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 8.0 / 16.0;
        double z = pos.getZ() + 0.5;
        RandomSource random = level.getRandom();
        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.05 + random.nextDouble() * 0.02;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = -0.003 - random.nextDouble() * 0.007;
            level.sendParticles(GooParticles.GOO_SPARK.get(), x, y, z, 0,
                vx, vy, vz, 1.0);
        }
    }

    /** Ember chance when idle (rod contact, no melting): ~10% per tick. */
    private static final float EMBER_CHANCE_IDLE = 0.10f;
    /** Ember chance when melting: ~30% per tick. */
    private static final float EMBER_CHANCE_MELTING = 0.30f;

    /**
     * Spawns 1-2 spark particles at the rod-basin contact point.
     * Embers spray laterally outward in random directions and fall down.
     * Frequency depends on whether the crucible is actively melting goo.
     */
    public static void spawnEmbers(ServerLevel level, BlockPos pos,
            RandomSource random, boolean melting) {
        float chance = melting ? EMBER_CHANCE_MELTING : EMBER_CHANCE_IDLE;
        if (random.nextFloat() >= chance) return;
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 8.0 / 16.0;
        double z = pos.getZ() + 0.5;
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.05 + random.nextDouble() * 0.02;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = -0.003 - random.nextDouble() * 0.007;
            level.sendParticles(GooParticles.GOO_SPARK.get(), x, y, z, 0,
                vx, vy, vz, 1.0);
        }
    }

    /**
     * Spawns 0-1 color-tinted goo bubble particles at random XZ within the basin.
     * Rejects positions too close to recently spawned bubbles.
     * Bubbles spawn directly at the liquid surface. 3 "emergence" frames and a continuous
     * scaling of 40% to 100% over 10 frames gives the illusion of surface breaks and expansion.
     */
    public static void spawnGooBubbles(ServerLevel level, BlockPos pos,
            float surfaceY, int color, RandomSource random) {
        int count = random.nextInt(2);
        ColorParticleOption options = ColorParticleOption.create(
            GooParticles.GOO_BUBBLE.get(), color | 0xFF000000);
        // slight rise here to keep the bubble from going under fluid
        double y = pos.getY() + surfaceY + 1.0 / 32.0;
        for (int i = 0; i < count; i++) {
            if (!tryFindSpacedPosition(pos, random)) continue;
            double x = recentX[wrapIndex(recentIndex - 1)];
            double z = recentZ[wrapIndex(recentIndex - 1)];
            level.sendParticles(options, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Tries to find a spawn position that isn't too close to recent bubbles.
     * Records the position in the ring buffer if successful.
     */
    private static boolean tryFindSpacedPosition(BlockPos pos, RandomSource random) {
        for (int attempt = 0; attempt < MAX_PLACEMENT_TRIES; attempt++) {
            double x = pos.getX() + randomInBasin(random);
            double z = pos.getZ() + randomInBasin(random);
            if (!tooCloseToRecent(x, z)) {
                recordSpawn(x, z);
                return true;
            }
        }
        return false;
    }

    /** Returns true if the given position is within MIN_SPACING of any recent spawn. */
    private static boolean tooCloseToRecent(double x, double z) {
        for (int i = 0; i < HISTORY_SIZE; i++) {
            double dx = x - recentX[i];
            double dz = z - recentZ[i];
            if (dx * dx + dz * dz < MIN_SPACING_SQ) return true;
        }
        return false;
    }

    /** Records a spawn position in the ring buffer. */
    private static void recordSpawn(double x, double z) {
        recentX[recentIndex] = x;
        recentZ[recentIndex] = z;
        recentIndex = wrapIndex(recentIndex + 1);
    }

    /** Wraps a ring buffer index. */
    private static int wrapIndex(int i) {
        return ((i % HISTORY_SIZE) + HISTORY_SIZE) % HISTORY_SIZE;
    }

    /**
     * Spawns 3-5 smoke particles in the basin area when an item is absorbed.
     * One-shot burst.
     */
    public static void spawnMeltSmoke(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 14.0 / 16.0;
        double z = pos.getZ() + 0.5;
        int count = 3 + level.getRandom().nextInt(3);
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, count,
            0.15, 0.05, 0.15, 0.01);
    }

    /**
     * Plays a high-pitched lava pop sound at the crucible position.
     * Volume 0.3 keeps it subtle; pitch 1.8-2.2 gives a sizzle character.
     */
    public static void playSizzle(ServerLevel level, BlockPos pos) {
        float pitch = 1.8f + level.getRandom().nextFloat() * 0.4f;
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.3f, pitch);
    }

    /**
     * Computes the liquid surface Y in block-relative coords from total goo volume.
     * Replicates the BER's logarithmic fill curve (acceptable DRY exception
     * since BER runs client-side and this runs server-side).
     */
    public static float computeSurfaceY(long totalGoo) {
        if (totalGoo <= 0) return LIQUID_MIN_Y;
        float fill = (float) (Math.log(1.0 + totalGoo) / Math.log(1.0 + LIQUID_LOG_CAP));
        fill = Math.min(1f, fill);
        return LIQUID_MIN_Y + fill * (LIQUID_MAX_Y - LIQUID_MIN_Y);
    }

    /** Returns a random XZ coordinate within the basin interior, inset by 3px from walls. */
    private static double randomInBasin(RandomSource random) {
        return 3.0 / 16.0 + random.nextDouble() * (10.0 / 16.0);
    }
}
