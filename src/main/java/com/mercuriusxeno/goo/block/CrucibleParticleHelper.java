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
    /** Ticks before a dedupe entry expires (matches GooBubbleParticle.POP_END). */
    private static final long BUBBLE_TTL_TICKS = 54;

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

    /** Spark shower velocity profile. */
    private static final SparkProfile SPARK_PROFILE =
        new SparkProfile(SPARK_BASE_SPEED, SPARK_RANDOM_SPEED, SPARK_BASE_FALL, SPARK_RANDOM_FALL);

    /** Ember/ignition velocity profile. */
    private static final SparkProfile EMBER_PROFILE =
        new SparkProfile(EMBER_BASE_SPEED, EMBER_RANDOM_SPEED, EMBER_BASE_FALL, EMBER_RANDOM_FALL);

    /** Velocity profile for radial spark emission. */
    private record SparkProfile(double baseSpeed, double randomSpeed,
            double baseFall, double randomFall) {}

    private CrucibleParticleHelper() {}

    /**
     * Spawns 8-12 lava particles at the rod-basin contact point.
     * Sparks spray laterally outward and fall down.
     *
     * @param level the current level
     * @param pos   the block position
     */
    public static void spawnSparkShower(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        int count = SPARK_BASE_COUNT + random.nextInt(SPARK_RANDOM_COUNT);
        emitSparks(level, pos, random, count, SPARK_PROFILE);
    }

    /**
     * Spawns 3-4 sparks in random directions at the rod-basin contact point.
     * Used by the ignition spray for a burst over 6-8 ticks.
     *
     * @param level the current level
     * @param pos   the block position
     */
    public static void spawnIgnitionSparks(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        int count = IGNITION_BASE_COUNT + random.nextInt(IGNITION_RANDOM_COUNT);
        emitSparks(level, pos, random, count, EMBER_PROFILE);
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
        int count = 1 + random.nextInt(IGNITION_RANDOM_COUNT);
        emitSparks(level, pos, random, count, EMBER_PROFILE);
    }

    /**
     * Emits spark particles with radial velocity at the rod-basin contact point.
     *
     * @param level  the server level
     * @param pos    the block position
     * @param random the random source
     * @param count  number of sparks to emit
     * @param p      the velocity profile
     */
    private static void emitSparks(ServerLevel level, BlockPos pos,
            RandomSource random, int count, SparkProfile p) {
        double x = pos.getX() + BLOCK_CENTER;
        double y = pos.getY() + FLAME_Y;
        double z = pos.getZ() + BLOCK_CENTER;
        for (int i = 0; i < count; i++) {
            emitOneSpark(level, random, p, x, y, z);
        }
    }

    /**
     * Emits a single spark with randomized radial velocity.
     *
     * @param level  the server level
     * @param random the random source
     * @param p      the velocity profile
     * @param x      the spawn X coordinate
     * @param y      the spawn Y coordinate
     * @param z      the spawn Z coordinate
     */
    private static void emitOneSpark(ServerLevel level, RandomSource random,
            SparkProfile p, double x, double y, double z) {
        double angle = random.nextDouble() * TWO_PI;
        double speed = p.baseSpeed() + random.nextDouble() * p.randomSpeed();
        double vx = Math.cos(angle) * speed;
        double vz = Math.sin(angle) * speed;
        double vy = p.baseFall() - random.nextDouble() * p.randomFall();
        level.sendParticles(GooParticles.GOO_SPARK.get(), x, y, z, 0, vx, vy, vz, 1.0);
    }

    /**
     * Spawns 0-1 color-tinted goo bubble particles at random XZ within the basin.
     * Rejects positions too close to live (non-expired) bubbles using per-crucible history.
     *
     * @param level    the current level
     * @param pos      the block position
     * @param surfaceY the liquid surface Y in block coords
     * @param color    the ARGB color value
     * @param random   the random source
     * @param history  per-crucible bubble spawn history
     */
    public static void spawnGooBubbles(ServerLevel level, BlockPos pos,
            float surfaceY, int color, RandomSource random,
            BubbleHistory history) {
        history.tick(level.getGameTime());
        int count = random.nextInt(IGNITION_RANDOM_COUNT);
        ColorParticleOption options = ColorParticleOption.create(
            GooParticles.GOO_BUBBLE.get(), color | ALPHA_OPAQUE);
        double y = pos.getY() + surfaceY + BUBBLE_RISE_OFFSET;
        for (int i = 0; i < count; i++) {
            trySpawnBubble(level, options, pos, y, random, history);
        }
    }

    /**
     * Tries to find a non-overlapping position and spawn one bubble.
     *
     * @param level   the server level
     * @param options the particle color options
     * @param pos     the block position
     * @param y       the spawn Y coordinate
     * @param random  the random source
     * @param history per-crucible bubble spawn history
     */
    @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop") // return exits on first successful spawn
    private static void trySpawnBubble(ServerLevel level, ColorParticleOption options,
            BlockPos pos, double y, RandomSource random, BubbleHistory history) {
        for (int attempt = 0; attempt < MAX_PLACEMENT_TRIES; attempt++) {
            double x = pos.getX() + randomInBasin(random);
            double z = pos.getZ() + randomInBasin(random);
            if (history.tooClose(x, z, MIN_SPACING_SQ)) { continue; }
            history.record(x, z);
            level.sendParticles(options, x, y, z, 1, 0, 0, 0, 0);
            return;
        }
    }

    /**
     * Per-crucible ring buffer tracking recent bubble spawn positions.
     * Entries expire after {@link #BUBBLE_TTL_TICKS} so dead positions stop blocking new spawns.
     */
    public static final class BubbleHistory {
        private final double[] recentX = new double[HISTORY_SIZE];
        private final double[] recentZ = new double[HISTORY_SIZE];
        private final long[] expiryTick = new long[HISTORY_SIZE];
        private int index;
        private long currentTick;

        /**
         * Sets the current game tick. Call once per server tick before spawning.
         *
         * @param gameTick the current game tick
         */
        void tick(long gameTick) { this.currentTick = gameTick; }

        /**
         * Returns true if (x, z) is within minSqDist of any live entry.
         *
         * @param x         the X coordinate
         * @param z         the Z coordinate
         * @param minSqDist minimum squared distance threshold
         * @return true if too close to a live entry
         */
        boolean tooClose(double x, double z, double minSqDist) {
            for (int i = 0; i < HISTORY_SIZE; i++) {
                if (currentTick >= expiryTick[i]) { continue; }
                double dx = x - recentX[i];
                double dz = z - recentZ[i];
                if (dx * dx + dz * dz < minSqDist) { return true; }
            }
            return false;
        }

        /**
         * Records a spawn position. Expires after BUBBLE_TTL_TICKS.
         *
         * @param x the X coordinate
         * @param z the Z coordinate
         */
        void record(double x, double z) {
            recentX[index] = x;
            recentZ[index] = z;
            expiryTick[index] = currentTick + BUBBLE_TTL_TICKS;
            index = (index + 1) % HISTORY_SIZE;
        }

        /**
         * Returns the X of the most recently recorded position.
         *
         * @return the last recorded X
         */
        double lastX() {
            return recentX[((index - 1) % HISTORY_SIZE + HISTORY_SIZE) % HISTORY_SIZE];
        }

        /**
         * Returns the Z of the most recently recorded position.
         *
         * @return the last recorded Z
         */
        double lastZ() {
            return recentZ[((index - 1) % HISTORY_SIZE + HISTORY_SIZE) % HISTORY_SIZE];
        }
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
