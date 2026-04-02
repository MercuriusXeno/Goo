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
public class GooSparkParticle extends SingleQuadParticle {

    /** Gravity pull per tick (vanilla lava uses 0.75). */
    private static final float SPARK_GRAVITY = 0.6f;
    /** Velocity damping per tick. */
    private static final float SPARK_FRICTION = 0.96f;
    /** Ticks to skip collision so sparks escape the platform VoxelShape. */
    private static final int PHYSICS_DELAY = 3;

    /** Creates a spark particle that falls with gravity and collides with blocks. */
    private GooSparkParticle(ClientLevel level, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.setSize(0.01f, 0.01f);
        this.gravity = SPARK_GRAVITY;
        this.friction = SPARK_FRICTION;
        this.hasPhysics = false;
        this.lifetime = 10 + level.getRandom().nextInt(10);
        this.quadSize = 0.04f + level.getRandom().nextFloat() * 0.02f;
        this.rCol = 1.0f;
        this.gCol = 0.6f + level.getRandom().nextFloat() * 0.4f;
        this.bCol = level.getRandom().nextFloat() * 0.3f;
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
                this.x, this.y, this.z, 0, 0.01, 0);
        }
    }

    /** Sparks render on the opaque particle sheet. */
    @Override
    public Layer getLayer() {
        return Layer.OPAQUE;
    }

    /** Shrinks quadratically over lifetime for a natural fade-out. */
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

        /** Creates a provider with the given sprite set. */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /** Creates a spark particle, passing through the server-provided velocity. */
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
