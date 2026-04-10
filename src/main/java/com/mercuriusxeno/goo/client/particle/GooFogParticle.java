package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Radial gradient fog puff for blob flight trails.
 * Alpha-blended billboard on Layer.TRANSLUCENT - starts semi-transparent and
 * fades to zero, giving a smoggy buildup when multiple puffs overlap.
 * Uses a radial-gradient "dot" texture for soft edges.
 */
public final class GooFogParticle extends SingleQuadParticle {

    /** Initial alpha - never fully opaque so overlapping puffs layer naturally. */
    private static final float START_ALPHA = 0.4f;
    /** Initial collision box size for fog particles. */
    private static final float COLLISION_SIZE = 0.01f;
    /** Velocity damping factor per tick. */
    private static final float FRICTION_FACTOR = 0.95f;
    /** Base lifetime in ticks before random extension. */
    private static final int BASE_LIFETIME = 8;
    /** Base quad size before random scaling. */
    private static final float BASE_QUAD_SIZE = 0.1f;

    /**
     * Creates a fog puff tinted to the goo type color.
     *
     * @param level   the client level
     * @param x       the X spawn position
     * @param y       the Y spawn position
     * @param z       the Z spawn position
     * @param vx      the initial X velocity
     * @param vy      the initial Y velocity
     * @param vz      the initial Z velocity
     * @param red     the red color component
     * @param green   the green color component
     * @param blue    the blue color component
     * @param sprites the sprite set for animation frames
     */
    private GooFogParticle(ClientLevel level, double x, double y, double z,
            double vx, double vy, double vz,
            float red, float green, float blue, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
        configureFogDefaults(level.getRandom());
    }

    /**
     * Sets physics, size, lifetime, and alpha defaults for a fog puff.
     * @param random the random source for lifetime and size variance
     */
    private void configureFogDefaults(RandomSource random) {
        this.setSize(COLLISION_SIZE, COLLISION_SIZE);
        this.gravity = 0f;
        this.friction = FRICTION_FACTOR;
        this.hasPhysics = false;
        this.lifetime = BASE_LIFETIME + random.nextInt(BASE_LIFETIME);
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * BASE_QUAD_SIZE;
        this.alpha = START_ALPHA;
    }

    /** Advances the particle and linearly fades alpha to zero over its lifetime. */
    @Override
    public void tick() {
        super.tick();
        // Linear fade from START_ALPHA to 0 over the full lifetime
        float progress = (float) this.age / this.lifetime;
        this.alpha = START_ALPHA * (1f - progress);
    }

    /**
     * Translucent particle sheet with depth sorting for alpha blend.
     *
     * @return the translucent particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Provider that creates GooFogParticles from ColorParticleOption.
     * Registered via RegisterParticleProvidersEvent with a SpriteSet.
     */
    public static class Provider implements ParticleProvider<ColorParticleOption> {

        private final SpriteSet sprites;

        /**
         * Creates a provider with the given sprite set from the particle definition.
         *
         * @param sprites the sprite set for fog puff rendering
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Creates a fog puff particle, extracting RGB from the color option.
         *
         * @param options the color particle data carrying RGB values
         * @param level the client level to spawn in
         * @param x the x spawn coordinate
         * @param y the y spawn coordinate
         * @param z the z spawn coordinate
         * @param xSpeed the x velocity for drift
         * @param ySpeed the y velocity for drift
         * @param zSpeed the z velocity for drift
         * @param random the random source
         * @return the new fog puff particle, or null if skipped
         */
        @Override
        public @Nullable GooFogParticle createParticle(
                ColorParticleOption options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new GooFogParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed,
                    options.getRed(), options.getGreen(), options.getBlue(), sprites);
        }
    }
}
