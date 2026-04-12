package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

/**
 * Sonic-boom-style particle oriented flat against the blast plane
 * instead of billboarding toward the camera. The blast direction is
 * encoded in the xAux parameter as a Direction ordinal.
 */
public class OrientedBoomParticle extends HugeExplosionParticle {

    /** Half-turn in radians for 180-degree rotations. */
    private static final float HALF_PI = (float) (Math.PI / 2);
    /** Particle lifetime in ticks. */
    private static final int BOOM_LIFETIME = 16;
    /** Base quad size for the boom particle. */
    private static final float BOOM_QUAD_SIZE = 1.5F;

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

    /** Computes a quaternion that orients the quad perpendicular to the
     * given direction. Default particle quad faces +Z (toward camera),
     * so we rotate to face the blast direction.
     *
     * @param dir the blast direction
     * @return the orientation quaternion
     */
    private static Quaternionf computeRotation(Direction dir) {
        return switch (dir) {
            case UP -> new Quaternionf().rotationX(-HALF_PI);
            case DOWN -> new Quaternionf().rotationX(HALF_PI);
            case NORTH -> new Quaternionf().identity();
            case SOUTH -> new Quaternionf().rotationY((float) Math.PI);
            case EAST -> new Quaternionf().rotationY(-HALF_PI);
            case WEST -> new Quaternionf().rotationY(HALF_PI);
        };
    }

    /** Provider that reads blast direction from the xAux parameter. */
        public static class Provider implements ParticleProvider<SimpleParticleType> {
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
                SimpleParticleType options, ClientLevel level,
                double x, double y, double z,
                double xAux, double yAux, double zAux,
                RandomSource random) {
            int ordinal = (int) xAux;
            Direction[] dirs = Direction.values();
            Direction dir = (ordinal >= 0 && ordinal < dirs.length) ? dirs[ordinal] : Direction.UP;
            return new OrientedBoomParticle(level, x, y, z, 0, sprites, dir);
        }
    }
}
