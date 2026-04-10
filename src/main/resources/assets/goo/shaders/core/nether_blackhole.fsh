#version 330

#moj_import <minecraft:globals.glsl>

in vec2 quadUv;
in float progress;

out vec4 fragColor;

// Tunables - iterate live via F3+T.
const float SWIRL_FREQ  = 6.0;    // angular frequency of the swirl bands
const float SWIRL_SPEED = 240.0;  // multiplier on GameTime for rotation
const float BAND_PHASE  = 18.0;   // radial phase shift per unit radius
const float RIM_POWER   = 3.0;    // exponent for the rim glow falloff
const float CORE_EDGE   = 0.7;    // radius where the dark core starts to fade out
const float ALPHA_BASE  = 0.35;   // alpha floor at progress 0
const float ALPHA_SPAN  = 0.65;   // additional alpha added by progress 1

const vec3 NETHER_CORE = vec3(0.35, 0.02, 0.45);
const vec3 NETHER_GLOW = vec3(0.85, 0.25, 0.95);

void main() {
    // Center the UV at 0 and scale so r == 1 at the quad edge.
    vec2 p = quadUv - vec2(0.5);
    float r = length(p) * 2.0;
    if (r > 1.0) { discard; }

    // Swirling color bands driven by GameTime. atan gives polar angle;
    // the BAND_PHASE * r term makes the bands spiral outward.
    float angle = atan(p.y, p.x);
    float swirl = sin(angle * SWIRL_FREQ + GameTime * SWIRL_SPEED + r * BAND_PHASE) * 0.5 + 0.5;

    // Rim = bright edge, core = dark well at the center.
    float rim  = pow(1.0 - r, RIM_POWER);
    float core = smoothstep(CORE_EDGE, 0.0, r);

    vec3 glow = NETHER_GLOW * swirl;
    vec3 col  = mix(glow, NETHER_CORE, core);

    // Alpha rises with both the radial features and the implosion progress:
    // early ticks are faint, final ticks are near-opaque.
    float alpha = (rim + core) * (ALPHA_BASE + ALPHA_SPAN * progress);

    fragColor = vec4(col, alpha);
}
