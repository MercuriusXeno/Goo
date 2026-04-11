#version 330

in float radialT;
in float angularT;
in float animPhase;

out vec4 fragColor;

// ── Tunables ──────────────────────────────────────────────────────────

// Overall additive brightness multiplier. Bump for harder blow-out.
const float DISK_INTENSITY = 3.0;

// Hot inner edge color (near-white, fusion-hot ISCO).
const vec3 INNER_COLOR = vec3(1.00, 0.97, 0.88);

// Cool outer edge color (orange, cooler outer disc material).
const vec3 OUTER_COLOR = vec3(1.00, 0.55, 0.18);

// Inner edge fade width in radialT units. Kept tight so the inside of
// the ring reads as a hard rim against the black sphere.
const float INNER_FADE = 0.08;

// Outer edge fade width in radialT units. Generous so the ring's
// trailing edge feathers away into the void instead of cutting flat.
const float OUTER_FADE = 0.45;

// How much of a black hole's mass wins the inner-edge whiten. 0.0 keeps
// the outer color all the way to the inner edge; 1.0 makes the whole
// disc blend toward INNER_COLOR. 0.55 keeps the orange read dominant.
const float WHITEN_STRENGTH = 0.55;

// Noise-driven filament contrast. Lower = smoother/brighter disc,
// higher = more "patchy" disc with visible dark lanes.
const float STRUCTURE_CONTRAST = 0.55;

// Base angular band count. Higher = more visible spiral arms.
const float BAND_FREQ_LOW = 5.0;
const float BAND_FREQ_HIGH = 13.0;

// ── Procedural 2D value noise (no texture binds) ──────────────────────

float hash21(vec2 p) {
    // Canonical sine hash. Good enough for this much noise.
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    // Smoothstep interpolation.
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    // ── Radial brightness ────────────────────────────────────────────
    // Bright right at the inner edge and fade across the ring toward
    // the outer edge. Both edges are softened so the ring blends into
    // the corona inside and into the void outside.
    float innerEdge = smoothstep(0.0, INNER_FADE, radialT);
    float outerEdge = 1.0 - smoothstep(1.0 - OUTER_FADE, 1.0, radialT);
    float brightness = innerEdge * outerEdge;

    // ── Keplerian swirl ──────────────────────────────────────────────
    // Inner material orbits faster than outer (v ~ 1 / sqrt(r)). We
    // fake the same shear by adding a rotation-speed-scaled offset to
    // the angular coordinate before the noise lookup, so inner bands
    // lag outer bands and the pattern streaks into a spiral.
    float rParam = 0.25 + radialT;
    float rotationalVelocity = 1.0 / sqrt(rParam);
    float timeAngle = animPhase * 6.28318530;
    float streakedAngle = angularT + rotationalVelocity * 0.35 + timeAngle * rotationalVelocity * 0.5;

    // Two octaves of value noise layered for spiral-arm structure.
    vec2 uvLow  = vec2(streakedAngle * BAND_FREQ_LOW,  radialT * 2.5);
    vec2 uvHigh = vec2(streakedAngle * BAND_FREQ_HIGH + 2.3, radialT * 5.0);
    float nLow  = valueNoise(uvLow);
    float nHigh = valueNoise(uvHigh);
    float structure = mix(nLow, nHigh, 0.5);
    // Remap into [1 - STRUCTURE_CONTRAST, 1] so filaments darken but
    // never black out the disc. Keeps the overall ring always visible.
    structure = mix(1.0 - STRUCTURE_CONTRAST, 1.0, structure);

    // ── Color ramp ───────────────────────────────────────────────────
    // Whiten toward the inner edge (hot material) and fade to OUTER_COLOR
    // at the trailing edge.
    float whiteness = (1.0 - smoothstep(0.0, 0.6, radialT)) * WHITEN_STRENGTH;
    vec3 color = mix(OUTER_COLOR, INNER_COLOR, whiteness);

    // ── Output ───────────────────────────────────────────────────────
    // LIGHTNING blend (SRC_ALPHA, ONE): additive contribution is
    // color * DISK_INTENSITY * (brightness * structure) added to the
    // scene.
    float strength = brightness * structure;
    fragColor = vec4(color * DISK_INTENSITY, strength);
}
