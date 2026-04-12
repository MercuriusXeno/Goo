#version 330

in vec3 viewPos;
in vec3 centerView;
in float mainRadiusSq;

out vec4 fragColor;

// Must match the vertex shader's CORONA_SCALE (1.08). Squared form is
// used for the outer-edge normalization below. 1.08^2 = 1.1664.
// another option is 1.15^2 = 1.3225
const float CORONA_SCALE_SQ = 1.1664;

// Pure white halo. Kept as a constant so live tuning is one line.
const vec3 CORONA_WHITE = vec3(1.0, 1.0, 1.0);

// Additive-blend brightness multiplier. Bump for harder blow-out.
// Dropped from 3.0 after the sphere pop-in curve was sped up; a full-
// size corona sitting on-screen for the entire HOLD phase reads much
// brighter than one that was growing over EXPAND, so the absolute
// brightness needed to come down to compensate.
const float CORONA_INTENSITY = 1.5;

// Falloff curve across the annular ring. 1.0 is linear, lower values
// bias more of the ring toward full brightness, higher values tighten
// the ring against the inner (main-silhouette) edge.
const float FALLOFF_POWER = 2.0;

void main() {
    // Ray from the camera (view-space origin) to this fragment.
    vec3 rayDir = normalize(viewPos);

    // Squared perpendicular distance from the sphere center to the ray:
    // classic |C|^2 - (D . C)^2 formula, where C = centerView (camera
    // is at the view-space origin so "camera to center" == centerView)
    // and D = rayDir (unit). This tells us how close the ray gets to
    // the sphere center on its way through space.
    float b = dot(rayDir, centerView);
    float perpSq = dot(centerView, centerView) - b * b;

    // If the ray passes through the main sphere (perpSq < mainRadiusSq),
    // this fragment is visually "inside" the main sphere's silhouette
    // on screen. Showing the corona here would bleed it over the black
    // hole's body. Discard so only the annular ring remains.
    if (perpSq < mainRadiusSq) {
        discard;
    }

    // Normalize across the ring: t=0 at the inner edge (perpSq equal
    // to the main radius, i.e. the ray is tangent to the main sphere),
    // t=1 at the outer edge (perpSq equal to the corona radius, where
    // the ray is tangent to the corona sphere's silhouette). The span
    // of the annulus in perpSq space is mainRadiusSq * (CORONA_SCALE_SQ - 1).
    float t = (perpSq - mainRadiusSq) / (mainRadiusSq * (CORONA_SCALE_SQ - 1.0));
    t = clamp(t, 0.0, 1.0);

    // Brightness: strongest at the inner edge, fading outward.
    float strength = pow(1.0 - t, FALLOFF_POWER);

    // LIGHTNING blend (SRC_ALPHA, ONE): final contribution is
    // CORONA_WHITE * CORONA_INTENSITY * strength added to the scene.
    fragColor = vec4(CORONA_WHITE * CORONA_INTENSITY, strength);
}
