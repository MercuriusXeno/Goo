package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mercuriusxeno.goo.effect.NetherBehavior;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.level.Level;

/**
 * Cube-shaped nether black-hole experiment. Mirrors the three-pass
 * structure of {@link com.mercuriusxeno.goo.client.ber.NetherBlackHoleRender}
 * but with cube geometry for the occluder and a cube-edge-glow shader
 * in place of the fresnel corona:
 * <ol>
 *   <li>Solid cube occluder reusing {@link GooRenderTypes#NETHER_BLACKHOLE_TYPE}
 *       — the sphere occluder shader reads only {@code Position}, so
 *       feeding it cube vertices produces a black cube with correct
 *       depth write.</li>
 *   <li>Cube edge glow via {@link GooRenderTypes#NETHER_CUBE_EDGE_TYPE}
 *       — same cube mesh at {@link #EDGE_SCALE} the occluder size, with
 *       per-vertex intra-face UVs packed into {@code Color.rg} so the
 *       fragment shader can compute distance to the nearest face edge.</li>
 *   <li>Flat accretion disc via {@link GooRenderTypes#NETHER_DISK_TYPE}
 *       — same annulus geometry as the sphere style, duplicated here
 *       (mesh data + emit) so {@code NetherBlackHoleRender} itself does
 *       not need to expose any helpers on this experiment branch.</li>
 * </ol>
 *
 * <p>Cube "radius" is interpreted as a half-extent: an occluder of
 * "radius" R produces a cube spanning {@code [-R, +R]} on every axis,
 * so the cube's corner distance from the center is {@code R * sqrt(3)}
 * ≈ 1.73 * R (longer than the sphere silhouette of the same R). Lens
 * marking and disc placement still key off R as the canonical radius,
 * matching the sphere style so A/B comparisons are apples-to-apples.
 */
public final class CubeHoleStyle implements NetherHoleStyle {

    /** Offset to get block center from integer position. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Minimum visible half-extent for the cube so it never collapses
     * to a single pixel. */
    private static final float CUBE_MIN_HALF_EXTENT = 0.25f;
    /** World-space margin added to the effective implosion radius so
     * the cube fully covers the blast zone. */
    private static final float OCCLUSION_MARGIN = 0.75f;

    /** Scale of the edge-glow cube relative to the occluder. 1.04
     * pushes the glow cube just past the occluder surface so its
     * faces are visible against depth-test even with depth write off. */
    private static final float EDGE_SCALE = 1.04f;

    /** Disc inner edge as a multiple of the current cube half-extent.
     * 1.12 sits just past the corner circumradius (1.0 at a face,
     * 1.414 at an edge, 1.732 at a corner) so the disc clears the
     * cube face silhouette but hugs it at the corners. */
    private static final float DISK_INNER_CUBE_MULT = 1.12f;
    /** Disc outer edge at full expansion as a multiple of the full
     * pre-scaled blast radius. Same value as the sphere style so the
     * overall silhouette of the effect is consistent when flipping. */
    private static final float DISK_OUTER_FULL_MULT = 2.8f;
    /** Minimum outer edge overshoot past the inner edge, as a multiple
     * of the current cube half-extent. Prevents the ring from
     * collapsing to zero width at the very start of the effect. */
    private static final float DISK_MIN_RING_WIDTH = 0.25f;

    /** Angular segments around the accretion disc. Matches the sphere
     * style's tessellation for visual parity. */
    private static final int DISK_ANGULAR_SEGMENTS = 64;
    /** Floats per entry in {@link #DISK_ANGULAR_SAMPLES}: cos, sin,
     * angularT. */
    private static final int DISK_SAMPLE_STRIDE = 3;
    private static final int DISK_SAMPLE_COS_OFFSET = 0;
    private static final int DISK_SAMPLE_SIN_OFFSET = 1;
    private static final int DISK_SAMPLE_ANG_OFFSET = 2;
    /** Full circle in radians. */
    private static final double TWO_PI = 2.0 * Math.PI;

    /** Radial T packed into Color.r for disc inner-edge vertices. */
    private static final float RADIAL_T_INNER = 0f;
    /** Radial T packed into Color.r for disc outer-edge vertices. */
    private static final float RADIAL_T_OUTER = 1f;

    /** 0xFF opaque alpha for vertex color packing. */
    private static final int OPAQUE_ALPHA = 0xFF;
    /** Maximum byte value for a 0..1 to byte mapping. */
    private static final int PROGRESS_BYTE_MAX = 255;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_CHANNEL_SHIFT = 16;
    private static final int GREEN_CHANNEL_SHIFT = 8;
    private static final int BLUE_CHANNEL_SHIFT = 0;

    /** Animation cycle length in ticks. Matches sphere style. */
    private static final int ANIMATION_CYCLE_TICKS = 64;

    /** Number of faces on a cube. */
    private static final int CUBE_FACES = 6;
    /** Vertices per face when emitting as {@code VertexFormat.Mode.QUADS}. */
    private static final int CUBE_VERTICES_PER_FACE = 4;
    /** Floats per entry in {@link #CUBE_FACE_POSITIONS} and
     * {@link #CUBE_FACE_NORMALS} — an {@code (x, y, z)} triple. */
    private static final int CUBE_POS_STRIDE = 3;
    /** Floats per entry in {@link #CUBE_FACE_UVS} — a {@code (u, v)}
     * pair. */
    private static final int CUBE_UV_STRIDE = 2;
    /** Offset of the Z component inside a stride-3 position or normal
     * triple. Named so array accesses like {@code data[p + CUBE_Z]}
     * don't trip checkstyle's magic-number rule. */
    private static final int CUBE_Z = 2;
    /** Unit magnitude for cube half-extent literals. Used through its
     * negation as {@code -UNIT} in the cube vertex tables so the
     * tables contain no raw {@code -1f} literals (0f and 1f are fine;
     * -1f is flagged). */
    private static final float UNIT = 1f;

    // ── Static cube face definitions ────────────────────────────────

    /** Unit cube vertex positions for all six faces in QUADS order,
     * CCW winding when viewed from outside the cube. Each face spans
     * {@code [-1, +1]} on its two in-plane axes. Laid out as stride-3
     * triples so the emit helper can scale and translate without
     * unpacking a JOML object per vertex. 6 faces × 4 vertices × 3 =
     * 72 floats. */
    private static final float[] CUBE_FACE_POSITIONS = buildCubeFacePositions();

    /** Per-vertex intra-face UV coordinates matching
     * {@link #CUBE_FACE_POSITIONS}: each quad walks (0,0), (1,0),
     * (1,1), (0,1) so the edge shader sees a full 0..1 range across
     * every face. 6 faces × 4 vertices × 2 = 48 floats. */
    private static final float[] CUBE_FACE_UVS = buildCubeFaceUvs();

    /** Per-face outward normals, one per vertex (4 duplicates per face)
     * so the vertex consumer's {@code setNormal} has a sensible value
     * even though neither the occluder nor the edge shader consumes
     * them. 6 faces × 4 vertices × 3 = 72 floats. */
    private static final float[] CUBE_FACE_NORMALS = buildCubeFaceNormals();

    /** Total vertex count for one full cube emit. */
    private static final int CUBE_VERTEX_COUNT = CUBE_FACES * CUBE_VERTICES_PER_FACE;

    // ── Precomputed disc angular samples ────────────────────────────

    private static final float[] DISK_ANGULAR_SAMPLES = buildDiskAngularSamples();

    CubeHoleStyle() {}

    @Override
    public void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        if (be.getBehavior() instanceof NetherBehavior nether) {
            state.netherActive = true;
            state.visibleScale = nether.getVisibleScale();
            state.diskExpansionScale = nether.getDiskExpansionScale();
            state.implodeRadius = nether.getCurrentRadius();
            state.animationTime = computeAnimationTime(be);
            // Intentionally NOT calling NetherLensEffect.markHoleActive
            // here. The lens shader paints a round event-horizon disc
            // and a circular photon ring sized to the passed world
            // radius; both look like "a sphere floating inside the
            // cube" because the cube's corners extend to
            // sqrt(3) * halfExtent while the ring sits at 1.5 *
            // halfExtent. The lens also applies a radial UV warp that
            // ignores the cube silhouette entirely, curving its
            // straight edges. Letting the lens go stale (no mark this
            // frame) deactivates it via NetherLensEffect's frame
            // staleness check; sphere style keeps marking and keeps
            // the lens. A cube-aware lens (square horizon, no photon
            // ring) is a separate follow-up.
            return;
        }
        state.netherActive = false;
    }

    @Override
    public void submit(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        float fullRadius = state.implodeRadius + OCCLUSION_MARGIN;
        float visibleHalfExtent = Math.max(CUBE_MIN_HALF_EXTENT, fullRadius * state.visibleScale);
        float edgeHalfExtent = visibleHalfExtent * EDGE_SCALE;

        float diskInnerRadius = visibleHalfExtent * DISK_INNER_CUBE_MULT;
        float diskOuterRadiusRaw = fullRadius * DISK_OUTER_FULL_MULT * state.diskExpansionScale;
        float diskOuterRadius = Math.max(
                diskInnerRadius + visibleHalfExtent * DISK_MIN_RING_WIDTH,
                diskOuterRadiusRaw);

        final float occluderHalf = visibleHalfExtent;
        final float edgeHalf = edgeHalfExtent;
        final float innerR = diskInnerRadius;
        final float outerR = diskOuterRadius;
        final float animPhase = state.animationTime;

        // Pass 1: cube occluder. Reuses the sphere occluder pipeline —
        // its shader only reads Position so cube vertices produce a
        // black cube with correct depth write and nothing else.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_BLACKHOLE_TYPE,
            (pose, c) -> emitCubeMesh(pose, c, occluderHalf, false));
        // Pass 2: cube edge glow. Slightly enlarged cube, per-vertex
        // intra-face UVs packed into Color.rg; the fragment shader
        // brightens toward each face edge.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_CUBE_EDGE_TYPE,
            (pose, c) -> emitCubeMesh(pose, c, edgeHalf, true));
        // Pass 3: flat accretion disc — identical geometry to the
        // sphere style, duplicated here so NetherBlackHoleRender stays
        // untouched on this experiment branch.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_DISK_TYPE,
            (pose, c) -> emitDiskMesh(pose, c, innerR, outerR, animPhase));
    }

    // ── Cube mesh emit ───────────────────────────────────────────────

    /**
     * Emits the pre-generated unit cube mesh with each vertex scaled to
     * {@code halfExtent} and translated to the block center. When
     * {@code packUv} is true, Color.rg carries the intra-face UV for
     * the edge shader; otherwise Color is opaque white (the occluder
     * shader ignores it).
     *
     * @param pose       the current pose entry
     * @param c          the vertex consumer
     * @param halfExtent world-space half-extent in blocks (±halfExtent on each axis)
     * @param packUv     whether to pack the intra-face UV into Color.rg
     */
    private static void emitCubeMesh(PoseStack.Pose pose, VertexConsumer c,
            float halfExtent, boolean packUv) {
        for (int i = 0; i < CUBE_VERTEX_COUNT; i++) {
            int color = packUv ? vertexUvColor(i) : packCubeOccluderColor();
            emitCubeVertex(pose, c, i, halfExtent, color);
        }
    }

    /** Emits a single cube vertex: reads the position and normal from
     * the static tables at index {@code i}, scales the position to
     * {@code halfExtent}, offsets to the block center, and forwards
     * the pre-packed {@code color} to the vertex consumer.
     *
     * @param pose       the current pose entry
     * @param c          the vertex consumer
     * @param i          cube vertex index (0 .. CUBE_VERTEX_COUNT - 1)
     * @param halfExtent world-space half-extent on each axis in blocks
     * @param color      pre-packed ARGB vertex color
     */
    private static void emitCubeVertex(PoseStack.Pose pose, VertexConsumer c,
            int i, float halfExtent, int color) {
        int p = i * CUBE_POS_STRIDE;
        float px = CUBE_FACE_POSITIONS[p];
        float py = CUBE_FACE_POSITIONS[p + 1];
        float pz = CUBE_FACE_POSITIONS[p + CUBE_Z];
        float nx = CUBE_FACE_NORMALS[p];
        float ny = CUBE_FACE_NORMALS[p + 1];
        float nz = CUBE_FACE_NORMALS[p + CUBE_Z];
        c.addVertex(pose,
                BLOCK_CENTER + px * halfExtent,
                BLOCK_CENTER + py * halfExtent,
                BLOCK_CENTER + pz * halfExtent)
            .setColor(color)
            .setNormal(pose, nx, ny, nz);
    }

    /** Packs the edge-glow vertex color for cube vertex {@code i}
     * from the static UV table.
     *
     * @param i cube vertex index
     * @return the packed ARGB color with UV in R/G
     */
    private static int vertexUvColor(int i) {
        int u = i * CUBE_UV_STRIDE;
        int uByte = Math.round(CUBE_FACE_UVS[u] * PROGRESS_BYTE_MAX);
        int vByte = Math.round(CUBE_FACE_UVS[u + 1] * PROGRESS_BYTE_MAX);
        return packCubeUvColor(uByte, vByte);
    }

    /** Packs the edge-glow vertex color: R = intra-face U, G = intra-face V.
     *
     * @param uByte intra-face U already encoded to a byte
     * @param vByte intra-face V already encoded to a byte
     * @return the packed ARGB color
     */
    private static int packCubeUvColor(int uByte, int vByte) {
        return (OPAQUE_ALPHA << ALPHA_SHIFT)
            | (uByte << RED_CHANNEL_SHIFT)
            | (vByte << GREEN_CHANNEL_SHIFT);
    }

    /** Packs the occluder vertex color. The occluder shader ignores
     * the vertex color entirely — any opaque value works — but we use
     * opaque white so render-debug overlays read sensibly.
     *
     * @return the packed ARGB color
     */
    private static int packCubeOccluderColor() {
        return (OPAQUE_ALPHA << ALPHA_SHIFT)
            | (PROGRESS_BYTE_MAX << RED_CHANNEL_SHIFT)
            | (PROGRESS_BYTE_MAX << GREEN_CHANNEL_SHIFT)
            | (PROGRESS_BYTE_MAX << BLUE_CHANNEL_SHIFT);
    }

    // ── Cube mesh builders ───────────────────────────────────────────

    /** Builds the unit cube vertex position table as stride-3 floats.
     * Six faces, 4 CCW vertices each when viewed from outside, all
     * spanning {@code [-1, +1]} on their in-plane axes.
     *
     * @return stride-3 position table
     */
    private static float[] buildCubeFacePositions() {
        final float n = -UNIT;
        return new float[] {
            // +X face: normal +X, in-plane axes (-Z → +Z, -Y → +Y)
            UNIT,    n,    n,   UNIT,    n, UNIT,   UNIT, UNIT, UNIT,   UNIT, UNIT,    n,
            // -X face: normal -X, flipped winding vs +X
               n,    n, UNIT,      n,    n,    n,      n, UNIT,    n,      n, UNIT, UNIT,
            // +Y face (top)
               n, UNIT,    n,   UNIT, UNIT,    n,   UNIT, UNIT, UNIT,      n, UNIT, UNIT,
            // -Y face (bottom)
               n,    n, UNIT,   UNIT,    n, UNIT,   UNIT,    n,    n,      n,    n,    n,
            // +Z face
               n,    n, UNIT,      n, UNIT, UNIT,   UNIT, UNIT, UNIT,   UNIT,    n, UNIT,
            // -Z face
            UNIT,    n,    n,   UNIT, UNIT,    n,      n, UNIT,    n,      n,    n,    n,
        };
    }

    /** Builds the unit cube intra-face UV table as stride-2 floats.
     * Every face walks (0,0) → (1,0) → (1,1) → (0,1) so the edge
     * shader sees a full 0..1 sweep across each face.
     *
     * @return stride-2 UV table
     */
    private static float[] buildCubeFaceUvs() {
        float[] out = new float[CUBE_VERTEX_COUNT * CUBE_UV_STRIDE];
        int idx = 0;
        for (int face = 0; face < CUBE_FACES; face++) {
            // Quad winding (u, v): (0,0) → (1,0) → (1,1) → (0,1).
            out[idx++] = 0f;
            out[idx++] = 0f;
            out[idx++] = 1f;
            out[idx++] = 0f;
            out[idx++] = 1f;
            out[idx++] = 1f;
            out[idx++] = 0f;
            out[idx++] = 1f;
        }
        return out;
    }

    /** Builds the per-vertex outward normal table. Each face
     * contributes its unit outward normal four times (once per vertex)
     * so the stride-3 layout matches the position table and the emit
     * loop can share an index.
     *
     * @return stride-3 normal table
     */
    private static float[] buildCubeFaceNormals() {
        final float n = -UNIT;
        float[] out = new float[CUBE_VERTEX_COUNT * CUBE_POS_STRIDE];
        float[][] normals = {
            { UNIT,   0f,   0f },
            {    n,   0f,   0f },
            {   0f, UNIT,   0f },
            {   0f,    n,   0f },
            {   0f,   0f, UNIT },
            {   0f,   0f,    n },
        };
        int idx = 0;
        for (int face = 0; face < CUBE_FACES; face++) {
            float fnx = normals[face][0];
            float fny = normals[face][1];
            float fnz = normals[face][CUBE_Z];
            for (int v = 0; v < CUBE_VERTICES_PER_FACE; v++) {
                out[idx++] = fnx;
                out[idx++] = fny;
                out[idx++] = fnz;
            }
        }
        return out;
    }

    // ── Disc emit (duplicated from NetherBlackHoleRender) ───────────

    /**
     * Emits the flat accretion-disc annulus. Byte-for-byte duplicate
     * of the sphere style's emit so both paths feed identical geometry
     * into the shared {@code NETHER_DISK_TYPE} pipeline. Duplicated
     * rather than extracted to keep NetherBlackHoleRender untouched on
     * this experiment branch.
     *
     * @param pose      the current pose entry
     * @param c         the vertex consumer
     * @param innerR    disc inner edge radius in world blocks
     * @param outerR    disc outer edge radius in world blocks
     * @param animPhase global animation phase in [0, 1]
     */
    private static void emitDiskMesh(PoseStack.Pose pose, VertexConsumer c,
            float innerR, float outerR, float animPhase) {
        int animByte = Math.round(clamp01(animPhase) * PROGRESS_BYTE_MAX);
        for (int i = 0; i < DISK_ANGULAR_SEGMENTS; i++) {
            int i0 = i * DISK_SAMPLE_STRIDE;
            int i1 = (i + 1) * DISK_SAMPLE_STRIDE;
            float cos0 = DISK_ANGULAR_SAMPLES[i0 + DISK_SAMPLE_COS_OFFSET];
            float sin0 = DISK_ANGULAR_SAMPLES[i0 + DISK_SAMPLE_SIN_OFFSET];
            float ang0 = DISK_ANGULAR_SAMPLES[i0 + DISK_SAMPLE_ANG_OFFSET];
            float cos1 = DISK_ANGULAR_SAMPLES[i1 + DISK_SAMPLE_COS_OFFSET];
            float sin1 = DISK_ANGULAR_SAMPLES[i1 + DISK_SAMPLE_SIN_OFFSET];
            float ang1 = DISK_ANGULAR_SAMPLES[i1 + DISK_SAMPLE_ANG_OFFSET];
            emitDiskVertex(pose, c, cos0, sin0, innerR, ang0, RADIAL_T_INNER, animByte);
            emitDiskVertex(pose, c, cos1, sin1, innerR, ang1, RADIAL_T_INNER, animByte);
            emitDiskVertex(pose, c, cos1, sin1, outerR, ang1, RADIAL_T_OUTER, animByte);
            emitDiskVertex(pose, c, cos0, sin0, outerR, ang0, RADIAL_T_OUTER, animByte);
        }
    }

    /** Writes a single disc vertex. See sphere-style counterpart for
     * the vertex color packing contract with the disc shader.
     *
     * @param pose     current pose entry
     * @param c        vertex consumer
     * @param cosT     cos of the angular coordinate
     * @param sinT     sin of the angular coordinate
     * @param radius   world-space radius for this vertex (inner or outer)
     * @param angularT angular coordinate in [0, 1]
     * @param radialT  radial coordinate (0 inner, 1 outer)
     * @param animByte pre-computed animation phase byte
     */
    private static void emitDiskVertex(PoseStack.Pose pose, VertexConsumer c,
            float cosT, float sinT, float radius,
            float angularT, float radialT, int animByte) {
        int radialByte = Math.round(clamp01(radialT) * PROGRESS_BYTE_MAX);
        int angularByte = Math.round(clamp01(angularT) * PROGRESS_BYTE_MAX);
        int color = packDiskColor(radialByte, angularByte, animByte);
        c.addVertex(pose,
                BLOCK_CENTER + cosT * radius,
                BLOCK_CENTER,
                BLOCK_CENTER + sinT * radius)
            .setColor(color)
            .setNormal(pose, 0f, 1f, 0f);
    }

    /** Packs the disc vertex color identically to the sphere style:
     * R = radialT, G = angularT, B = animPhase, A = fixed opaque.
     *
     * @param radialByte  radialT already encoded to a byte
     * @param angularByte angularT already encoded to a byte
     * @param animByte    animation phase already encoded to a byte
     * @return the packed ARGB color
     */
    private static int packDiskColor(int radialByte, int angularByte, int animByte) {
        return (OPAQUE_ALPHA << ALPHA_SHIFT)
            | (radialByte << RED_CHANNEL_SHIFT)
            | (angularByte << GREEN_CHANNEL_SHIFT)
            | (animByte << BLUE_CHANNEL_SHIFT);
    }

    /** Builds the disc's pre-computed angular sample table as stride-3
     * triples of (cos, sin, angularT). Trailing sample wraps back to
     * 0 with angularT = 1 to close the ring on a continuous UV.
     *
     * @return the stride-3 angular sample table
     */
    private static float[] buildDiskAngularSamples() {
        int sampleCount = DISK_ANGULAR_SEGMENTS + 1;
        float[] out = new float[sampleCount * DISK_SAMPLE_STRIDE];
        for (int i = 0; i < sampleCount; i++) {
            double theta = TWO_PI * i / DISK_ANGULAR_SEGMENTS;
            int base = i * DISK_SAMPLE_STRIDE;
            out[base + DISK_SAMPLE_COS_OFFSET] = (float) Math.cos(theta);
            out[base + DISK_SAMPLE_SIN_OFFSET] = (float) Math.sin(theta);
            out[base + DISK_SAMPLE_ANG_OFFSET] = (float) i / DISK_ANGULAR_SEGMENTS;
        }
        return out;
    }

    // ── Utilities ────────────────────────────────────────────────────

    /** Derives a deterministic [0, 1) animation phase from the BE's
     * level game time, cycling every {@link #ANIMATION_CYCLE_TICKS}
     * ticks. Matches the sphere-style computation byte-for-byte so
     * flipping between styles keeps the swirl pattern in phase.
     *
     * @param be the chain marker block entity
     * @return the animation phase for the shader
     */
    private static float computeAnimationTime(ChainMarkerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) { return 0f; }
        long tick = level.getGameTime() % ANIMATION_CYCLE_TICKS;
        return (float) tick / ANIMATION_CYCLE_TICKS;
    }

    /** Clamps {@code v} to {@code [0, 1]}.
     *
     * @param v the value to clamp
     * @return the clamped value
     */
    private static float clamp01(float v) {
        return Math.min(1f, Math.max(0f, v));
    }
}
