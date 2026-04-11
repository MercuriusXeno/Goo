#version 330

in vec3 viewNormal;
in float progress;
in float animationTime;

out vec4 fragColor;

// Tunables - iterate live via F3+T.
const float RIM_START  = 0.45;  // fresnel value where the rim begins to appear
const float RIM_END    = 1.00;  // fresnel value at the silhouette
const float SWIRL_FREQ = 6.0;
const float SWIRL_ROTATIONS_PER_CYCLE = 4.0;
const float TWO_PI = 6.28318530718;

const vec3 CORE_COLOR = vec3(0.01, 0.00, 0.03);  // near-black purple
const vec3 RIM_COLOR  = vec3(0.95, 0.35, 1.00);  // hot accretion-disc purple

void main() {
    // Fresnel factor: 0 at center of the sphere (facing camera), 1 at silhouette.
    float fresnel = 1.0 - abs(viewNormal.z);

    // Polar angle around the sphere's view-space silhouette for the swirl.
    float angle = atan(viewNormal.y, viewNormal.x);

    // animationTime cycles 0..1 every N ticks; multiply by TWO_PI for phase.
    float swirlPhase = animationTime * TWO_PI * SWIRL_ROTATIONS_PER_CYCLE;
    float swirl = sin(angle * SWIRL_FREQ + swirlPhase) * 0.5 + 0.5;

    // Core-to-rim color transition: below RIM_START, pure core. Above, blend
    // in rim color weighted by both the rim intensity and the swirl pattern.
    float rimStrength = smoothstep(RIM_START, RIM_END, fresnel);
    vec3 col = mix(CORE_COLOR, RIM_COLOR, rimStrength * swirl);

    // Fully opaque everywhere inside the sphere surface. The 3D sphere mesh
    // writes depth so it occludes correctly without needing an alpha ramp.
    fragColor = vec4(col, 1.0);
}
