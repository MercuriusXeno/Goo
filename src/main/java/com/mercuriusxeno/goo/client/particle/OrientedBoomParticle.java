package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

/**
 * Sonic-boom-style particle oriented flat against the blast plane
 * instead of billboarding toward the camera. The blast direction
 * arrives via {@link OrientedBoomParticleOptions} as a typed
 * {@link Direction} field.
 */
public class OrientedBoomParticle extends HugeExplosionParticle {

    /** Half-turn in radians for 180-degree rotations. */
    private static final float HALF_PI = (float) (Math.PI / 2);
    /** Particle lifetime in ticks. */
    private static final int BOOM_LIFETIME = 16;
    /** Base quad size for the boom particle. */
    private static final float BOOM_QUAD_SIZE = 1.5F;
    /** Pre-computed rotation templates per direction, copied on use. */
    private static final Quaternionf[] ROTATIONS = buildRotationTable();

    private final Quaternionf fixedRotation;
    private final Quaternionf flippedRotation;

    /** Creates an oriented boom particle facing perpendicular to the blast axis.
     *
     * @param level     the client level
     * @param x         the X position
     * @param y         the Y position
     * @param z         the Z position
     * @param size      the size parameter
     * @param sprites   the sprite set
     * @param blastDir  the blast direction (quad faces this way)
     */
    public OrientedBoomParticle(ClientLevel level, double x, double y, double z,
                                double size, SpriteSet sprites, Direction blastDir) {
        super(level, x, y, z, size, sprites);
        this.lifetime = BOOM_LIFETIME;
        this.quadSize = BOOM_QUAD_SIZE;
        this.setSpriteFromAge(sprites);
        this.fixedRotation = computeRotation(blastDir);
        this.flippedRotation = new Quaternionf(fixedRotation).rotateY((float) Math.PI);
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        this.extractRotatedQuad(state, camera, fixedRotation, partialTick);
        this.extractRotatedQuad(state, camera, flippedRotation, partialTick);
    }

    private static Quaternionf[] buildRotationTable() {
        Quaternionf[] table = new Quaternionf[Direction.values().length];
        table[Direction.UP.ordinal()] = new Quaternionf().rotationX(-HALF_PI);
        table[Direction.DOWN.ordinal()] = new Quaternionf().rotationX(HALF_PI);
        table[Direction.NORTH.ordinal()] = new Quaternionf().identity();
        table[Direction.SOUTH.ordinal()] = new Quaternionf().rotationY((float) Math.PI);
        table[Direction.EAST.ordinal()] = new Quaternionf().rotationY(-HALF_PI);
        table[Direction.WEST.ordinal()] = new Quaternionf().rotationY(HALF_PI);
        return table;
    }

    /** Returns a fresh copy of the pre-computed rotation for the given direction.
     *
     * @param dir the blast direction
     * @return the orientation quaternion
     */
    private static Quaternionf computeRotation(Direction dir) {
        return new Quaternionf(ROTATIONS[dir.ordinal()]);
    }

    /** Provider that reads blast direction from the typed options. */
    public static class Provider implements ParticleProvider<OrientedBoomParticleOptions> {
        private final SpriteSet sprites;

        /** Creates a provider with the given sprite set.
         *
         * @param sprites the sprite set
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                OrientedBoomParticleOptions options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new OrientedBoomParticle(level, x, y, z, 0, sprites, options.direction());
        }
    }
}
