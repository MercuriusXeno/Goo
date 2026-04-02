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
public class GooFogParticle extends SingleQuadParticle {

    /** Initial alpha - never fully opaque so overlapping puffs layer naturally. */
    private static final float START_ALPHA = 0.4f;

    /** Creates a fog puff tinted to the goo type color. */
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
        this.setSize(0.01f, 0.01f);
        this.gravity = 0f;
        this.friction = 0.95f;
        this.hasPhysics = false;
        this.lifetime = 8 + level.getRandom().nextInt(8); // 8-15 ticks
        this.quadSize = 0.1f + level.getRandom().nextFloat() * 0.1f; // 0.1-0.2
        this.alpha = START_ALPHA;
    }

    @Override
    public void tick() {
        super.tick();
        // Linear fade from START_ALPHA to 0 over the full lifetime
        float progress = (float) this.age / this.lifetime;
        this.alpha = START_ALPHA * (1f - progress);
    }

    /** Translucent particle sheet with depth sorting for alpha blend. */
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

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

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
