package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Trail particle spawning for blob flights: drip sparks and fog puffs
 * shed along the wake. Throttled to every other tick.
 */
final class BlobTrailParticles {

    /**
     * Tick interval for trail particle spawning (every N ticks).
     */
    private static final int PARTICLE_TICK_INTERVAL = 2;

    /**
     * Fully opaque black alpha for particle colors.
     */
    private static final int OPAQUE_BLACK = 0xFF000000;

    /**
     * Trail velocity scale for drip particles.
     */
    private static final double DRIP_VEL_SCALE = 0.05;

    /**
     * Downward velocity for drip particles.
     */
    private static final double DRIP_DOWN_VEL = -0.02;

    /**
     * Minimum squared distance from camera before fog particles spawn.
     */
    private static final double FOG_MIN_DIST_SQ = 9.0;

    /**
     * Base fog particle count before random addition.
     */
    private static final int FOG_BASE_COUNT = 2;

    /**
     * Random fog particle count range (exclusive upper bound).
     */
    private static final int FOG_RANDOM_RANGE = 3;

    /**
     * Fog position offset scale behind the blob.
     */
    private static final double FOG_POS_SCALE = 0.15;

    /**
     * Position spread multiplier for fog particles.
     */
    private static final double FOG_SPREAD = 2;

    /**
     * Velocity jitter for fog particles.
     */
    private static final double FOG_VEL_JITTER = 0.02;

    /**
     * Random offset range half-extent.
     */
    private static final double OFFSET_HALF = 0.5;

    /**
     * Random offset scale.
     */
    private static final double OFFSET_SCALE = 0.1;

    private BlobTrailParticles() {
    }

    /**
     * Spawns trail particles behind the blob: a viscous slime drip downward
     * and several radial fog puffs along the wake. Throttled to every other tick.
     *
     * @param pos    the blob world position
     * @param vel    the velocity vector
     * @param type   the goo type
     * @param flight the flight instance for tick tracking
     */
    static void spawnTrailParticles(Vec3 pos, Vec3 vel, GooType type,
                                    BlobFlightManager.BlobFlight flight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (flight.ticksElapsed % PARTICLE_TICK_INTERVAL != 0) {
            return;
        }

        int color = type.getColor();
        spawnDripParticle(mc, pos, vel, color);
        spawnFogParticles(mc, pos, vel, color);
    }

    /**
     * Spawns a single slime drip particle with gentle downward velocity.
     *
     * @param mc    the Minecraft client instance
     * @param pos   the blob world position
     * @param vel   the velocity vector
     * @param color the RGB color of the goo type
     */
    private static void spawnDripParticle(Minecraft mc, Vec3 pos, Vec3 vel, int color) {
        ColorParticleOption dripOption = ColorParticleOption.create(
                GooParticles.GOO_DRIP.get(), color | OPAQUE_BLACK);
        mc.level.addParticle(dripOption,
                pos.x + randomOffset(), pos.y + randomOffset(), pos.z + randomOffset(),
                vel.x * DRIP_VEL_SCALE, DRIP_DOWN_VEL, vel.z * DRIP_VEL_SCALE);
    }

    /**
     * Spawns 2-4 fog puff particles behind the blob, suppressed when close to the camera.
     *
     * @param mc    the Minecraft client instance
     * @param pos   the blob world position
     * @param vel   the velocity vector
     * @param color the RGB color of the goo type
     */
    private static void spawnFogParticles(Minecraft mc, Vec3 pos, Vec3 vel, int color) {
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        if (pos.distanceToSqr(camPos) <= FOG_MIN_DIST_SQ) {
            return;
        }

        ColorParticleOption fogOption = ColorParticleOption.create(
                GooParticles.GOO_FOG.get(), color | OPAQUE_BLACK);
        int fogCount = FOG_BASE_COUNT + ThreadLocalRandom.current().nextInt(FOG_RANDOM_RANGE);
        for (int i = 0; i < fogCount; i++) {
            emitSingleFogPuff(mc, fogOption, pos, vel);
        }
    }

    /**
     * Emits one fog puff particle at a jittered position behind the blob.
     *
     * @param mc        the Minecraft client instance
     * @param fogOption the color particle option for fog
     * @param pos       the blob world position
     * @param vel       the velocity vector
     */
    private static void emitSingleFogPuff(Minecraft mc, ColorParticleOption fogOption,
                                          Vec3 pos, Vec3 vel) {
        mc.level.addParticle(fogOption,
                pos.x - vel.x * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                pos.y - vel.y * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                pos.z - vel.z * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                randomOffset() * FOG_VEL_JITTER, randomOffset() * FOG_VEL_JITTER, randomOffset() * FOG_VEL_JITTER);
    }

    /**
     * Small random offset for particle position jitter.
     *
     * @return a small random offset value
     */
    private static double randomOffset() {
        return (ThreadLocalRandom.current().nextDouble() - OFFSET_HALF) * OFFSET_SCALE;
    }
}
