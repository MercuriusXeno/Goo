#version 330

in vec3 viewNormal;
in float progress;
in float animationTime;

out vec4 fragColor;

// Tunables - iterate live via F3+T.
const float RIM_FADE_START = 0.00;  // fresnel value where the swirl begins to appear
const float RIM_FADE_END   = 0.55;  // fresnel value at which the swirl reaches full intensity
const float SWIRL_FREQ = 10.0;
const float SWIRL_ROTATIONS_PER_CYCLE = 4.0;
// How tight the spiral curls. This is the phase offset added across the
// full fresnel range (0 at center, 1 at silhouette). Values >= PI produce
// visibly curved bands; larger values wind the spiral more aggressively.
const float TWIST_STRENGTH = 8.0;
const float TWO_PI = 6.28318530718;

const vec3 CORE_COLOR = vec3(0.01, 0.00, 0.03);  // near-black purple
const vec3 RIM_COLOR  = vec3(0.35, 0.08, 0.55);  // darker violet, subdued

void main() {
    // Fresnel factor: 0 at center of the sphere (facing camera), 1 at
    // silhouette. Also the radial coordinate of the vortex (distance
    // from the view axis through the sphere center).
    float fresnel = 1.0 - abs(viewNormal.z);

    // Roll angle around the view axis. Together with fresnel this defines
    // polar coordinates on the disc the camera is looking at, so the
    // vortex's center is always the point of the sphere facing the camera
    // regardless of where the camera is relative to the black hole.
    float angle = atan(viewNormal.y, viewNormal.x);

    // animationTime cycles 0..1 every N ticks; multiply by TWO_PI for phase.
    float swirlPhase = animationTime * TWO_PI * SWIRL_ROTATIONS_PER_CYCLE;

    // Radial twist: adding fresnel * TWIST_STRENGTH inside the sin curls
    // the angular bands into a spiral. Without this term the pattern is
    // just rotating angular wedges; with it, each band follows a curve
    // from silhouette inward, reading as a twirling spiral. The swirlPhase
    // is subtracted so time advances the pattern counterclockwise rather
    // than clockwise - flip the sign if you want the other direction.
    float twistedPhase = angle * SWIRL_FREQ + fresnel * TWIST_STRENGTH - swirlPhase;
    float swirl = sin(twistedPhase) * 0.5 + 0.5;

    // Rim mask: ramps the swirl intensity from 0 at the center of the
    // visible face up to full by the time fresnel hits RIM_FADE_END. The
    // outer ~half of the sphere face renders the spiral at full strength.
    float rimStrength = smoothstep(RIM_FADE_START, RIM_FADE_END, fresnel);
    vec3 col = mix(CORE_COLOR, RIM_COLOR, rimStrength * swirl);

    // Fully opaque everywhere inside the sphere surface. The 3D sphere mesh
    // writes depth so it occludes correctly without needing an alpha ramp.
    fragColor = vec4(col, 1.0);
}
