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
    /** Basin floor Y in block-relative coords (goocible rim interior). */
    private static final float LIQUID_MIN_Y = 13f / 16f;
    /** Basin rim Y (just below top) in block-relative coords. */
    private static final float LIQUID_MAX_Y = 15f / 16f;

    /** Minimum squared XZ distance between recent bubble spawns (in blocks). */
    private static final double MIN_SPACING_SQ = 0.0156;
    /** Number of recent spawn positions to track per crucible. */
    private static final int HISTORY_SIZE = 16;
    /** Maximum attempts to find a non-overlapping spawn position. */
    private static final int MAX_PLACEMENT_TRIES = 8;

    /** Ring buffer of recent bubble spawn XZ positions (pairs: x, z). */
    private static final double[] RECENT_X = new double[HISTORY_SIZE];
    private static final double[] RECENT_Z = new double[HISTORY_SIZE];
    /** Next write index in the ring buffer. */
    private static int recentIndex;

    /** Ember chance when idle (rod contact, no melting): ~10% per tick. */
    private static final float EMBER_CHANCE_IDLE = 0.10f;
    /** Ember chance when melting: ~30% per tick. */
    private static final float EMBER_CHANCE_MELTING = 0.30f;

    /** Block center offset (0.5 blocks). */
    private static final double BLOCK_CENTER = 0.5;
    /** Flame area Y in pixel coords (top of goocible body, inside rim). */
    private static final double FLAME_Y = 13.0 / 16.0;
    /** Smoke spawn Y in pixel coords (near basin rim). */
    private static final double SMOKE_Y = 14.0 / 16.0;
    /** Full-circle angle in radians. */
    private static final double TWO_PI = Math.PI * 2;

    // -- Spark shower constants --
    /** Base number of sparks in a shower burst. */
    private static final int SPARK_BASE_COUNT = 8;
    /** Random additional sparks in a shower burst. */
    private static final int SPARK_RANDOM_COUNT = 5;
    /** Base lateral speed of spark particles. */
    private static final double SPARK_BASE_SPEED = 0.06;
    /** Random additional lateral speed of spark particles. */
    private static final double SPARK_RANDOM_SPEED = 0.02;
    /** Base downward velocity of falling sparks. */
    private static final double SPARK_BASE_FALL = -0.005;
    /** Random additional downward velocity of falling sparks. */
    private static final double SPARK_RANDOM_FALL = 0.01;

    // -- Ignition/ember constants --
    /** Base number of ignition sparks per tick. */
    private static final int IGNITION_BASE_COUNT = 2;
    /** Random additional ignition sparks per tick. */
    private static final int IGNITION_RANDOM_COUNT = 2;
    /** Base lateral speed of ignition/ember particles. */
    private static final double EMBER_BASE_SPEED = 0.05;
    /** Random additional lateral speed of ember particles. */
    private static final double EMBER_RANDOM_SPEED = 0.02;
    /** Base downward velocity of ember particles. */
    private static final double EMBER_BASE_FALL = -0.003;
    /** Random additional downward velocity of ember particles. */
    private static final double EMBER_RANDOM_FALL = 0.007;

    // -- Bubble constants --
    /** Alpha channel mask for fully opaque color. */
    private static final int ALPHA_OPAQUE = 0xFF000000;
    /** Slight vertical offset to keep bubbles above the fluid surface. */
    private static final double BUBBLE_RISE_OFFSET = 1.0 / 32.0;

    // -- Smoke burst constants --
    /** Base smoke particle count on item absorption. */
    private static final int SMOKE_BASE_COUNT = 3;
    /** Random additional smoke particles on item absorption. */
    private static final int SMOKE_RANDOM_COUNT = 3;
    /** XZ spread of smoke particles. */
    private static final double SMOKE_SPREAD_XZ = 0.15;
    /** Y spread of smoke particles. */
    private static final double SMOKE_SPREAD_Y = 0.05;
    /** Initial speed of smoke particles. */
    private static final double SMOKE_SPEED = 0.01;

    // -- Sizzle sound constants --
    /** Base pitch for sizzle sound. */
    private static final float SIZZLE_BASE_PITCH = 1.8f;
    /** Random pitch variation for sizzle sound. */
    private static final float SIZZLE_PITCH_RANGE = 0.4f;
    /** Volume of the sizzle sound. */
    private static final float SIZZLE_VOLUME = 0.3f;

    // -- Basin interior constants --
    /** Basin wall inset in pixel coords (5 pixels, goocible rim). */
    private static final double BASIN_INSET = 5.0 / 16.0;
    /** Basin interior width in block-relative coords (6 pixels). */
    private static final double BASIN_INTERIOR_WIDTH = 6.0 / 16.0;

    private CrucibleParticleHelper() {}

    /**
     * Spawns 8-12 lava particles at the rod-basin contact point.
     * Sparks spray laterally outward and fall down.
     *
     * @param level the current level
     * @param pos   the block position
     */
    public static void spawnSparkShower(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + BLOCK_CENTER;
        double y = pos.getY() + FLAME_Y;
        double z = pos.getZ() + BLOCK_CENTER;
        RandomSource random = level.getRandom();
        int count = SPARK_BASE_COUNT + random.nextInt(SPARK_RANDOM_COUNT);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * TWO_PI;
            double speed = SPARK_BASE_SPEED + random.nextDouble() * SPARK_RANDOM_SPEED;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = SPARK_BASE_FALL - random.nextDouble() * SPARK_RANDOM_FALL;
            level.sendParticles(GooParticles.GOO_SPARK.get(), x, y, z, 0,
                vx, vy, vz, 1.0);
        }
    }

    /**
     * Spawns 3-4 sparks in random directions at the rod-basin contact point.
     * Used by the ignition spray for a burst over 6-8 ticks.
     *
     * @param level the current level
     * @param pos   the block position
     */
    public static void spawnIgnitionSparks(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + BLOCK_CENTER;
        double y = pos.getY() + FLAME_Y;
        double z = pos.getZ() + BLOCK_CENTER;
        RandomSource random = level.getRandom();
        int count = IGNITION_BASE_COUNT + random.nextInt(IGNITION_RANDOM_COUNT);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * TWO_PI;
            double speed = EMBER_BASE_SPEED + random.nextDouble() * EMBER_RANDOM_SPEED;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = EMBER_BASE_FALL - random.nextDouble() * EMBER_RANDOM_FALL;
            level.sendParticles(GooParticles.GOO_SPARK.get(), x, y, z, 0,
                vx, vy, vz, 1.0);
        }
    }

    /**
     * Spawns 1-2 spark particles at the rod-basin contact point.
     * Embers spray laterally outward in random directions and fall down.
     * Frequency depends on whether the crucible is actively melting goo.
     *
     * @param level   the current level
     * @param pos     the block position
     * @param random  the random source
     * @param melting true if actively melting an item
     */
    public static void spawnEmbers(ServerLevel level, BlockPos pos,
            RandomSource random, boolean melting) {
        float chance = melting ? EMBER_CHANCE_MELTING : EMBER_CHANCE_IDLE;
        if (random.nextFloat() >= chance) { return; }
        double x = pos.getX() + BLOCK_CENTER;
        double y = pos.getY() + FLAME_Y;
        double z = pos.getZ() + BLOCK_CENTER;
        int count = 1 + random.nextInt(IGNITION_RANDOM_COUNT);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * TWO_PI;
            double speed = EMBER_BASE_SPEED + random.nextDouble() * EMBER_RANDOM_SPEED;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = EMBER_BASE_FALL - random.nextDouble() * EMBER_RANDOM_FALL;
            level.sendParticles(GooParticles.GOO_SPARK.get(), x, y, z, 0,
                vx, vy, vz, 1.0);
        }
    }

    /**
     * Spawns 0-1 color-tinted goo bubble particles at random XZ within the basin.
     * Rejects positions too close to recently spawned bubbles.
     * Bubbles spawn directly at the liquid surface. 3 "emergence" frames and a continuous
     * scaling of 40% to 100% over 10 frames gives the illusion of surface breaks and expansion.
     *
     * @param level    the current level
     * @param pos      the block position
     * @param surfaceY the liquid surface Y in block coords
     * @param color    the ARGB color value
     * @param random   the random source
     */
    public static void spawnGooBubbles(ServerLevel level, BlockPos pos,
            float surfaceY, int color, RandomSource random) {
        int count = random.nextInt(IGNITION_RANDOM_COUNT);
        ColorParticleOption options = ColorParticleOption.create(
            GooParticles.GOO_BUBBLE.get(), color | ALPHA_OPAQUE);
        // slight rise here to keep the bubble from going under fluid
        double y = pos.getY() + surfaceY + BUBBLE_RISE_OFFSET;
        for (int i = 0; i < count; i++) {
            if (!tryFindSpacedPosition(pos, random)) { continue; }
            double x = RECENT_X[wrapIndex(recentIndex - 1)];
            double z = RECENT_Z[wrapIndex(recentIndex - 1)];
            level.sendParticles(options, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Tries to find a spawn position that isn't too close to recent bubbles.
     * Records the position in the ring buffer if successful.
     *
     * @param pos    the block position
     * @param random the random source
     * @return the computed surface y of find spaced position
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

    /** Returns true if the given position is within MIN_SPACING of any recent spawn.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @return true if the condition is met
     */
    private static boolean tooCloseToRecent(double x, double z) {
        for (int i = 0; i < HISTORY_SIZE; i++) {
            double dx = x - RECENT_X[i];
            double dz = z - RECENT_Z[i];
            if (dx * dx + dz * dz < MIN_SPACING_SQ) { return true; }
        }
        return false;
    }

    /** Records a spawn position in the ring buffer.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     */
    private static void recordSpawn(double x, double z) {
        RECENT_X[recentIndex] = x;
        RECENT_Z[recentIndex] = z;
        recentIndex = wrapIndex(recentIndex + 1);
    }

    /** Wraps a ring buffer index.
     *
     * @param i the index
     * @return the integer value
     */
    private static int wrapIndex(int i) {
        return ((i % HISTORY_SIZE) + HISTORY_SIZE) % HISTORY_SIZE;
    }

    /**
     * Spawns 3-5 smoke particles in the basin area when an item is absorbed.
     * One-shot burst.
     *
     * @param level the current level
     * @param pos   the block position
     */
    public static void spawnMeltSmoke(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + BLOCK_CENTER;
        double y = pos.getY() + SMOKE_Y;
        double z = pos.getZ() + BLOCK_CENTER;
        int count = SMOKE_BASE_COUNT + level.getRandom().nextInt(SMOKE_RANDOM_COUNT);
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, count,
            SMOKE_SPREAD_XZ, SMOKE_SPREAD_Y, SMOKE_SPREAD_XZ, SMOKE_SPEED);
    }

    /**
     * Plays a high-pitched lava pop sound at the crucible position.
     * Volume 0.3 keeps it subtle; pitch 1.8-2.2 gives a sizzle character.
     *
     * @param level the current level
     * @param pos   the block position
     */
    public static void playSizzle(ServerLevel level, BlockPos pos) {
        float pitch = SIZZLE_BASE_PITCH + level.getRandom().nextFloat() * SIZZLE_PITCH_RANGE;
        level.playSound(null, pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER,
            SoundEvents.LAVA_POP, SoundSource.BLOCKS, SIZZLE_VOLUME, pitch);
    }

    /**
     * Computes the liquid surface Y in block-relative coords from total goo volume.
     * Replicates the BER's logarithmic fill curve (acceptable DRY exception
     * since BER runs client-side and this runs server-side).
     *
     * @param totalGoo the total goo
     * @return the result
     */
    public static float computeSurfaceY(long totalGoo) {
        if (totalGoo <= 0) { return LIQUID_MIN_Y; }
        float fill = (float) (Math.log(1.0 + totalGoo) / Math.log(1.0 + LIQUID_LOG_CAP));
        fill = Math.min(1f, fill);
        return LIQUID_MIN_Y + fill * (LIQUID_MAX_Y - LIQUID_MIN_Y);
    }

    /** Returns a random XZ coordinate within the basin interior, inset by 3px from walls.
     *
     * @param random the random source
     * @return the double value
     */
    private static double randomInBasin(RandomSource random) {
        return BASIN_INSET + random.nextDouble() * BASIN_INTERIOR_WIDTH;
    }
}
