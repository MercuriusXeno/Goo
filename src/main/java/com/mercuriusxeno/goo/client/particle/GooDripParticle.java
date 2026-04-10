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
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Blocky slime drip particle for thrown goo blob trails. Modeled after
 * vanilla's lava/water drip particles - falls under gravity, splats on
 * ground contact. Spawned directly into the fall phase (no hang phase)
 * because these drip off a moving blob, not a stationary block.
 */
public class GooDripParticle extends SingleQuadParticle {

    /** Gravity matching vanilla DripParticle base. */
    private static final float DRIP_GRAVITY = 0.06f;

    /** Initial particle size for drip collision box. */
    private static final float DRIP_SIZE = 0.01f;

    /** Drag coefficient per tick for velocity damping. */
    private static final float DRAG = 0.98f;

    /** Maximum channel value for color packing. */
    private static final int MAX_CHANNEL = 255;

    /** Mask for extracting a single color channel. */
    private static final int CHANNEL_MASK = 0xFF;

    /** Fully opaque black alpha for color packing. */
    private static final int OPAQUE_BLACK = 0xFF000000;

    /** Bit shift for red channel in ARGB packing. */
    private static final int RED_SHIFT = 16;

    /** Bit shift for green channel in ARGB packing. */
    private static final int GREEN_SHIFT = 8;

    /** Lifetime divisor for randomized particle duration. */
    private static final double LIFETIME_DIVISOR = 64.0;

    /** Minimum lifetime random factor. */
    private static final double LIFETIME_MIN_FACTOR = 0.2;

    /** Lifetime random range. */
    private static final double LIFETIME_RANGE = 0.8;

    /** Ground nudge to prevent z-fighting for land splats. */
    private static final double LAND_SURFACE_NUDGE = 0.02;

    /** Scale factor for land splat quad size. */
    private static final float LAND_QUAD_SCALE = 1.2f;

    /** Land splat lifetime divisor. */
    private static final double LAND_LIFETIME_DIVISOR = 10.0;

    private final float red;
    private final float green;
    private final float blue;

    /**
     * Creates a goo drip tinted to the given color.
     *
     * @param level   the client level
     * @param x       the X spawn position
     * @param y       the Y spawn position
     * @param z       the Z spawn position
     * @param red     the red color component
     * @param green   the green color component
     * @param blue    the blue color component
     * @param sprites the sprite set for animation frames
     */
    private GooDripParticle(ClientLevel level, double x, double y, double z,
            float red, float green, float blue, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.setSize(DRIP_SIZE, DRIP_SIZE);
        this.gravity = DRIP_GRAVITY;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
    }

    /**
     * Renders on the translucent particle layer for alpha blending.
     *
     * @return the translucent particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /** Applies gravity, movement, drag, and delegates to pre/post move hooks. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.preMoveUpdate();
        if (!this.removed) {
            applyPhysics();
        }
    }

    /** Applies gravity, moves the particle, runs post-move hooks, and damps velocity. */
    private void applyPhysics() {
        this.yd -= this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.postMoveUpdate();
        if (!this.removed) {
            this.xd *= DRAG;
            this.yd *= DRAG;
            this.zd *= DRAG;
        }
    }

    /** Counts down lifetime; subclasses override for phase transitions. */
    protected void preMoveUpdate() {
        this.lifetime--;
        if (this.lifetime <= 0) {
            this.remove();
        }
    }

    /** Hook for ground-contact behavior; subclasses override. */
    protected void postMoveUpdate() {
    }

    /**
     * Packs stored RGB into ARGB for spawning child particles.
     *
     * @return the packed ARGB color integer
     */
    protected int packedColor() {
        int r = (int) (red * MAX_CHANNEL) & CHANNEL_MASK;
        int g = (int) (green * MAX_CHANNEL) & CHANNEL_MASK;
        int b = (int) (blue * MAX_CHANNEL) & CHANNEL_MASK;
        return OPAQUE_BLACK | (r << RED_SHIFT) | (g << GREEN_SHIFT) | b;
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
            this.lifetime = (int) (LIFETIME_DIVISOR / (level.getRandom().nextFloat() * LIFETIME_RANGE + LIFETIME_MIN_FACTOR));
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
            this.y += LAND_SURFACE_NUDGE;
            this.yo = this.y;
            this.quadSize *= LAND_QUAD_SCALE;
            this.lifetime = (int) (LAND_LIFETIME_DIVISOR / (level.getRandom().nextFloat() * LIFETIME_RANGE + LIFETIME_MIN_FACTOR));
            this.maxLifetime = this.lifetime;
            this.gravity = 0.0f;
        }

        /**
         * Renders as a flat, ground-facing quad instead of a camera billboard.
         *
         * @param reusedState the reusable render state for quad particles
         * @param camera the active camera for view transform
         * @param partialTick the partial tick for interpolation
         */
        @Override
        public void extract(QuadParticleRenderState reusedState, Camera camera,
                float partialTick) {
            this.extractRotatedQuad(reusedState, camera,
                    new Quaternionf(FLAT_ROTATION), partialTick);
        }

        /**
         * Grows wider over lifetime to simulate the drip spreading on impact.
         *
         * @param partialTick the partial tick for interpolation
         * @return the scaled quad size for this frame
         */
        @Override
        public float getQuadSize(float partialTick) {
            float progress = 1.0f - (float) this.lifetime / this.maxLifetime;
            return this.quadSize * (1.0f + progress * 1.0f);
        }

        /** Fades out as the splat spreads. */
        @Override
        protected void preMoveUpdate() {
            this.alpha = (float) this.lifetime / this.maxLifetime;
            super.preMoveUpdate();
        }
    }

    // ── Providers ──────────────────────────────────────────────────────

    /** Provider for the falling drip - used by BlobFlightRenderer trail. */
    public static class Provider implements ParticleProvider<ColorParticleOption> {

        private final SpriteSet sprites;

        /**
         * Creates a provider with the given sprite set from the particle definition.
         *
         * @param sprites the sprite set for drip animation frames
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Creates a falling goo drip particle, extracting RGB from the color option.
         *
         * @param options the color particle data carrying RGB values
         * @param level the client level to spawn in
         * @param x the x spawn coordinate
         * @param y the y spawn coordinate
         * @param z the z spawn coordinate
         * @param xSpeed the x velocity for the falling drip
         * @param ySpeed the y velocity for the falling drip
         * @param zSpeed the z velocity for the falling drip
         * @param random the random source
         * @return the new falling drip particle, or null if skipped
         */
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

        /**
         * Creates a land provider with the given sprite set.
         *
         * @param sprites the sprite set for land splat rendering
         */
        public LandProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Creates a ground splat particle, extracting RGB from the color option.
         *
         * @param options the color particle data carrying RGB values
         * @param level the client level to spawn in
         * @param x the x spawn coordinate
         * @param y the y spawn coordinate
         * @param z the z spawn coordinate
         * @param xSpeed the x velocity (unused for land splats)
         * @param ySpeed the y velocity (unused for land splats)
         * @param zSpeed the z velocity (unused for land splats)
         * @param random the random source
         * @return the new land splat particle, or null if skipped
         */
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
