#version 330

#moj_import <minecraft:globals.glsl>

in vec2 quadUv;
in float progress;

out vec4 fragColor;

// Tunables - iterate live via F3+T.
// Size zones within the normalized quad (0 at center, 1 at edge).
const float CORE_RADIUS    = 0.85;  // solid opaque core extends almost to the quad edge
const float RIM_INNER      = 0.85;  // inside of the swirl band
const float RIM_OUTER      = 1.00;  // outside of the swirl band
const float EDGE_FEATHER   = 0.03;  // anti-aliased silhouette feather
// GameTime in this shader is (gameTicks % 24000 + partialTick) / 24000, a 0..1 fraction of a day.
// For one full swirl revolution in ~3 seconds (60 ticks), GameTime delta is 60/24000 = 0.0025.
// SWIRL_SPEED * 0.0025 should equal ~2*PI, so SWIRL_SPEED ~ 2500.
const float SWIRL_FREQ     = 7.0;
const float SWIRL_SPEED    = 2500.0;
const float BAND_PHASE     = 16.0;
// Alpha modulation of the rim so the swirl bands read as translucent wisps.
const float RIM_ALPHA_FLOOR = 0.15;
const float RIM_ALPHA_SPAN  = 0.85;

const vec3 NETHER_VOID = vec3(0.01, 0.00, 0.03);  // near-black purple void
const vec3 NETHER_RIM  = vec3(0.85, 0.25, 0.95);  // hot accretion-disk purple

void main() {
    vec2 p = quadUv - vec2(0.5);
    float r = length(p) * 2.0;
    if (r > 1.0) { discard; }
    float silhouette = smoothstep(1.0, 1.0 - EDGE_FEATHER, r);

    // Swirl bands rotate with GameTime and spiral outward with r.
    float angle = atan(p.y, p.x);
    float swirl = sin(angle * SWIRL_FREQ + GameTime * SWIRL_SPEED + r * BAND_PHASE) * 0.5 + 0.5;

    // Three zones:
    //   r < CORE_RADIUS                 -> solid opaque void core
    //   RIM_INNER < r < RIM_OUTER        -> rim with swirling alpha
    //   r > 1.0                         -> discarded (outer clip)
    float coreMask = 1.0 - smoothstep(CORE_RADIUS - EDGE_FEATHER, CORE_RADIUS, r);
    float rimBand  = smoothstep(RIM_INNER, (RIM_INNER + RIM_OUTER) * 0.5, r)
                   * (1.0 - smoothstep((RIM_INNER + RIM_OUTER) * 0.5, RIM_OUTER, r));

    vec3 coreCol = NETHER_VOID;
    vec3 rimCol  = NETHER_RIM;
    vec3 col     = coreCol * coreMask + rimCol * rimBand;

    // Core is always fully opaque, rim alpha varies with swirl so the bands
    // read as translucent wisps rather than a flat painted ring.
    float rimAlpha = rimBand * (RIM_ALPHA_FLOOR + RIM_ALPHA_SPAN * swirl);
    float alpha = max(coreMask, rimAlpha) * silhouette * progress;
    fragColor = vec4(col, alpha);
}
