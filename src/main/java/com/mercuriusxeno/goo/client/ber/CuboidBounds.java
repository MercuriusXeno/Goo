package com.mercuriusxeno.goo.client.ber;

/** Axis-aligned box bounds for BER rendering. Lighter than AABB (floats, no clamping). */
record CuboidBounds(float x0, float x1, float z0, float z1, float yBot, float yTop) {
}
