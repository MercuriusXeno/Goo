#version 330

in vec3 worldPos;
in vec4 vertColor;
in vec3 unitNormal;

out vec4 fragColor;

// Crack pattern tuning
const float CELL_SIZE = 1.4;
const float CRACK_HALF_WIDTH = 0.04;
const float CRACK_ALPHA_BASE = 0.5;
const float CRACK_GLOW = 0.2;
const vec3 CRACK_TINT = vec3(0.8, 0.9, 1.0);
const float DRIFT_SPEED = 0.4;

// 3D hash - returns pseudo-random vec3 in [0,1) for a given integer cell
vec3 hash3(vec3 p) {
    vec3 q = vec3(
        dot(p, vec3(127.1, 311.7, 74.7)),
        dot(p, vec3(269.5, 183.3, 246.1)),
        dot(p, vec3(113.5, 271.9, 124.6))
    );
    return fract(sin(q) * 43758.5453123);
}

// Returns the distance to the nearest Voronoi cell edge in 3D.
// Two-pass: find nearest center, then find nearest edge (perpendicular
// bisector between nearest and each neighbor's center).
float voronoiEdgeDist(vec3 p) {
    vec3 ip = floor(p);
    vec3 fp = fract(p);

    // Pass 1: find the nearest cell center
    vec3 nearestOffset = vec3(0.0);
    float nearestDist = 8.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec3 neighbor = vec3(float(x), float(y), float(z));
                vec3 cellCenter = neighbor + hash3(ip + neighbor);
                float d = length(cellCenter - fp);
                if (d < nearestDist) {
                    nearestDist = d;
                    nearestOffset = neighbor;
                }
            }
        }
    }

    // Pass 2: find the nearest edge (bisector between nearest and neighbors)
    float edgeDist = 8.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec3 neighbor = vec3(float(x), float(y), float(z));
                if (neighbor == nearestOffset) continue;
                vec3 cellCenter = neighbor + hash3(ip + neighbor);
                vec3 nearestCenter = nearestOffset + hash3(ip + nearestOffset);
                // Distance from p to the perpendicular bisector of the two centers
                vec3 midpoint = 0.5 * (nearestCenter + cellCenter);
                vec3 bisectorNormal = normalize(cellCenter - nearestCenter);
                float d = abs(dot(fp - midpoint, bisectorNormal));
                edgeDist = min(edgeDist, d);
            }
        }
    }
    return edgeDist;
}

void main() {
    float density = vertColor.r;
    float animTime = vertColor.g;

    // Scale cell size - more cracks at higher density
    float scale = CELL_SIZE / max(0.4, density * 1.2);
    vec3 seed = worldPos / scale + vec3(animTime * DRIFT_SPEED, 0.0, 0.0);

    float edgeDist = voronoiEdgeDist(seed);

    // Crack width scales with density
    float crackWidth = CRACK_HALF_WIDTH * (0.5 + density * 0.8);
    float crack = 1.0 - smoothstep(0.0, crackWidth, edgeDist);

    if (crack < 0.01) discard;

    // Icy blue-white tint with subtle variation from position
    vec3 tint = CRACK_TINT + 0.08 * sin(worldPos * 3.0);
    float alpha = crack * CRACK_ALPHA_BASE * max(density, 0.15);
    vec3 color = tint + CRACK_GLOW * crack;

    fragColor = vec4(color, alpha);
}
