package com.mercuriusxeno.goo.client.ability;

/**
 * Orthonormal basis math for ability-effect cone visuals: the per-marker
 * metal spike trap and the in-flight metal dart trail. Both extend a
 * 3-sided cone from a base point along a direction vector and need a
 * stable perpendicular + cross basis to place vertices around the cone's
 * circular base.
 *
 * <p>The basis is an array of six floats laid out as
 * {@code [perpX, perpY, perpZ, crossX, crossY, crossZ]}. Consumers index
 * into it through the public {@code PERP_X} / {@code CROSS_X} constants
 * to keep call sites self-documenting.
 */
public final class ConeGeometry {

    /** Index of the perpendicular X component in the basis array. */
    public static final int PERP_X = 0;
    /** Index of the perpendicular Y component in the basis array. */
    public static final int PERP_Y = 1;
    /** Index of the perpendicular Z component in the basis array. */
    public static final int PERP_Z = 2;
    /** Index of the cross-product X component in the basis array. */
    public static final int CROSS_X = 3;
    /** Index of the cross-product Y component in the basis array. */
    public static final int CROSS_Y = 4;
    /** Index of the cross-product Z component in the basis array. */
    public static final int CROSS_Z = 5;

    /**
     * Threshold for choosing a perpendicular seed vector. When the
     * direction is too close to the world Y axis, fall back to {1, 0, 0}
     * to avoid a degenerate cross product.
     */
    private static final float DIRECTION_THRESHOLD = 0.9f;

    private ConeGeometry() {
    }

    /**
     * Computes orthonormal {perp, cross} basis vectors for the given
     * direction. Both output vectors are unit length and perpendicular to
     * the direction (and to each other).
     *
     * @param dirX cone direction X component (normalized)
     * @param dirY cone direction Y component (normalized)
     * @param dirZ cone direction Z component (normalized)
     * @return six-element basis array {@code [perpX, perpY, perpZ, crossX, crossY, crossZ]}
     */
    public static float[] computeBasis(float dirX, float dirY, float dirZ) {
        float[] perp = seedPerp(dirX, dirY, dirZ);
        orthonormalize(perp, dirX, dirY, dirZ);
        float crossX = dirY * perp[PERP_Z] - dirZ * perp[PERP_Y];
        float crossY = dirZ * perp[PERP_X] - dirX * perp[PERP_Z];
        float crossZ = dirX * perp[PERP_Y] - dirY * perp[PERP_X];
        return new float[]{perp[PERP_X], perp[PERP_Y], perp[PERP_Z], crossX, crossY, crossZ};
    }

    /**
     * Picks a seed perpendicular vector that avoids near-parallel
     * alignment with {@code dir}. Returns {-dirZ, 0, dirX} for most
     * directions; falls back to {1, 0, 0} when {@code dir} runs close to
     * the Y axis.
     * @param dirX cone direction X
     * @param dirY cone direction Y
     * @param dirZ cone direction Z
     * @return three-element seed perpendicular vector
     */
    private static float[] seedPerp(float dirX, float dirY, float dirZ) {
        if (Math.abs(dirY) < DIRECTION_THRESHOLD) {
            return new float[]{-dirZ, 0, dirX};
        }
        return new float[]{1, 0, 0};
    }

    /**
     * Gram-Schmidt orthonormalizes {@code perp} against {@code dir} in
     * place: subtract the projection onto {@code dir}, then normalize.
     * @param perp the perpendicular seed vector to normalize in place
     * @param dirX reference direction X
     * @param dirY reference direction Y
     * @param dirZ reference direction Z
     */
    private static void orthonormalize(float[] perp, float dirX, float dirY, float dirZ) {
        float dot = perp[PERP_X] * dirX + perp[PERP_Y] * dirY + perp[PERP_Z] * dirZ;
        perp[PERP_X] -= dot * dirX;
        perp[PERP_Y] -= dot * dirY;
        perp[PERP_Z] -= dot * dirZ;
        float len = (float) Math.sqrt(
                perp[PERP_X] * perp[PERP_X] + perp[PERP_Y] * perp[PERP_Y]
                        + perp[PERP_Z] * perp[PERP_Z]);
        perp[PERP_X] /= len;
        perp[PERP_Y] /= len;
        perp[PERP_Z] /= len;
    }
}
