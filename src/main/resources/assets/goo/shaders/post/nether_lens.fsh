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

// Edge softness for the event-horizon occluder, in UV units. Wide
// enough (~6 pixels at 1080p vertical) to hide any sub-pixel
// disagreement between the hex SDF's hull-derived horizon and the
// actual cube silhouette rendered into the main framebuffer (the
// two projections can drift under active FOV modifiers like sprint
// bob or bow zoom).
const float HORIZON_AA = 0.006;

// Sharpness of the photon-ring highlight. Higher = thinner ring.
const float RING_SHARPNESS = 180.0;

// Shape-mode discriminator threshold for the LensTuning.w branch.
const float SHAPE_MODE_HEX_THRESHOLD = 0.5;

// Squared epsilon for edge-length degeneracy test in the hex SDF loop.
// Edges with squared length below this are treated as zero-length
// padding (from repeated-vertex hull padding) and skipped.
const float HEX_DEGENERATE_EDGE_SQ = 1e-10;

// Epsilon for normalizing the (p - closest) vector when the fragment
// sits exactly on the polygon boundary.
const float HEX_NORMAL_EPS = 1e-5;

// ── Convex-polygon signed distance ────────────────────────────────────

// Returns the true signed distance from point {@code p} to the convex
// hex hull {@code v[0..5]}. For each edge the function computes the
// point-to-segment distance (which correctly handles both the
// edge-interior and the vertex-closest cases), takes the global
// minimum, and signs it via a CCW inside test (cross-product sign
// against every edge). This is the version that works correctly at
// cube corners — the previous half-plane approximation produced
// kinked iso-contours where two edges' outward normals disagreed, and
// the photon ring and warp direction both "zig-zagged" in those
// zones.
//
// Outputs the result as a {@code vec3} where {@code .x} is the signed
// distance (negative inside, positive outside) and {@code .yz} is the
// unit outward direction from the closest point on the polygon to
// {@code p} — used as the (negated) warp direction so samples get
// pulled toward the nearest point on the cube silhouette, whether
// that point lies on an edge interior or at a vertex.
vec3 hexSignedDistance(vec2 p,
        vec2 v0, vec2 v1, vec2 v2, vec2 v3, vec2 v4, vec2 v5) {
    vec2 verts[6];
    verts[0] = v0;
    verts[1] = v1;
    verts[2] = v2;
    verts[3] = v3;
    verts[4] = v4;
    verts[5] = v5;

    float bestDistSq = 1e20;
    vec2 bestClosest = p;
    bool inside = true;
    for (int i = 0; i < 6; i++) {
        int j = (i + 1) == 6 ? 0 : (i + 1);
        vec2 a = verts[i];
        vec2 b = verts[j];
        vec2 edge = b - a;
        float edgeLenSq = dot(edge, edge);
        // Skip degenerate padding edges (repeated vertices from the
        // convex-hull padding in NetherLensEffect). Zero-length edges
        // would produce NaN in the projection below and contribute
        // nothing useful to either the distance or the inside test.
        if (edgeLenSq < HEX_DEGENERATE_EDGE_SQ) { continue; }

        vec2 diff = p - a;
        // Project p onto the edge segment, clamped to [0, 1] so the
        // closest point is inside the segment (edge-interior case) or
        // at an endpoint (vertex case).
        float t = clamp(dot(diff, edge) / edgeLenSq, 0.0, 1.0);
        vec2 closest = a + t * edge;
        vec2 offset = p - closest;
        float distSq = dot(offset, offset);
        if (distSq < bestDistSq) {
            bestDistSq = distSq;
            bestClosest = closest;
        }

        // Inside test: for a CCW-oriented convex polygon, p is inside
        // iff cross(edge, diff) >= 0 for every edge. As soon as any
        // edge says "right side" (negative cross) we know p is
        // outside and stop updating the flag.
        float crossEdge = edge.x * diff.y - edge.y * diff.x;
        if (crossEdge < 0.0) {
            inside = false;
        }
    }

    float dist = sqrt(bestDistSq);
    vec2 toP = p - bestClosest;
    float toPLen = length(toP);
    // Degenerate case: p sits exactly on the polygon boundary. Any
    // unit vector works for the warp direction since dist == 0 means
    // the fragment is inside the horizon AA band anyway.
    vec2 outward = toPLen > HEX_NORMAL_EPS ? (toP / toPLen) : vec2(0.0, 1.0);
    return vec3(inside ? -dist : dist, outward);
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
    // for hex. Both are computed in aspect-corrected UV space (X
    // multiplied by aspect) so distances correspond to equal pixel
    // offsets on both axes regardless of viewport aspect. "warpDir"
    // is converted back to raw UV space before being added to
    // texCoord: the X component gets divided by aspect to undo the
    // stretch. Both normalize identically — negative r means inside,
    // positive r means outside.
    float r;
    vec2 warpDir;
    if (isHex) {
        // Hull vertices were pre-multiplied by aspect on the Java
        // side (see NetherLensEffect.projectToCornerSlot), so we run
        // the SDF on texCoord's aspect-corrected counterpart to stay
        // in the same space as the hull.
        vec2 pAspect = vec2(texCoord.x * aspect, texCoord.y);
        vec3 sdfRes = hexSignedDistance(pAspect,
                HoleHull0.xy, HoleHull0.zw,
                HoleHull1.xy, HoleHull1.zw,
                HoleHull2.xy, HoleHull2.zw);
        r = sdfRes.x;
        // Outward direction from the SDF is in aspect space; undo
        // the aspect stretch on X so the warp lands on the right
        // texel in UV space.
        warpDir = -vec2(sdfRes.y / aspect, sdfRes.z);
    } else {
        vec2 d = texCoord - holeUv;
        vec2 dAspect = vec2(d.x * aspect, d.y);
        float dist = length(dAspect);
        r = dist - eventRadius;
        vec2 unitDirAspect = -dAspect / max(dist, 1e-5);
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
    // Clamp the warp so the sample never crosses the event horizon.
    // Without this, fragments within ~warpMag of the horizon pull
    // their samples INTO the cube/sphere silhouette (pure black in
    // the framebuffer, from the occluder pass), which blended with
    // the photon ring's additive glow produces a dim-orange-on-black
    // band hugging the silhouette — the "black mixing in the photon
    // edge" artifact. Clamping keeps the sample just outside the
    // horizon so the lensed color is always real scene rather than
    // occluder black.
    warpMag = min(warpMag, max(r - HORIZON_AA, 0.0));
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
