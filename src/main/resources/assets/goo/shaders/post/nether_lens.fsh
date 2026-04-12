#version 330

// Nether black-hole gravitational-lensing post-process.
//
// Unified round/hex formulation. Every code path expresses the hole
// boundary as a signed distance "r" to the event horizon (negative
// inside, positive outside), the hole's reference scale as
// "eventRadius" (in UV), and the photon ring offset from the horizon
// as "photonOffset" (also UV). The warp magnitude formula
//
//     warpMag = (eventRadius^2) / ((r + eventRadius)^2) * lensStrength
//
// is mathematically equivalent to the previous round-only formulation
// {@code eventRadius^2 / distFromCenter^2 * strength} (substitute
// {@code distFromCenter = r + eventRadius}), so the sphere style
// cannot regress as long as it keeps passing the round uniforms it
// always did.
//
// Shape switch lives in {@code LensTuning.w}: 0 = round (classic
// Euclidean distance from a point center), 1 = hex (signed distance
// to a convex polygon hull uploaded in {@code HoleHull0/1/2}). The
// hex path uses the screen-projected convex hull of the cube's eight
// corners, so it tracks the actual cube silhouette from any angle
// (rect when face-on, hexagon from a corner, etc.).

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    // (holeUvX, holeUvY, eventRadiusUv, photonOffsetUv)
    // Hole center in UV, characteristic radius in UV, and the photon
    // ring's signed-distance offset from the horizon (photon ring
    // appears at {@code r == photonOffsetUv}). A negative X value is
    // the "no active hole" sentinel; the shader short-circuits to a
    // pass-through blit when it sees this.
    vec4 HoleParams;
    // (lensStrength, aspectRatio, ringBrightness, shapeMode)
    // shapeMode: 0 = round (HoleHull0/1/2 ignored), 1 = hex (polygon
    // hull drives the event horizon and warp direction instead of a
    // point center).
    vec4 LensTuning;
    // Three vec4s holding six UV-space hex vertices (2 per vec4) in
    // CCW order. Unused in round mode. Degenerate hulls with fewer
    // than 6 unique vertices pad the last vertex into the tail slots;
    // the zero-length edges that creates are skipped in the SDF loop.
    vec4 HoleHull0;
    vec4 HoleHull1;
    vec4 HoleHull2;
};

out vec4 fragColor;

// Edge softness for the event-horizon occluder, in UV units.
const float HORIZON_AA = 0.002;

// Sharpness of the photon-ring highlight. Higher = thinner ring.
const float RING_SHARPNESS = 180.0;

// Shape-mode discriminator threshold for the LensTuning.w branch.
const float SHAPE_MODE_HEX_THRESHOLD = 0.5;

// Epsilon for edge-length degeneracy test in the hex SDF loop. Edges
// shorter than this are treated as degenerate padding (from
// repeated-vertex hull padding) and skipped.
const float HEX_DEGENERATE_EDGE = 1e-5;

// ── Convex-polygon signed distance ────────────────────────────────────

// Returns the signed distance from point {@code p} to the convex hex
// hull {@code v[0..5]} using the "max over outward half-plane
// distances" approach. For a CCW-ordered convex polygon this is exact
// along each edge interior and slightly underestimates near vertices
// (corner regions), which is visually fine for a lens warp.
//
// Outputs the result as a {@code vec3} where {@code .x} is the signed
// distance (negative inside, positive outside) and {@code .yz} is the
// outward normal of the closest edge — used as the (negated) warp
// direction so samples get pulled perpendicular to the nearest cube
// silhouette edge.
vec3 hexSignedDistance(vec2 p,
        vec2 v0, vec2 v1, vec2 v2, vec2 v3, vec2 v4, vec2 v5) {
    vec2 verts[6];
    verts[0] = v0;
    verts[1] = v1;
    verts[2] = v2;
    verts[3] = v3;
    verts[4] = v4;
    verts[5] = v5;

    float bestSdf = -1e9;
    vec2 bestNormal = vec2(0.0, 1.0);
    for (int i = 0; i < 6; i++) {
        int j = (i + 1) == 6 ? 0 : (i + 1);
        vec2 a = verts[i];
        vec2 b = verts[j];
        vec2 edge = b - a;
        float edgeLen = length(edge);
        if (edgeLen < HEX_DEGENERATE_EDGE) { continue; }
        // CCW winding: outward normal of edge (a→b) is
        // (edge.y, -edge.x) / |edge|.
        vec2 n = vec2(edge.y, -edge.x) / edgeLen;
        float d = dot(p - a, n);
        if (d > bestSdf) {
            bestSdf = d;
            bestNormal = n;
        }
    }
    return vec3(bestSdf, bestNormal);
}

void main() {
    vec2 holeUv = HoleParams.xy;
    float eventRadius = HoleParams.z;
    float photonOffset = HoleParams.w;
    float lensStrength = LensTuning.x;
    float aspect = LensTuning.y;
    float ringBoost = LensTuning.z;
    float shapeMode = LensTuning.w;

    // Sentinel: hole X < 0 means "no active hole". Pass through.
    if (holeUv.x < 0.0 || eventRadius <= 0.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    bool isHex = shapeMode > SHAPE_MODE_HEX_THRESHOLD;

    // "r" is the signed distance to the event horizon in the two
    // modes: r = length(d) - eventRadius for round, r = polygon SDF
    // for hex. "warpDir" is the unit vector pointing from the
    // fragment toward the horizon (ie the direction samples get
    // pulled when we lens). Both normalize identically — negative r
    // means inside, positive r means outside.
    float r;
    vec2 warpDir;
    if (isHex) {
        vec3 sdfRes = hexSignedDistance(texCoord,
                HoleHull0.xy, HoleHull0.zw,
                HoleHull1.xy, HoleHull1.zw,
                HoleHull2.xy, HoleHull2.zw);
        r = sdfRes.x;
        warpDir = -sdfRes.yz;
    } else {
        vec2 d = texCoord - holeUv;
        vec2 dAspect = vec2(d.x * aspect, d.y);
        float dist = length(dAspect);
        r = dist - eventRadius;
        vec2 unitDirAspect = -dAspect / max(dist, 1e-5);
        // Undo the aspect stretch so the warp lands on the right
        // texel in UV space.
        warpDir = vec2(unitDirAspect.x / aspect, unitDirAspect.y);
    }

    // Inside the event horizon: pure black with a soft AA edge. In
    // unified form, "inside" is r < 0.
    if (r < -HORIZON_AA) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    if (r < HORIZON_AA) {
        float t = smoothstep(-HORIZON_AA, HORIZON_AA, r);
        vec4 edge = texture(InSampler, texCoord);
        fragColor = mix(vec4(0.0, 0.0, 0.0, 1.0), edge, t);
        return;
    }

    // Gravitational warp outside the horizon. The unified falloff
    // is equivalent to the old round formula: substituting
    // distFromCenter = r + eventRadius into eventRadius^2 /
    // distFromCenter^2 * strength recovers the old result exactly,
    // so the sphere style produces pixel-identical output to its
    // previous implementation.
    float denom = r + eventRadius;
    float warpMag = (eventRadius * eventRadius) / max(denom * denom, 1e-8) * lensStrength;
    vec2 warpedUv = clamp(texCoord + warpDir * warpMag, vec2(0.0), vec2(1.0));
    vec4 warped = texture(InSampler, warpedUv);

    // Photon ring: gaussian at r == photonOffset. For round mode
    // photonOffset is photonRadiusUv - eventRadiusUv (the old
    // ring-minus-horizon offset). For hex mode it's the same scalar
    // distance past the polygon boundary.
    float ringDelta = r - photonOffset;
    float ring = exp(-RING_SHARPNESS * ringDelta * ringDelta) * ringBoost;
    vec3 ringColor = vec3(1.0, 0.85, 0.55);

    fragColor = vec4(warped.rgb + ringColor * ring, 1.0);
}
