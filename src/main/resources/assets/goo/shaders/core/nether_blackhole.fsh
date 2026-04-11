#version 330

#moj_import <minecraft:globals.glsl>

in vec2 quadUv;
in float progress;

out vec4 fragColor;

// Tunables - iterate live via F3+T.
const float CORE_RADIUS    = 0.55;  // radius of the fully-opaque dark core (in [0, 1] quad-normalized)
const float RIM_RADIUS     = 0.95;  // outer edge of the accretion-disk rim
const float EDGE_FEATHER   = 0.04;  // softness of the outer discard so the silhouette isn't aliased
const float SWIRL_FREQ     = 8.0;   // angular frequency of the swirl bands
const float SWIRL_SPEED    = 80.0;  // GameTime multiplier for rotation
const float BAND_PHASE     = 14.0;  // radial phase shift (spiral pitch)
const float RIM_GAIN       = 1.6;   // brightness multiplier on the rim
const float CORE_DARKNESS  = 0.02;  // multiplicative darkening of the core (near-black)
const float PROGRESS_BOOST = 0.5;   // how much the progress adds to the rim brightness

const vec3 NETHER_VOID = vec3(0.02, 0.00, 0.04);  // near-black purple void inside the core
const vec3 NETHER_RIM  = vec3(0.85, 0.25, 0.95);  // hot accretion-disk purple

void main() {
    // Map UV [0,1] to centered radius in [0, sqrt(2)].
    vec2 p = quadUv - vec2(0.5);
    float r = length(p) * 2.0;

    // Anti-aliased outer discard: fade out over EDGE_FEATHER, hard clip beyond 1.
    if (r > 1.0) { discard; }
    float silhouette = smoothstep(1.0, 1.0 - EDGE_FEATHER, r);

    // Swirling accretion disk band, driven by GameTime.
    float angle = atan(p.y, p.x);
    float swirl = sin(angle * SWIRL_FREQ + GameTime * SWIRL_SPEED + r * BAND_PHASE) * 0.5 + 0.5;

    // Three zones:
    //   r < CORE_RADIUS            -> solid opaque void (blocks view of destruction)
    //   CORE_RADIUS < r < RIM_RADIUS -> accretion-disk rim with swirling purple
    //   r > RIM_RADIUS             -> soft fade to discard edge
    float coreMask = 1.0 - smoothstep(CORE_RADIUS - EDGE_FEATHER, CORE_RADIUS, r);
    float rimMask  = smoothstep(CORE_RADIUS, (CORE_RADIUS + RIM_RADIUS) * 0.5, r)
                   * (1.0 - smoothstep(RIM_RADIUS, 1.0, r));

    // Core is solid opaque near-black; rim is bright swirling purple.
    vec3 coreCol = NETHER_VOID * CORE_DARKNESS;
    vec3 rimCol  = NETHER_RIM * (RIM_GAIN * swirl) * (1.0 + PROGRESS_BOOST * progress);
    vec3 col     = coreCol * coreMask + rimCol * rimMask;

    // Alpha is full inside the core and on the rim; the outer feather fades.
    float alpha = (coreMask + rimMask) * silhouette;
    alpha = clamp(alpha, 0.0, 1.0);

    fragColor = vec4(col, alpha);
}
