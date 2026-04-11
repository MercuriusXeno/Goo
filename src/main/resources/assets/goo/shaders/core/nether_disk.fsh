#version 330

in float innerness;

out vec4 fragColor;

// Pure white emissive disk. Matches the corona's palette so the whole
// black-hole effect reads as one bright body.
const vec3 DISK_WHITE = vec3(1.0, 1.0, 1.0);

// Overall additive brightness multiplier. Bump for harder blow-out.
const float DISK_INTENSITY = 3.0;

// Falloff curve from the inner (brightest) edge outward through the
// tube cross-section. 1.0 is linear; higher values concentrate the
// brightness at the inner edge and give a gentler fade-out across the
// Y extrusion. Tune to match the corona's blend feel.
const float FALLOFF_POWER = 2.0;

void main() {
    // innerness = (1 - cos(phi)) / 2, computed per-vertex from the tube's
    // minor angle phi and interpolated across each quad:
    //   phi = 0   (outer edge): innerness = 0 → strength 0 → invisible tail
    //   phi = π/2 (top of tube): innerness = 0.5 → medium → Y feathering
    //   phi = π   (inner edge):  innerness = 1 → strength 1 → brightest
    //   phi = 3π/2 (bottom):     innerness = 0.5 → medium → Y feathering
    // Raised to FALLOFF_POWER so the inner edge dominates and the outer
    // edge tapers into invisibility smoothly.
    float strength = pow(innerness, FALLOFF_POWER);

    // LIGHTNING blend (SRC_ALPHA, ONE): additive contribution is
    // DISK_WHITE * DISK_INTENSITY * strength added to the scene.
    fragColor = vec4(DISK_WHITE * DISK_INTENSITY, strength);
}
