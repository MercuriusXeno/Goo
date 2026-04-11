#version 330

in vec2 faceUv;

out vec4 fragColor;

// Overall additive brightness multiplier. Matches the corona's
// intensity ballpark so the cube style reads at a similar exposure
// level as the sphere style when A/B-switched.
const float EDGE_INTENSITY = 1.2;

// Width of the glowing edge band in UV units (0..1 across a face).
// At 0.18 each of the four edges occupies the outer ~18% of its face,
// so the interior ~64% square of each face is mostly dark and the
// corners (where two edge bands cross) get the brightest sum.
const float EDGE_WIDTH = 0.18;

// Pure white halo to match the corona style. Single constant so live
// tuning is one line.
const vec3 EDGE_WHITE = vec3(1.0, 1.0, 1.0);

void main() {
    // Distance from the nearest edge of the face in UV units:
    // edgeDist = min(u, 1-u, v, 1-v), clamped to [0, 0.5]. 0 at the
    // edge, 0.5 at the face center.
    float edgeDist = min(min(faceUv.x, 1.0 - faceUv.x),
                         min(faceUv.y, 1.0 - faceUv.y));

    // Smoothstep from 1 (at the edge) down to 0 (EDGE_WIDTH inward).
    // This is the per-edge falloff; corners stack two of these.
    float strength = 1.0 - smoothstep(0.0, EDGE_WIDTH, edgeDist);

    // LIGHTNING blend (SRC_ALPHA, ONE): additive contribution is
    // EDGE_WHITE * EDGE_INTENSITY * strength added to the scene.
    fragColor = vec4(EDGE_WHITE * EDGE_INTENSITY, strength);
}
