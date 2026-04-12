package com.mercuriusxeno.goo.client.lens;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ber.style.NetherHoleStyles;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;

/**
 * Screen-space gravitational-lensing post-process for nether black
 * holes. Applies the {@code goo:nether_lens} post-chain to the main
 * framebuffer when a nether black hole is active on the client, and
 * rewrites the chain's {@code LensConfig} uniform buffer each frame
 * with the current hole screen-space position + radii (and, for cube
 * holes, the screen-space convex hull of the cube's eight projected
 * corners) so the warp tracks the hole as the camera or the hole
 * moves.
 *
 * <p>Supports two lens shapes via the {@link LensShape} enum:
 * <ul>
 *   <li>{@link LensShape#ROUND} — the canonical sphere lens. Classic
 *       Euclidean distance-from-center math, pixel-for-pixel
 *       equivalent to the shipped sphere-style behavior. Default for
 *       the two-arg {@link #markHoleActive(Vec3, float)} overload so
 *       all existing sphere-style callers keep working unchanged.</li>
 *   <li>{@link LensShape#HEX} — the cube lens. Projects the cube's
 *       eight corners to UV space each frame, computes the 2D convex
 *       hull (always 4 – 6 vertices for a cube projection), pads to
 *       six, and uploads the resulting polygon as three
 *       {@code HoleHullN} vec4s. The shader uses a convex-polygon
 *       signed distance function, so the event horizon and photon
 *       ring follow the cube's actual on-screen silhouette.</li>
 * </ul>
 *
 * <p>Activation flow: {@code NetherBlackHoleRender} (sphere style)
 * and {@code CubeHoleStyle} (cube style) both call
 * {@link #markHoleActive} from their extract paths. A client-side
 * event handler drains that state once per frame, projects the hole
 * into screen space using the camera's orthonormal basis + the
 * player's FOV setting, builds the hull if needed, writes fresh
 * uniform bytes through {@link CommandEncoder#writeToBuffer}, and
 * toggles {@link GameRenderer#setPostEffect} on or off accordingly.
 *
 * <p>The uniform update uses {@code writeToBuffer} rather than
 * {@code mapBuffer} because the chain's custom uniforms are created
 * with {@link GpuBuffer#USAGE_UNIFORM} only (no {@code MAP_WRITE}
 * bit). The first frame for each chain instance installs a writable
 * replacement buffer ({@code USAGE_UNIFORM | USAGE_COPY_DST});
 * subsequent frames stream to that replacement.
 */
public final class NetherLensEffect {

    /** Shape of the event horizon and photon ring drawn by the lens. */
    public enum LensShape {
        /** Classic circular lens — Euclidean distance from a point
         * center. Used by the sphere black-hole style. */
        ROUND,
        /** Hex lens — convex-polygon signed distance using the
         * screen-projected hull of the cube's eight corners. Used by
         * the cube black-hole style. Falls back to {@link #ROUND} on
         * frames where the cube projection would fail (e.g. corners
         * behind the camera). */
        HEX
    }

    /** Identifier of the post-effect chain at {@code assets/goo/post_effect/nether_lens.json}. */
    public static final Identifier POST_EFFECT_ID =
            Identifier.fromNamespaceAndPath(Goo.MODID, "nether_lens");

    /** UBO block name in {@code nether_lens.json} and {@code nether_lens.fsh}. */
    private static final String LENS_CONFIG_BLOCK = "LensConfig";

    /** Std140 size of the {@code LensConfig} uniform block: five
     * {@code vec4}s at 16 bytes each (HoleParams, LensTuning,
     * HoleHull0, HoleHull1, HoleHull2). */
    private static final int LENS_CONFIG_SIZE = 80;

    /** Multiplier from event radius to the photon ring radius. 1.5 is
     * the Schwarzschild photon sphere radius in the low-budget
     * Newtonian approximation used by the shader. The shader reads
     * the photon position as an offset from the horizon rather than
     * an absolute radius, so the value passed is
     * {@code eventRadius * (PHOTON_RING_RADIUS_MULT - 1)}. */
    private static final float PHOTON_RING_RADIUS_MULT = 1.5f;

    /** Base warp strength passed as {@code LensTuning.x}. */
    private static final float LENS_STRENGTH = 0.08f;

    /** Photon-ring additive brightness passed as {@code LensTuning.z}. */
    private static final float PHOTON_RING_BRIGHTNESS = 0.4f;

    /** Shape-mode value for round lens in {@code LensTuning.w}. */
    private static final float SHAPE_MODE_ROUND = 0f;
    /** Shape-mode value for hex lens in {@code LensTuning.w}. */
    private static final float SHAPE_MODE_HEX = 1f;

    /** Half-turn degrees; used to convert the FOV setting to radians. */
    private static final float DEG_TO_RAD = (float) Math.PI / 180f;

    /** NDC → UV scale (NDC spans [-1, 1], UV spans [0, 1]). */
    private static final float NDC_TO_UV_SCALE = 0.5f;

    /** NDC → UV offset, added after the scale so NDC 0 maps to UV 0.5. */
    private static final float NDC_TO_UV_OFFSET = 0.5f;

    /** Vertex label prefix for the LensConfig GpuBuffers we manage. */
    private static final String MANAGED_LABEL_PREFIX = "goo_nether_lens/";

    /** Number of corners on a cube. */
    private static final int CUBE_CORNERS = 8;
    /** Number of hex hull vertices emitted per frame. The actual
     * convex hull of a cube projection is 4 – 6 points; we always pad
     * to 6 by repeating the last vertex, so the shader can unroll a
     * fixed-size loop and skip zero-length edges. */
    private static final int HULL_VERTICES = 6;
    /** Stride (floats) per UV-space 2D point in the corner and hull
     * working buffers. */
    private static final int POINT_STRIDE = 2;
    /** Offset of the Y component within a {@link #POINT_STRIDE}-float
     * point. */
    private static final int POINT_Y = 1;

    /** Bitmask in the cube-corner iteration index that toggles the X
     * sign. Corner index {@code i} has {@code x = -half} when
     * {@code (i & CORNER_BIT_X) == 0}, {@code +half} otherwise. */
    private static final int CORNER_BIT_X = 1;
    /** Bitmask in the cube-corner iteration index that toggles the Y sign. */
    private static final int CORNER_BIT_Y = 2;
    /** Bitmask in the cube-corner iteration index that toggles the Z sign. */
    private static final int CORNER_BIT_Z = 4;

    /** Minimum chain length before the monotone-chain hull
     * construction tests whether a vertex should be popped. The
     * cross-product check needs at least 2 prior points. */
    private static final int MONOTONE_MIN_CHAIN = 2;

    /** Number of 2D points packed into each {@code HoleHullN} vec4.
     * A vec4 holds two {@code (x, y)} pairs. */
    private static final int POINTS_PER_HULL_VEC4 = 2;

    /** Staging vec4s reused across frames to avoid per-frame allocation. */
    private static final Vector4f HOLE_PARAMS = new Vector4f();
    private static final Vector4f LENS_TUNING = new Vector4f();
    private static final Vector4f HOLE_HULL_0 = new Vector4f();
    private static final Vector4f HOLE_HULL_1 = new Vector4f();
    private static final Vector4f HOLE_HULL_2 = new Vector4f();

    /** Array view over the three {@code HoleHullN} staging vec4s so
     * the hull-packing loop can iterate them without hard-coded
     * indices. */
    private static final Vector4f[] HOLE_HULLS = { HOLE_HULL_0, HOLE_HULL_1, HOLE_HULL_2 };

    /** Working buffer for the 8 projected cube corners as
     * {@link #POINT_STRIDE}-float UV points. */
    private static final float[] CUBE_CORNERS_UV = new float[CUBE_CORNERS * POINT_STRIDE];
    /** Working buffer for the final hex hull as
     * {@link #POINT_STRIDE}-float UV points, always length
     * {@link #HULL_VERTICES}. */
    private static final float[] HULL_UV = new float[HULL_VERTICES * POINT_STRIDE];
    /** Working buffer for indices sorted by (x, y) inside
     * {@link #convexHull8}. Sized for up to {@link #CUBE_CORNERS}. */
    private static final int[] HULL_SORT_IDX = new int[CUBE_CORNERS];
    /** Scratch buffer for monotone-chain hull construction. Size is
     * {@code 2 * CUBE_CORNERS} per the algorithm's worst-case bound. */
    private static final int[] HULL_CHAIN_SCRATCH = new int[CUBE_CORNERS * 2];

    /** World-space position of the active hole, or {@code null} if
     * none is currently active. */
    private static @Nullable Vec3 activeHoleCenter;
    /** Effective world-space radius (or cube half-extent) of the
     * active hole, in blocks. */
    private static float activeHoleRadius;
    /** Shape of the active hole. {@link LensShape#ROUND} by default;
     * set via the three-arg {@link #markHoleActive} overload. */
    private static LensShape activeHoleShape = LensShape.ROUND;
    /** Incremented every frame. Used to expire stale hole markers if
     * no BER reported one this frame. */
    private static int markFrameStamp;
    /** Frame stamp at the last {@link #markHoleActive} call. */
    private static int lastMarkFrame = Integer.MIN_VALUE;

    /** The post chain instance whose LensConfig buffers have already
     * been swapped for writable copies. */
    private static @Nullable PostChain managedChain;

    private NetherLensEffect() {}

    /** Reports an active nether black hole for this frame with the
     * default {@link LensShape#ROUND} shape. Sphere-style callers use
     * this overload unchanged.
     *
     * @param center      world-space center of the hole
     * @param worldRadius effective world-space radius in blocks
     */
    public static void markHoleActive(Vec3 center, float worldRadius) {
        markHoleActive(center, worldRadius, LensShape.ROUND);
    }

    /** Reports an active nether black hole for this frame with an
     * explicit lens shape. Cube-style callers pass
     * {@link LensShape#HEX} with the cube's half-extent as the
     * {@code worldRadius}.
     *
     * @param center      world-space center of the hole
     * @param worldRadius effective world-space radius or cube
     *                    half-extent in blocks
     * @param shape       lens shape to render with
     */
    public static void markHoleActive(Vec3 center, float worldRadius, LensShape shape) {
        activeHoleCenter = center;
        activeHoleRadius = worldRadius;
        activeHoleShape = shape;
        lastMarkFrame = markFrameStamp;
    }

    /** Called once per frame before the post-effect pass runs.
     * Advances the frame stamp, projects the active hole (if any)
     * into screen UV space, writes uniforms into the lens pass's
     * custom UBO, and toggles the post effect on or off accordingly.
     *
     * @param mc the client instance
     */
    public static void applyPerFrame(Minecraft mc) {
        int frame = ++markFrameStamp;
        GameRenderer gameRenderer = mc.gameRenderer;
        // Dev kill switch: when the lens is globally disabled via the
        // NetherHoleStyles flag, clear any currently-active post
        // effect and drop the tracked hole. Markers keep calling
        // markHoleActive (it's a cheap static write) but nothing
        // drains the state, so the lens never runs.
        if (!NetherHoleStyles.LENS_ENABLED) {
            deactivateIfActive(gameRenderer);
            activeHoleCenter = null;
            return;
        }
        boolean fresh = (frame - lastMarkFrame) <= 1 && activeHoleCenter != null;
        if (!fresh) {
            deactivateIfActive(gameRenderer);
            activeHoleCenter = null;
            return;
        }
        applyActiveHole(mc, gameRenderer);
    }

    /** Orchestrator for the per-frame work: stages the round uniforms
     * (center UV, event radius, aspect, tuning), optionally stages
     * the hex hull uniforms, then loads the chain and writes the
     * final payload.
     *
     * @param mc           the client instance
     * @param gameRenderer the active game renderer
     */
    private static void applyActiveHole(Minecraft mc, GameRenderer gameRenderer) {
        Vec3 center = activeHoleCenter;
        if (center == null) { return; }
        if (!stageRoundUniforms(mc, gameRenderer, center)) { return; }
        if (activeHoleShape == LensShape.HEX) {
            stageHexHullUniforms(mc, gameRenderer, center);
        }
        PostChain chain = loadChain(mc);
        if (chain == null) { return; }
        writeUniforms(chain);
        activateIfInactive(gameRenderer);
    }

    /** Computes the round-mode uniforms ({@link #HOLE_PARAMS} and
     * {@link #LENS_TUNING}) for the currently-active hole. Defaults
     * {@link LensTuning#w} to {@link #SHAPE_MODE_ROUND}; the hex
     * staging step overrides it if appropriate. Returns {@code false}
     * (and disables the post effect for this frame) if the hole is
     * behind the camera or its projection is degenerate.
     *
     * @param mc           the client instance
     * @param gameRenderer the active game renderer
     * @param center       the hole's world-space center
     * @return whether the round uniforms are valid this frame
     */
    private static boolean stageRoundUniforms(
            Minecraft mc, GameRenderer gameRenderer, Vec3 center) {
        Camera camera = gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        double rx = center.x - cam.x;
        double ry = center.y - cam.y;
        double rz = center.z - cam.z;
        Vector3fc forward = camera.forwardVector();
        float vForward = (float) (forward.x() * rx + forward.y() * ry + forward.z() * rz);
        if (vForward <= 0f) {
            deactivateIfActive(gameRenderer);
            return false;
        }
        float tanHalfFov = computeTanHalfFov(mc);
        float aspect = computeAspect(mc);
        float vUp = viewUpComponent(camera, rx, ry, rz);
        float vRight = viewRightComponent(camera, rx, ry, rz);
        float ndcX = vRight / (vForward * tanHalfFov * aspect);
        float ndcY = vUp / (vForward * tanHalfFov);
        float uvX = ndcX * NDC_TO_UV_SCALE + NDC_TO_UV_OFFSET;
        float uvY = ndcY * NDC_TO_UV_SCALE + NDC_TO_UV_OFFSET;
        float eventRadiusUv = (activeHoleRadius / (vForward * tanHalfFov)) * NDC_TO_UV_SCALE;
        if (eventRadiusUv <= 0f) {
            deactivateIfActive(gameRenderer);
            return false;
        }
        float photonOffsetUv = eventRadiusUv * (PHOTON_RING_RADIUS_MULT - 1f);
        HOLE_PARAMS.set(uvX, uvY, eventRadiusUv, photonOffsetUv);
        LENS_TUNING.set(LENS_STRENGTH, aspect, PHOTON_RING_BRIGHTNESS, SHAPE_MODE_ROUND);
        return true;
    }

    /** Projects the 8 corners of an axis-aligned cube of half-extent
     * {@link #activeHoleRadius} at {@code center} into UV space,
     * computes the 2D convex hull of those 8 points, pads the hull
     * to {@link #HULL_VERTICES} vertices, and packs the result into
     * the {@link #HOLE_HULL_0} / {@link #HOLE_HULL_1} /
     * {@link #HOLE_HULL_2} staging vec4s. Sets
     * {@link #LENS_TUNING}{@code .w} to {@link #SHAPE_MODE_HEX} on
     * success; on failure (any corner behind the camera) leaves
     * {@code LENS_TUNING.w} at {@link #SHAPE_MODE_ROUND} so the
     * shader falls back to the round path with the already-staged
     * center and radius — no flash, no disappearing lens.
     *
     * @param mc           the client instance
     * @param gameRenderer the active game renderer
     * @param center       the cube's world-space center
     */
    private static void stageHexHullUniforms(
            Minecraft mc, GameRenderer gameRenderer, Vec3 center) {
        Camera camera = gameRenderer.getMainCamera();
        float tanHalfFov = computeTanHalfFov(mc);
        float aspect = computeAspect(mc);
        if (!projectCubeCorners(camera, center, tanHalfFov, aspect)) {
            return;  // fall back to round with the already-staged center
        }
        int hullCount = convexHull8();
        padAndPackHull(hullCount);
        LENS_TUNING.w = SHAPE_MODE_HEX;
    }

    /** Projects all 8 corners of the axis-aligned cube at
     * {@code center} with half-extent {@link #activeHoleRadius} into
     * UV space and writes them into {@link #CUBE_CORNERS_UV}. Returns
     * {@code false} as soon as any corner is behind the camera.
     *
     * @param camera     the active camera
     * @param center     the cube's world-space center
     * @param tanHalfFov tangent of half the player's vertical FOV
     * @param aspect     viewport aspect ratio (width / height)
     * @return whether all 8 corners projected successfully
     */
    private static boolean projectCubeCorners(
            Camera camera, Vec3 center, float tanHalfFov, float aspect) {
        Vec3 cam = camera.position();
        Vector3fc forward = camera.forwardVector();
        double cx = center.x - cam.x;
        double cy = center.y - cam.y;
        double cz = center.z - cam.z;
        float half = activeHoleRadius;
        for (int i = 0; i < CUBE_CORNERS; i++) {
            double sx = ((i & CORNER_BIT_X) == 0) ? -half : half;
            double sy = ((i & CORNER_BIT_Y) == 0) ? -half : half;
            double sz = ((i & CORNER_BIT_Z) == 0) ? -half : half;
            double dx = cx + sx;
            double dy = cy + sy;
            double dz = cz + sz;
            float vForward = (float) (forward.x() * dx + forward.y() * dy + forward.z() * dz);
            if (vForward <= 0f) { return false; }
            projectToCornerSlot(camera, i, dx, dy, dz, vForward, tanHalfFov, aspect);
        }
        return true;
    }

    /** Projects a single displacement {@code (dx, dy, dz)} into
     * aspect-corrected UV space at slot {@code i} of
     * {@link #CUBE_CORNERS_UV}. The X component is multiplied by
     * {@code aspect} after the NDC-to-UV conversion so the hull the
     * SDF sees is in the same "screen-proportional square" space the
     * round path uses via {@code dAspect}. Distances computed in this
     * space correspond to equal pixel offsets on both axes, so the
     * photon ring sits at uniform pixel distance from the cube
     * silhouette regardless of viewport aspect.
     *
     * @param camera     the active camera
     * @param i          corner index (0..7)
     * @param dx         camera-space displacement X
     * @param dy         camera-space displacement Y
     * @param dz         camera-space displacement Z
     * @param vForward   camera-space forward distance (already positive)
     * @param tanHalfFov tangent of half the player's vertical FOV
     * @param aspect     viewport aspect ratio
     */
    private static void projectToCornerSlot(
            Camera camera, int i, double dx, double dy, double dz,
            float vForward, float tanHalfFov, float aspect) {
        float vUp = viewUpComponent(camera, dx, dy, dz);
        float vRight = viewRightComponent(camera, dx, dy, dz);
        float ndcX = vRight / (vForward * tanHalfFov * aspect);
        float ndcY = vUp / (vForward * tanHalfFov);
        float uvX = ndcX * NDC_TO_UV_SCALE + NDC_TO_UV_OFFSET;
        float uvY = ndcY * NDC_TO_UV_SCALE + NDC_TO_UV_OFFSET;
        int slot = i * POINT_STRIDE;
        CUBE_CORNERS_UV[slot] = uvX * aspect;
        CUBE_CORNERS_UV[slot + POINT_Y] = uvY;
    }

    /** Computes the 2D convex hull of the 8 points in
     * {@link #CUBE_CORNERS_UV} via Andrew's monotone chain, writing
     * the ordered hull indices into {@link #HULL_CHAIN_SCRATCH}.
     * Returns the number of unique hull vertices (between 3 and
     * {@link #CUBE_CORNERS}, always 4 – 6 for a cube projection).
     *
     * @return number of unique hull vertices written to the chain scratch
     */
    private static int convexHull8() {
        for (int i = 0; i < CUBE_CORNERS; i++) { HULL_SORT_IDX[i] = i; }
        sortIndicesByXy(HULL_SORT_IDX, CUBE_CORNERS);
        int k = 0;
        for (int i = 0; i < CUBE_CORNERS; i++) {
            while (k >= MONOTONE_MIN_CHAIN && crossAt(
                    HULL_CHAIN_SCRATCH[k - MONOTONE_MIN_CHAIN],
                    HULL_CHAIN_SCRATCH[k - 1], HULL_SORT_IDX[i]) <= 0f) { k--; }
            HULL_CHAIN_SCRATCH[k++] = HULL_SORT_IDX[i];
        }
        int lowerEnd = k + 1;
        // Upper hull walks backward from the second-to-last sorted
        // point (the last one is already the endpoint of the lower
        // hull) to the first, skipping index 0 on the final step.
        int upperStart = CUBE_CORNERS - MONOTONE_MIN_CHAIN;
        for (int i = upperStart; i >= 0; i--) {
            while (k >= lowerEnd && crossAt(
                    HULL_CHAIN_SCRATCH[k - MONOTONE_MIN_CHAIN],
                    HULL_CHAIN_SCRATCH[k - 1], HULL_SORT_IDX[i]) <= 0f) { k--; }
            HULL_CHAIN_SCRATCH[k++] = HULL_SORT_IDX[i];
        }
        // Drop the duplicate closing vertex.
        return k - 1;
    }

    /** Insertion-sorts {@code idx[0..n)} by {@code (x, y)} on the
     * corresponding points in {@link #CUBE_CORNERS_UV}. Stable and
     * O(n^2) — fine for n = {@link #CUBE_CORNERS}.
     *
     * @param idx the index array to sort in place
     * @param n   the number of valid entries in {@code idx}
     */
    private static void sortIndicesByXy(int[] idx, int n) {
        for (int i = 1; i < n; i++) {
            int v = idx[i];
            float vx = CUBE_CORNERS_UV[v * POINT_STRIDE];
            float vy = CUBE_CORNERS_UV[v * POINT_STRIDE + POINT_Y];
            int j = i;
            while (j > 0) {
                int k = idx[j - 1];
                float kx = CUBE_CORNERS_UV[k * POINT_STRIDE];
                float ky = CUBE_CORNERS_UV[k * POINT_STRIDE + POINT_Y];
                if (kx < vx || (kx == vx && ky < vy)) { break; }
                idx[j] = idx[j - 1];
                j--;
            }
            idx[j] = v;
        }
    }

    /** Cross product of edges {@code (o→a)} and {@code (o→b)} on
     * points read from {@link #CUBE_CORNERS_UV}. Positive means
     * {@code b} is to the left of {@code (o→a)} (CCW turn).
     *
     * @param o origin index
     * @param a first endpoint index
     * @param b second endpoint index
     * @return the signed 2D cross product
     */
    private static float crossAt(int o, int a, int b) {
        float ox = CUBE_CORNERS_UV[o * POINT_STRIDE];
        float oy = CUBE_CORNERS_UV[o * POINT_STRIDE + POINT_Y];
        float ax = CUBE_CORNERS_UV[a * POINT_STRIDE];
        float ay = CUBE_CORNERS_UV[a * POINT_STRIDE + POINT_Y];
        float bx = CUBE_CORNERS_UV[b * POINT_STRIDE];
        float by = CUBE_CORNERS_UV[b * POINT_STRIDE + POINT_Y];
        return (ax - ox) * (by - oy) - (ay - oy) * (bx - ox);
    }

    /** Pads the hull to {@link #HULL_VERTICES} vertices by repeating
     * the last unique vertex, writes them into {@link #HULL_UV}, and
     * packs the result into {@link #HOLE_HULL_0} / {@link #HOLE_HULL_1}
     * / {@link #HOLE_HULL_2} as pairs of 2D points.
     *
     * @param hullCount number of unique hull vertices currently in
     *                  {@link #HULL_CHAIN_SCRATCH}
     */
    private static void padAndPackHull(int hullCount) {
        int last = Math.max(0, hullCount - 1);
        for (int i = 0; i < HULL_VERTICES; i++) {
            int src = i < hullCount ? HULL_CHAIN_SCRATCH[i] : HULL_CHAIN_SCRATCH[last];
            HULL_UV[i * POINT_STRIDE] = CUBE_CORNERS_UV[src * POINT_STRIDE];
            HULL_UV[i * POINT_STRIDE + POINT_Y] = CUBE_CORNERS_UV[src * POINT_STRIDE + POINT_Y];
        }
        // Pack pairs of hull points into the three HoleHullN vec4s.
        // Each vec4 carries POINTS_PER_HULL_VEC4 points, each point
        // has POINT_STRIDE floats, so vec4 N starts at UV float index
        // N * POINTS_PER_HULL_VEC4 * POINT_STRIDE.
        int pointsPerVec4Floats = POINTS_PER_HULL_VEC4 * POINT_STRIDE;
        for (int pair = 0; pair < HOLE_HULLS.length; pair++) {
            int base = pair * pointsPerVec4Floats;
            float ax = HULL_UV[base];
            float ay = HULL_UV[base + POINT_Y];
            float bx = HULL_UV[base + POINT_STRIDE];
            float by = HULL_UV[base + POINT_STRIDE + POINT_Y];
            HOLE_HULLS[pair].set(ax, ay, bx, by);
        }
    }

    // ── Per-frame helpers ───────────────────────────────────────────

    /** Computes {@code tan(fov/2)} from the player's current FOV
     * setting. Ignores transient modifiers (bow-pull zoom, speed,
     * etc.) so the lens stays consistent through those effects.
     *
     * @param mc the client instance
     * @return the tangent of the player's half-FOV
     */
    private static float computeTanHalfFov(Minecraft mc) {
        int fovDeg = mc.options.fov().get();
        float halfFovRad = fovDeg * NDC_TO_UV_SCALE * DEG_TO_RAD;
        return (float) Math.tan(halfFovRad);
    }

    /** Computes the viewport aspect ratio (width / height) as a
     * float, clamping height to at least 1 to avoid divide-by-zero
     * on degenerate window sizes.
     *
     * @param mc the client instance
     * @return the aspect ratio
     */
    private static float computeAspect(Minecraft mc) {
        int wPx = mc.getWindow().getWidth();
        int hPx = Math.max(1, mc.getWindow().getHeight());
        return (float) wPx / hPx;
    }

    /** Dots the camera's up basis vector with the world-space
     * displacement {@code (dx, dy, dz)}.
     *
     * @param camera the active camera
     * @param dx     world-space X displacement from the camera
     * @param dy     world-space Y displacement from the camera
     * @param dz     world-space Z displacement from the camera
     * @return the view-space Y (up) component
     */
    private static float viewUpComponent(Camera camera, double dx, double dy, double dz) {
        Vector3fc up = camera.upVector();
        return (float) (up.x() * dx + up.y() * dy + up.z() * dz);
    }

    /** Dots the camera's right basis vector (negation of
     * {@link Camera#leftVector()}) with the displacement.
     *
     * @param camera the active camera
     * @param dx     world-space X displacement from the camera
     * @param dy     world-space Y displacement from the camera
     * @param dz     world-space Z displacement from the camera
     * @return the view-space X (right) component
     */
    private static float viewRightComponent(Camera camera, double dx, double dy, double dz) {
        Vector3fc left = camera.leftVector();
        return (float) -(left.x() * dx + left.y() * dy + left.z() * dz);
    }

    // ── Post-chain plumbing ─────────────────────────────────────────

    /** Disables the post effect if we currently own the slot. Leaves
     * any other post effect (e.g. creeper cam) alone.
     *
     * @param gameRenderer the active game renderer
     */
    private static void deactivateIfActive(GameRenderer gameRenderer) {
        if (!POST_EFFECT_ID.equals(gameRenderer.currentPostEffect())) {
            return;
        }
        gameRenderer.clearPostEffect();
    }

    /** Activates the post effect if the slot is free or already ours.
     * Yields to any other post effect (vanilla spider/creeper/enderman
     * spectator cams, or another mod's chain).
     *
     * @param gameRenderer the active game renderer
     */
    private static void activateIfInactive(GameRenderer gameRenderer) {
        Identifier current = gameRenderer.currentPostEffect();
        if (POST_EFFECT_ID.equals(current)) { return; }
        if (current != null) { return; }
        gameRenderer.setPostEffect(POST_EFFECT_ID);
    }

    /** Looks up the lens post-chain via the shader manager. The chain
     * is cached so repeat calls are cheap.
     *
     * @param mc the client instance
     * @return the loaded chain or {@code null} if compilation failed
     */
    private static @Nullable PostChain loadChain(Minecraft mc) {
        ShaderManager sm = mc.getShaderManager();
        return sm.getPostChain(POST_EFFECT_ID, LevelTargetBundle.MAIN_TARGETS);
    }

    /** Rewrites the {@code LensConfig} uniform buffer on every pass
     * in the chain that declares it. See class docs for the
     * first-sighting swap rationale.
     *
     * @param chain the loaded post chain
     */
    private static void writeUniforms(PostChain chain) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, LENS_CONFIG_SIZE);
            builder.putVec4(HOLE_PARAMS);
            builder.putVec4(LENS_TUNING);
            builder.putVec4(HOLE_HULL_0);
            builder.putVec4(HOLE_HULL_1);
            builder.putVec4(HOLE_HULL_2);
            ByteBuffer bytes = builder.get();
            if (managedChain != chain) {
                installManagedBuffers(chain, bytes);
                managedChain = chain;
                return;
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (PostPass pass : chain.passes) {
                GpuBuffer buffer = pass.customUniforms.get(LENS_CONFIG_BLOCK);
                if (buffer == null) { continue; }
                bytes.position(0);
                encoder.writeToBuffer(buffer.slice(), bytes);
            }
        }
    }

    /** Replaces each pass's {@code LensConfig} GpuBuffer with a fresh
     * one carrying {@code USAGE_COPY_DST}, using {@code initialData}
     * as the first frame's payload. The old buffer is closed.
     *
     * @param chain       the post chain whose buffers to swap
     * @param initialData the std140 bytes for the first frame
     */
    private static void installManagedBuffers(PostChain chain, ByteBuffer initialData) {
        int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        for (PostPass pass : chain.passes) {
            GpuBuffer oldBuffer = pass.customUniforms.get(LENS_CONFIG_BLOCK);
            if (oldBuffer == null) { continue; }
            initialData.position(0);
            GpuBuffer newBuffer = RenderSystem.getDevice().createBuffer(
                    () -> MANAGED_LABEL_PREFIX + LENS_CONFIG_BLOCK,
                    usage,
                    initialData);
            pass.customUniforms.put(LENS_CONFIG_BLOCK, newBuffer);
            oldBuffer.close();
        }
    }
}
