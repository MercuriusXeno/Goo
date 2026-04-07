package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Small glowing spark particle with gravity and block collision.
 * Respects initial velocity from the server, falls realistically,
 * and bounces off surfaces. Used for crucible rod-contact sparks.
 */
public final class GooSparkParticle extends SingleQuadParticle {

    /** Gravity pull per tick (vanilla lava uses 0.75). */
    private static final float SPARK_GRAVITY = 0.6f;
    /** Velocity damping per tick. */
    private static final float SPARK_FRICTION = 0.96f;
    /** Ticks to skip collision so sparks escape the platform VoxelShape. */
    private static final int PHYSICS_DELAY = 3;

    /** Particle hitbox size (tiny). */
    private static final float SPARK_SIZE = 0.01f;
    /** Base lifetime for spark particles. */
    private static final int BASE_LIFETIME = 10;
    /** Random lifetime variance (exclusive bound). */
    private static final int LIFETIME_VARIANCE = 10;
    /** Base quad size for spark rendering. */
    private static final float BASE_QUAD_SIZE = 0.04f;
    /** Random quad size variance. */
    private static final float QUAD_SIZE_VARIANCE = 0.02f;
    /** Minimum green channel for warm spark color. */
    private static final float MIN_GREEN = 0.6f;
    /** Green channel random range. */
    private static final float GREEN_RANGE = 0.4f;
    /** Maximum blue channel for warm spark color. */
    private static final float MAX_BLUE = 0.3f;
    /** Minimum speed for smoke trail spawning. */
    private static final double SMOKE_SPEED = 0.01;

    /**
     * Creates a spark particle that falls with gravity and collides with blocks.
     *
     * @param level   the client level
     * @param x       the X spawn position
     * @param y       the Y spawn position
     * @param z       the Z spawn position
     * @param vx      the initial X velocity
     * @param vy      the initial Y velocity
     * @param vz      the initial Z velocity
     * @param sprites the sprite set for animation frames
     */
    private GooSparkParticle(ClientLevel level, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.setSize(SPARK_SIZE, SPARK_SIZE);
        this.gravity = SPARK_GRAVITY;
        this.friction = SPARK_FRICTION;
        this.hasPhysics = false;
        this.lifetime = BASE_LIFETIME + level.getRandom().nextInt(LIFETIME_VARIANCE);
        this.quadSize = BASE_QUAD_SIZE + level.getRandom().nextFloat() * QUAD_SIZE_VARIANCE;
        this.rCol = 1.0f;
        this.gCol = MIN_GREEN + level.getRandom().nextFloat() * GREEN_RANGE;
        this.bCol = level.getRandom().nextFloat() * MAX_BLUE;
    }

    /** Enables block collision after clearing the platform, and trails smoke. */
    @Override
    public void tick() {
        if (this.age == PHYSICS_DELAY) {
            this.hasPhysics = true;
        }
        super.tick();
        if (!this.removed && this.hasPhysics) {
            trailSmoke();
        }
    }

    /** Spawns a wisp of smoke with decreasing probability as the spark ages. */
    private void trailSmoke() {
        float chance = 1f - (float) this.age / this.lifetime;
        if (this.random.nextFloat() < chance) {
            this.level.addParticle(ParticleTypes.SMOKE,
                this.x, this.y, this.z, 0, SMOKE_SPEED, 0);
        }
    }

    /**
     * Sparks render on the opaque particle sheet.
     *
     * @return the opaque particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.OPAQUE;
    }

    /**
     * Shrinks quadratically over lifetime for a natural fade-out.
     *
     * @param partialTick the partial tick for interpolation
     * @return the scaled quad size for this frame
     */
    @Override
    public float getQuadSize(float partialTick) {
        float progress = (this.age + partialTick) / this.lifetime;
        return this.quadSize * (1f - progress * progress);
    }

    /**
     * Provider that creates GooSparkParticles from SimpleParticleType.
     * Registered via RegisterParticleProvidersEvent with a SpriteSet.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider with the given sprite set.
         *
         * @param sprites the sprite set for spark rendering
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Creates a spark particle, passing through the server-provided velocity.
         *
         * @param type the simple particle type descriptor
         * @param level the client level to spawn in
         * @param x the x spawn coordinate
         * @param y the y spawn coordinate
         * @param z the z spawn coordinate
         * @param xSpeed the x velocity from the server
         * @param ySpeed the y velocity from the server
         * @param zSpeed the z velocity from the server
         * @param random the random source
         * @return the new spark particle, or null if skipped
         */
        @Override
        public @Nullable GooSparkParticle createParticle(
                SimpleParticleType type, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new GooSparkParticle(level, x, y, z,
                xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
