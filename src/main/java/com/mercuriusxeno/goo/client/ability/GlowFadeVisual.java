package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.GhostMineVisual.FaceEdge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Renders aurora-style fade walls rising from the perimeter edges of
 * a chain marker's ghost outline. Two additive passes create layered
 * visual complexity: a primary narrow-band curtain and a broad slow
 * sweep. Layered on top of {@link GhostMineVisual}'s outline pass
 * for GLOW-typed chain markers.
 */
public final class GlowFadeVisual {

    /**
     * Maximum height of the fade wall in blocks (half block).
     */
    private static final float FADE_WALL_HEIGHT = 8f / 16f;

    /**
     * Sine oscillation speed: 2*pi / 60 ticks = 3 second full cycle.
     */
    private static final float FADE_PULSE_SPEED = (float) (2 * Math.PI / 60);

    /**
     * Minimum alpha multiplier at the sine valley.
     */
    private static final float FADE_PULSE_MIN = 0.7f;

    /**
     * Base alpha for fade wall quads at the block edge (full opaque).
     */
    private static final int FADE_WALL_ALPHA = 0xFF;

    /**
     * Number of vertical strips per block edge for aurora effect.
     */
    private static final int AURORA_STRIPS = 32;

    /**
     * Primary edge noise frequency (peaks per block).
     */
    private static final float FRILL_FREQ = 5.0f;

    /**
     * Primary edge noise amplitude (fraction of max height).
     */
    private static final float FRILL_AMP = 0.15f;

    /**
     * Minimum height fraction at noise valleys.
     */
    private static final float BASE_FRILL = 0.85f;

    /**
     * Primary edge noise drift speed.
     */
    private static final float FRILL_SPEED = 0.12f;

    /**
     * Fine jitter frequency layered on top of primary noise.
     */
    private static final float ROUGH_FREQ = 9.0f;

    /**
     * Fine jitter amplitude.
     */
    private static final float ROUGH_AMP = 0.04f;

    /**
     * Fine jitter drift speed.
     */
    private static final float ROUGH_SPEED = 0.2f;

    /**
     * Curtain band frequency (bright columns per block).
     */
    private static final float BAND_FREQ = 12.0f;

    /**
     * Curtain band drift speed.
     */
    private static final float BAND_SPEED = 0.06f;

    /**
     * Minimum curtain band intensity (valleys stay visible).
     */
    private static final float BAND_MIN = 0.5f;

    /**
     * Curtain band peak sharpness exponent.
     */
    private static final float BAND_SHARPNESS = 3.0f;

    /**
     * Broad curtain frequency (one wide bright region per ~3 blocks).
     */
    private static final float BROAD_BAND_FREQ = 1.2f;

    /**
     * Broad curtain drift speed (very slow).
     */
    private static final float BROAD_BAND_SPEED = 0.03f;

    /**
     * Broad curtain minimum intensity (deep valleys).
     */
    private static final float BROAD_BAND_MIN = 0.15f;

    /**
     * Broad curtain peak sharpness (wider, softer peaks).
     */
    private static final float BROAD_BAND_SHARPNESS = 1.5f;

    /**
     * Half-block offset used for face center and lerp midpoint.
     */
    private static final float HALF = 0.5f;

    /**
     * Lerp range: maps [0,1] fraction to [-1,1] for run-axis interpolation.
     */
    private static final float LERP_RANGE = 2.0f;

    /**
     * Hash prime A for edge seed computation.
     */
    private static final int HASH_PRIME_A = 31;

    /**
     * Hash prime B for edge seed computation.
     */
    private static final int HASH_PRIME_B = 17;

    /**
     * Hash prime C for edge seed computation.
     */
    private static final int HASH_PRIME_C = 7;

    /**
     * Array index for X component in offset triples.
     */
    private static final int X = 0;

    /**
     * Array index for Y component in offset triples.
     */
    private static final int Y = 1;

    /**
     * Array index for Z component in offset triples.
     */
    private static final int Z = 2;

    private GlowFadeVisual() {
    }

    /**
     * Submits aurora fade walls for all perimeter edges of the ghost
     * outline. Two additive passes (narrow band + broad band) create
     * a layered curtain effect.
     *
     * @param poseStack     the pose stack for rendering transforms
     * @param nodeCollector the render node collector for geometry submission
     * @param offsets       all 3D block offsets in the effect region
     * @param filled        packed position set for neighbor lookups
     * @param baseColor     the ARGB fill color at the base of the wall
     * @param placedFace    the face the blob was placed on
     * @param gameTime      the current game time for sine animation
     */
    static void submit(PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, List<int[]> offsets,
                       Set<Long> filled, int baseColor, Direction placedFace,
                       float gameTime) {
        float pulse = FADE_PULSE_MIN + (1f - FADE_PULSE_MIN)
                * (HALF + HALF * (float) Math.sin(gameTime * FADE_PULSE_SPEED));
        int pulsedAlpha = (int) (FADE_WALL_ALPHA * pulse);
        int pulsedBase = ARGB.color(pulsedAlpha, baseColor);
        int transparentColor = ARGB.color(0, baseColor);
        Set<Long> blobMost = computeBlobMostLayer(offsets, placedFace);
        submitPrimaryPass(poseStack, nodeCollector, offsets, filled,
                blobMost, placedFace, pulsedBase, transparentColor, gameTime);
        submitBroadBandPass(poseStack, nodeCollector, offsets, filled,
                blobMost, placedFace, baseColor, gameTime);
    }

    /**
     * Primary additive pass: narrow-band aurora strips with per-strip
     * frilly height and curtain band intensity modulation.
     *
     * @param poseStack        the pose stack
     * @param nodeCollector    the render node collector
     * @param offsets          all 3D block offsets in the region
     * @param filled           packed position set for neighbor checks
     * @param blobMost         packed positions of the blob-most layer
     * @param placedFace       the face the blob was placed on
     * @param pulsedBase       pulsed ARGB base color
     * @param transparentColor fully transparent version of the base color
     * @param gameTime         the current game time for animation
     */
    private static void submitPrimaryPass(PoseStack poseStack,
                                          SubmitNodeCollector nodeCollector, List<int[]> offsets,
                                          Set<Long> filled, Set<Long> blobMost, Direction placedFace,
                                          int pulsedBase, int transparentColor, float gameTime) {
        nodeCollector.submitCustomGeometry(poseStack,
                GooRenderTypes.QUADS_ADDITIVE_NO_DEPTH,
                (pose, c) -> {
                    for (int[] o : offsets) {
                        if (!blobMost.contains(GhostMineVisual.packPos(o[X], o[Y], o[Z]))) {
                            continue;
                        }
                        emitEdgesForBlock(pose, c, o, placedFace, filled,
                                pulsedBase, transparentColor, gameTime,
                                GlowFadeVisual::computeStripHeight,
                                GlowFadeVisual::computeBandIntensity);
                    }
                });
    }

    /**
     * Second additive pass: a slow, wide intensity wave that sweeps
     * across all fade wall edges. Same strip subdivision as the primary
     * pass but with wider, slower noise for layered visual complexity.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param offsets       all 3D block offsets in the region
     * @param filled        packed position set for neighbor checks
     * @param blobMost      packed positions of the blob-most layer
     * @param placedFace    the face the blob was placed on
     * @param baseColor     the ARGB fill color
     * @param gameTime      the current game time for animation
     */
    private static void submitBroadBandPass(PoseStack poseStack,
                                            SubmitNodeCollector nodeCollector, List<int[]> offsets,
                                            Set<Long> filled, Set<Long> blobMost, Direction placedFace,
                                            int baseColor, float gameTime) {
        int transparentColor = ARGB.color(0, baseColor);
        nodeCollector.submitCustomGeometry(poseStack,
                GooRenderTypes.QUADS_ADDITIVE_NO_DEPTH,
                (pose, c) -> {
                    for (int[] o : offsets) {
                        if (!blobMost.contains(GhostMineVisual.packPos(o[X], o[Y], o[Z]))) {
                            continue;
                        }
                        emitEdgesForBlock(pose, c, o, placedFace, filled,
                                ARGB.color(FADE_WALL_ALPHA, baseColor),
                                transparentColor, gameTime,
                                GlowFadeVisual::computeBroadStripHeight,
                                GlowFadeVisual::computeBroadBand);
                    }
                });
    }

    /**
     * Emits aurora strips for all perimeter edges of one block. Each
     * edge is subdivided into vertical strips with noisy heights and
     * intensity-banded alpha. The height and band functions are
     * parameterized to support both narrow and broad passes.
     *
     * @param pose             the pose matrix
     * @param consumer         the vertex consumer
     * @param pos              the block offset {dx, dy, dz}
     * @param faceDir          the exterior face direction
     * @param filled           packed position set for neighbor checks
     * @param baseColor        opaque base color at the bottom of the wall
     * @param transparentColor fully transparent version of the base color
     * @param gameTime         the current game time for animation
     * @param heightFn         computes strip height from (worldPos, gameTime, edgeSeed)
     * @param bandFn           computes band intensity from (worldPos, gameTime)
     */
    private static void emitEdgesForBlock(PoseStack.Pose pose,
                                          VertexConsumer consumer, int[] pos, Direction faceDir,
                                          Set<Long> filled, int baseColor, int transparentColor,
                                          float gameTime, HeightFunction heightFn,
                                          BiFunction<Float, Float, Float> bandFn) {
        FaceEdge[] edges = GhostMineVisual.getFaceEdges(faceDir);
        float cx = pos[X] + HALF + faceDir.getStepX() * HALF;
        float cy = pos[Y] + HALF + faceDir.getStepY() * HALF;
        float cz = pos[Z] + HALF + faceDir.getStepZ() * HALF;
        for (FaceEdge edge : edges) {
            if (GhostMineVisual.isCoplanarNeighbor(pos, faceDir, edge.neighborDir(), filled)) {
                continue;
            }
            emitAuroraStrips(pose, consumer, cx, cy, cz,
                    faceDir, edge, baseColor, transparentColor,
                    gameTime, heightFn, bandFn);
        }
    }

    /**
     * Emits subdivided aurora strips along a single perimeter edge.
     * Each strip has a noisy top height (frilly silhouette) and
     * intensity-banded alpha (curtain-like bright columns). The
     * height and band computation are pluggable to support both
     * the narrow primary pass and the broad secondary pass.
     *
     * @param pose             the pose matrix
     * @param consumer         the vertex consumer
     * @param cx               face center X
     * @param cy               face center Y
     * @param cz               face center Z
     * @param faceDir          the outward face direction
     * @param edge             the edge descriptor (neighbor + run direction)
     * @param baseColor        opaque base color
     * @param transparentColor fully transparent version
     * @param gameTime         the current game time for animation
     * @param heightFn         computes strip height from (worldPos, gameTime, edgeSeed)
     * @param bandFn           computes band intensity from (worldPos, gameTime)
     */
    private static void emitAuroraStrips(PoseStack.Pose pose,
                                         VertexConsumer consumer,
                                         float cx, float cy, float cz, Direction faceDir, FaceEdge edge,
                                         int baseColor, int transparentColor, float gameTime,
                                         HeightFunction heightFn,
                                         BiFunction<Float, Float, Float> bandFn) {
        float nx = edge.neighborDir().getStepX() * HALF;
        float ny = edge.neighborDir().getStepY() * HALF;
        float nz = edge.neighborDir().getStepZ() * HALF;
        float rx = edge.runDir().getStepX() * HALF;
        float ry = edge.runDir().getStepY() * HALF;
        float rz = edge.runDir().getStepZ() * HALF;
        float edgeX = cx + nx;
        float edgeY = cy + ny;
        float edgeZ = cz + nz;

        float edgeSeed = computeEdgeSeed(cx, cy, cz, nx, ny, nz, rx, ry, rz);
        emitStripLoop(pose, consumer, edgeX, edgeY, edgeZ,
                rx, ry, rz, faceDir, baseColor, transparentColor,
                gameTime, heightFn, bandFn, edgeSeed);
    }

    /**
     * Emits the per-strip quads along the edge with height and band modulation.
     *
     * @param pose             the current pose matrix
     * @param consumer         the vertex consumer
     * @param edgeX            edge origin X
     * @param edgeY            edge origin Y
     * @param edgeZ            edge origin Z
     * @param rx               edge run direction X
     * @param ry               edge run direction Y
     * @param rz               edge run direction Z
     * @param faceDir          the face direction for normal
     * @param baseColor        the opaque base color
     * @param transparentColor the fully transparent color
     * @param gameTime         current game time for animation
     * @param heightFn         function computing aurora height
     * @param bandFn           function computing aurora band alpha
     * @param edgeSeed         per-edge seed for variation
     */
    private static void emitStripLoop(PoseStack.Pose pose, VertexConsumer consumer,
                                      float edgeX, float edgeY, float edgeZ,
                                      float rx, float ry, float rz, Direction faceDir,
                                      int baseColor, int transparentColor, float gameTime,
                                      HeightFunction heightFn, BiFunction<Float, Float, Float> bandFn,
                                      float edgeSeed) {
        float stepFrac = 1.0f / AURORA_STRIPS;
        float prevWorldPos = computeWorldPos(edgeX, edgeY, edgeZ, rx, ry, rz, 0f);
        float prevHeight = heightFn.compute(prevWorldPos, gameTime, edgeSeed);
        float prevBand = bandFn.apply(prevWorldPos, gameTime);

        for (int i = 0; i < AURORA_STRIPS; i++) {
            float t0 = i * stepFrac;
            float t1 = (i + 1) * stepFrac;
            float nextWorldPos = computeWorldPos(edgeX, edgeY, edgeZ, rx, ry, rz, t1);
            float nextHeight = heightFn.compute(nextWorldPos, gameTime, edgeSeed);
            float nextBand = bandFn.apply(nextWorldPos, gameTime);
            float bandAlpha = (prevBand + nextBand) * HALF;
            emitAuroraQuadPerVertex(pose, consumer,
                    edgeX, edgeY, edgeZ, rx, ry, rz, faceDir,
                    t0, t1, prevHeight, nextHeight,
                    scaleColorAlpha(baseColor, bandAlpha),
                    transparentColor);
            prevHeight = nextHeight;
            prevBand = nextBand;
        }
    }

    /**
     * Emits one vertical strip quad with independent heights at each
     * edge so the silhouette varies per-vertex and matches at corners.
     *
     * @param pose             the pose matrix
     * @param consumer         the vertex consumer
     * @param ex               edge center X
     * @param ey               edge center Y
     * @param ez               edge center Z
     * @param rx               run half-vector X
     * @param ry               run half-vector Y
     * @param rz               run half-vector Z
     * @param faceDir          the outward face direction for height extrusion
     * @param t0               start fraction along the edge [0,1]
     * @param t1               end fraction along the edge [0,1]
     * @param height0          computed height at t0 in blocks
     * @param height1          computed height at t1 in blocks
     * @param baseColor        ARGB color at the base of the quad
     * @param transparentColor ARGB color at the top (alpha = 0)
     */
    private static void emitAuroraQuadPerVertex(PoseStack.Pose pose,
                                                VertexConsumer consumer,
                                                float ex, float ey, float ez,
                                                float rx, float ry, float rz,
                                                Direction faceDir, float t0, float t1,
                                                float height0, float height1,
                                                int baseColor, int transparentColor) {
        float lerp0 = t0 * LERP_RANGE - 1.0f;
        float lerp1 = t1 * LERP_RANGE - 1.0f;
        float bx0 = ex + rx * lerp0;
        float by0 = ey + ry * lerp0;
        float bz0 = ez + rz * lerp0;
        float bx1 = ex + rx * lerp1;
        float by1 = ey + ry * lerp1;
        float bz1 = ez + rz * lerp1;
        float fx = faceDir.getStepX();
        float fy = faceDir.getStepY();
        float fz = faceDir.getStepZ();
        consumer.addVertex(pose, bx0, by0, bz0).setColor(baseColor)
                .setLight(LightCoordsUtil.FULL_BRIGHT);
        consumer.addVertex(pose, bx1, by1, bz1).setColor(baseColor)
                .setLight(LightCoordsUtil.FULL_BRIGHT);
        consumer.addVertex(pose, bx1 + fx * height1, by1 + fy * height1, bz1 + fz * height1)
                .setColor(transparentColor)
                .setLight(LightCoordsUtil.FULL_BRIGHT);
        consumer.addVertex(pose, bx0 + fx * height0, by0 + fy * height0, bz0 + fz * height0)
                .setColor(transparentColor)
                .setLight(LightCoordsUtil.FULL_BRIGHT);
    }

    /**
     * Computes the world-space position along the run axis at fraction t.
     * Projects onto the run direction so adjacent block edges produce
     * continuous values that tile seamlessly along straight runs.
     *
     * @param ex edge center X
     * @param ey edge center Y
     * @param ez edge center Z
     * @param rx run half-vector X
     * @param ry run half-vector Y
     * @param rz run half-vector Z
     * @param t  fraction along the edge [0,1]
     * @return world-space scalar position along the run axis
     */
    private static float computeWorldPos(float ex, float ey, float ez,
                                         float rx, float ry, float rz, float t) {
        float lerp = t * LERP_RANGE - 1.0f;
        float wx = ex + rx * lerp;
        float wy = ey + ry * lerp;
        float wz = ez + rz * lerp;
        return wx + wy + wz;
    }

    /**
     * Computes a per-edge phase seed from the perpendicular coordinates
     * only (subtracting the run-axis component). Adjacent blocks sharing
     * the same edge line get the same seed so the noise tiles seamlessly.
     *
     * @param cx face center X
     * @param cy face center Y
     * @param cz face center Z
     * @param nx neighbor half-vector X
     * @param ny neighbor half-vector Y
     * @param nz neighbor half-vector Z
     * @param rx run half-vector X
     * @param ry run half-vector Y
     * @param rz run half-vector Z
     * @return a deterministic phase seed for this edge
     */
    private static float computeEdgeSeed(float cx, float cy, float cz,
                                         float nx, float ny, float nz,
                                         float rx, float ry, float rz) {
        float ex = cx + nx;
        float ey = cy + ny;
        float ez = cz + nz;
        float zeroX = rx == 0 ? ex : 0;
        float zeroY = ry == 0 ? ey : 0;
        float zeroZ = rz == 0 ? ez : 0;
        return zeroX * HASH_PRIME_A + zeroY * HASH_PRIME_B
                + zeroZ * HASH_PRIME_C;
    }

    /**
     * Computes the frilly height for one primary-pass aurora strip.
     * Two layered sine octaves drift over time with a per-edge phase
     * offset, creating an organic undulating silhouette.
     *
     * @param worldPos the world-space position along the run axis
     * @param gameTime the current game time for animation drift
     * @param edgeSeed the per-edge phase seed
     * @return the computed strip height in blocks
     */
    private static float computeStripHeight(float worldPos, float gameTime,
                                            float edgeSeed) {
        float timeBreath = HALF + HALF * (float) Math.sin(gameTime * FRILL_SPEED);
        float primary = BASE_FRILL + FRILL_AMP * timeBreath
                * (float) Math.sin(worldPos * FRILL_FREQ);
        float jitterBreath = HALF + HALF * (float) Math.sin(gameTime * ROUGH_SPEED);
        float jitter = 1.0f + ROUGH_AMP * jitterBreath
                * (float) Math.sin(worldPos * ROUGH_FREQ);
        return FADE_WALL_HEIGHT * primary * jitter;
    }

    /**
     * Computes the frilly height for one broad-pass aurora strip.
     * Lower frequency and different phase from the primary pass so the
     * two silhouettes create layered visual complexity.
     *
     * @param worldPos the world-space position along the run axis
     * @param gameTime the current game time for animation drift
     * @param edgeSeed the per-edge phase seed
     * @return the computed strip height in blocks
     */
    private static float computeBroadStripHeight(float worldPos,
                                                 float gameTime, float edgeSeed) {
        float timeBreath = HALF + HALF * (float) Math.sin(gameTime * BROAD_BAND_SPEED);
        float primary = BASE_FRILL + FRILL_AMP * timeBreath
                * (float) Math.sin(worldPos * BROAD_BAND_FREQ);
        float jitterBreath = HALF + HALF * (float) Math.sin(gameTime * ROUGH_SPEED);
        float jitter = 1.0f + ROUGH_AMP * jitterBreath
                * (float) Math.sin(worldPos * FRILL_FREQ);
        return FADE_WALL_HEIGHT * primary * jitter;
    }

    /**
     * Computes the narrow curtain band intensity for one strip. A
     * sharpened sine creates bright vertical columns that drift along
     * the edge over time.
     *
     * @param worldPos the world-space position along the run axis
     * @param gameTime the current game time for drift animation
     * @return intensity factor in [BAND_MIN, 1.0]
     */
    private static float computeBandIntensity(float worldPos,
                                              float gameTime) {
        float timeModulate = HALF + HALF * (float) Math.sin(gameTime * BAND_SPEED);
        float spatial = (float) Math.sin(worldPos * BAND_FREQ);
        float raw = HALF + HALF * spatial * timeModulate;
        return BAND_MIN + (1f - BAND_MIN)
                * (float) Math.pow(raw, BAND_SHARPNESS);
    }

    /**
     * Computes the broad curtain band intensity: a wide, slow,
     * high-contrast wave spanning multiple blocks.
     *
     * @param worldPos the world-space position along the run axis
     * @param gameTime the current game time for drift animation
     * @return intensity factor in [BROAD_BAND_MIN, 1.0]
     */
    private static float computeBroadBand(float worldPos,
                                          float gameTime) {
        float timeModulate = HALF + HALF * (float) Math.sin(gameTime * BROAD_BAND_SPEED);
        float spatial = (float) Math.sin(worldPos * BROAD_BAND_FREQ);
        float raw = HALF + HALF * spatial * timeModulate;
        return BROAD_BAND_MIN + (1f - BROAD_BAND_MIN)
                * (float) Math.pow(raw, BROAD_BAND_SHARPNESS);
    }

    /**
     * Scales the alpha channel of an ARGB color by a 0-1 factor,
     * preserving the RGB channels.
     *
     * @param argb   the source ARGB color
     * @param factor the alpha scale factor in [0, 1]
     * @return the color with scaled alpha
     */
    private static int scaleColorAlpha(int argb, float factor) {
        int a = (int) (ARGB.alpha(argb) * factor);
        return ARGB.color(a, argb);
    }

    /**
     * For each perpendicular column along the blast axis, finds the
     * block closest to the blob (highest offset in the placed-face
     * direction). Only these blocks receive fade walls, preventing
     * interior layers from rendering redundant geometry.
     *
     * @param offsets    all 3D block offsets in the region
     * @param placedFace the face the blob was placed on
     * @return packed positions of the blob-most block per column
     */
    private static Set<Long> computeBlobMostLayer(List<int[]> offsets,
                                                  Direction placedFace) {
        Map<Long, int[]> bestPerColumn = new HashMap<>();
        int ax = placedFace.getStepX();
        int ay = placedFace.getStepY();
        int az = placedFace.getStepZ();
        for (int[] o : offsets) {
            int depth = o[X] * ax + o[Y] * ay + o[Z] * az;
            long columnKey = packPerpendicular(o, placedFace);
            int[] current = bestPerColumn.get(columnKey);
            if (current == null
                    || depth > (current[X] * ax
                    + current[Y] * ay + current[Z] * az)) {
                bestPerColumn.put(columnKey, o);
            }
        }
        Set<Long> result = HashSet.newHashSet(bestPerColumn.size());
        for (int[] o : bestPerColumn.values()) {
            result.add(GhostMineVisual.packPos(
                    o[X], o[Y], o[Z]));
        }
        return result;
    }

    /**
     * Packs the two perpendicular coordinates of a block offset into a
     * single long for use as a column key. The blast-axis coordinate is
     * zeroed so all blocks in the same column share the same key.
     *
     * @param pos  the block offset {dx, dy, dz}
     * @param face the placed face (defines the blast axis)
     * @return packed perpendicular coordinates
     */
    private static long packPerpendicular(int[] pos, Direction face) {
        return switch (face.getAxis()) {
            case X -> GhostMineVisual.packPos(0, pos[Y], pos[Z]);
            case Y -> GhostMineVisual.packPos(pos[X], 0, pos[Z]);
            case Z -> GhostMineVisual.packPos(pos[X], pos[Y], 0);
        };
    }

    /**
     * Functional interface for strip height computation, accepting
     * world position, game time, and edge seed. Used to parameterize
     * the narrow vs broad pass without code duplication.
     */
    @FunctionalInterface
    interface HeightFunction {

        /**
         * Computes the strip height for one aurora strip.
         *
         * @param worldPos the world-space position along the run axis
         * @param gameTime the current game time
         * @param edgeSeed the per-edge phase seed
         * @return the computed strip height in blocks
         */
        float compute(float worldPos, float gameTime, float edgeSeed);
    }
}
