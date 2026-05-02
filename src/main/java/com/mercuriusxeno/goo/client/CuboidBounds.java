package com.mercuriusxeno.goo.client;

/**
 * Axis-aligned box bounds for BER rendering. Lighter than AABB (floats, no clamping).
 *
 * @param x0   minimum X
 * @param x1   maximum X
 * @param z0   minimum Z
 * @param z1   maximum Z
 * @param yBot bottom Y
 * @param yTop top Y
 */
public record CuboidBounds(float x0, float x1, float z0, float z1, float yBot, float yTop) {

    /** Returns a copy with the Y range replaced. */
    public CuboidBounds withY(float newYBot, float newYTop) {
        return new CuboidBounds(x0, x1, z0, z1, newYBot, newYTop);
    }
}
