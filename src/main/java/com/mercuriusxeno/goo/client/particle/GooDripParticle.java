package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Blocky slime drip particle for thrown goo blob trails. Modeled after
 * vanilla's lava/water drip particles - falls under gravity, splats on
 * ground contact. Spawned directly into the fall phase (no hang phase)
 * because these drip off a moving blob, not a stationary block.
 */
public class GooDripParticle extends SingleQuadParticle {

    /** Gravity matching vanilla DripParticle base. */
    private static final float DRIP_GRAVITY = 0.06f;

    private final float red;
    private final float green;
    private final float blue;

    /** Creates a goo drip tinted to the given color. */
    private GooDripParticle(ClientLevel level, double x, double y, double z,
            float red, float green, float blue, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.setSize(0.01f, 0.01f);
        this.gravity = DRIP_GRAVITY;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.preMoveUpdate();
        if (!this.removed) {
            this.yd -= this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.postMoveUpdate();
            if (!this.removed) {
                this.xd *= 0.98;
                this.yd *= 0.98;
                this.zd *= 0.98;
            }
        }
    }

    /** Counts down lifetime; subclasses override for phase transitions. */
    protected void preMoveUpdate() {
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    /** Hook for ground-contact behavior; subclasses override. */
    protected void postMoveUpdate() {
    }

    /** Packs stored RGB into ARGB for spawning child particles. */
    protected int packedColor() {
        int r = (int) (red * 255) & 0xFF;
        int g = (int) (green * 255) & 0xFF;
        int b = (int) (blue * 255) & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ── Fall particle ──────────────────────────────────────────────────
    /**
     * The main drip - falls under gravity, spawns a land splat on ground contact.
     * This is what the blob flight trail spawns directly.
     */
    private static class FallParticle extends GooDripParticle {

        FallParticle(ClientLevel level, double x, double y, double z,
                double vx, double vy, double vz,
                float red, float green, float blue, SpriteSet sprites) {
            super(level, x, y, z, red, green, blue, sprites);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.lifetime = (int) (64.0 / (level.getRandom().nextFloat() * 0.8 + 0.2));
        }

        /** Spawns a landing splat on ground contact. */
        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
                ColorParticleOption landOption = ColorParticleOption.create(
                        GooParticles.GOO_DRIP_LAND.get(), packedColor());
                this.level.addParticle(landOption,
                        this.x, this.y, this.z, 0.0, 0.0, 0.0);
            }
        }
    }

    // ── Land particle ──────────────────────────────────────────────────
    /**
     * Ground splat that lies flat and spreads out while fading.
     * Overrides the billboard orientation so the quad faces upward,
     * giving the visual impression of a drip flattening on impact.
     */
    private static class LandParticle extends GooDripParticle {

        /** Quaternion that lays the quad flat on the XZ plane (normal facing +Y). */
        private static final Quaternionf FLAT_ROTATION =
                new Quaternionf().rotateX((float) (-Math.PI / 2.0));

        private final int maxLifetime;

        LandParticle(ClientLevel level, double x, double y, double z,
                float red, float green, float blue, SpriteSet sprites) {
            super(level, x, y, z, red, green, blue, sprites);
            // Nudge above the block surface so the flat quad doesn't z-fight.
            this.y += 0.02;
            this.yo = this.y;
            this.quadSize *= 1.2f;
            this.lifetime = (int) (10.0 / (level.getRandom().nextFloat() * 0.8 + 0.2));
            this.maxLifetime = this.lifetime;
            this.gravity = 0.0f;
        }

        /** Renders as a flat, ground-facing quad instead of a camera billboard. */
        @Override
        public void extract(QuadParticleRenderState reusedState, Camera camera,
                float partialTick) {
            this.extractRotatedQuad(reusedState, camera,
                    new Quaternionf(FLAT_ROTATION), partialTick);
        }

        /** Grows wider over lifetime to simulate the drip spreading on impact. */
        @Override
        public float getQuadSize(float partialTick) {
            float progress = 1.0f - ((float) this.lifetime / (float) this.maxLifetime);
            return this.quadSize * (1.0f + progress * 1.0f);
        }

        /** Fades out as the splat spreads. */
        @Override
        protected void preMoveUpdate() {
            this.alpha = (float) this.lifetime / (float) this.maxLifetime;
            super.preMoveUpdate();
        }
    }

    // ── Providers ──────────────────────────────────────────────────────

    /** Provider for the falling drip - used by BlobFlightRenderer trail. */
    public static class Provider implements ParticleProvider<ColorParticleOption> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(
                ColorParticleOption options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new FallParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed,
                    options.getRed(), options.getGreen(), options.getBlue(), sprites);
        }
    }

    /** Provider for the ground splat - spawned by FallParticle on impact. */
    public static class LandProvider implements ParticleProvider<ColorParticleOption> {

        private final SpriteSet sprites;

        public LandProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(
                ColorParticleOption options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new LandParticle(level, x, y, z,
                    options.getRed(), options.getGreen(), options.getBlue(), sprites);
        }
    }
}
