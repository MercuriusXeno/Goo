package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.effect.NetherBehavior;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;

/**
 * Nether black-hole render path: the three-pass sphere/corona/disk
 * submission triggered from {@link ChainMarkerBER} when a nether
 * {@code ChainBehavior} is active on the chain marker BE. All nether-
 * specific geometry, mesh caches, color packing, and render-state
 * extraction for the black-hole visual lives here so the generic BER
 * only has to know about the orb visual and the thin dispatch check.
 *
 * <p>Three passes per frame while the nether behavior is active:
 * <ol>
 *   <li>Solid occluder sphere via {@link GooRenderTypes#NETHER_BLACKHOLE_TYPE}
 *       - writes depth so everything behind the visible face is hidden.</li>
 *   <li>Additive corona halo via {@link GooRenderTypes#NETHER_CORONA_TYPE}
 *       - same sphere mesh at {@link #CORONA_SCALE} the main radius,
 *       fragment shader does a geometric ray-sphere test against the
 *       main radius (decoded from {@code Color.b}) to carve out the
 *       annular ring.</li>
 *   <li>Additive accretion disk via {@link GooRenderTypes#NETHER_DISK_TYPE}
 *       - flat annulus in the XZ plane, inner radius equal to the main
 *       radius, outer radius at {@link #DISK_OUTER_SCALE}, with a strict
 *       "no fragment inside the sphere" discard in the fragment shader.</li>
 * </ol>
 */
public final class NetherBlackHoleRender {

    /** Offset to get block center from integer position. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Minimum visible radius for the black-hole sphere so it never collapses to a single pixel. */
    private static final float BLACKHOLE_MIN_RADIUS = 0.25f;
    /** Extra world-space margin added to the effective implosion radius so
     * the sphere fully occludes the blast zone. */
    private static final float OCCLUSION_MARGIN = 0.75f;
    /** Solid alpha (0xFF) for the blackhole sphere vertices. */
    private static final int BLACKHOLE_ALPHA = 0xFF;
    /** Maximum byte value for a 0..1 to byte mapping. */
    private static final int PROGRESS_BYTE_MAX = 255;
    /** Bit shift for the alpha channel in an ARGB color. */
    private static final int ALPHA_SHIFT = 24;
    /** Bit shift for the red channel in an ARGB color. */
    private static final int RED_CHANNEL_SHIFT = 16;
    /** Bit shift for the green channel in an ARGB color. */
    private static final int GREEN_CHANNEL_SHIFT = 8;
    /** Bit shift for the blue channel in an ARGB color. */
    private static final int BLUE_CHANNEL_SHIFT = 0;
    /** Maximum encodable radius for the {@code Color.b} channel (in world blocks).
     * Must match the {@code MAX_ENCODED_RADIUS} constants in
     * {@code nether_corona.vsh} and {@code nether_disk.vsh}. 16 sits
     * safely above the max actual visible radius (~11 for stack-4 nether). */
    private static final float MAX_ENCODED_RADIUS = 16f;

    /** Number of latitude bands on the sphere mesh (excluding poles). */
    private static final int SPHERE_LAT_SEGMENTS = 32;
    /** Number of longitude segments around the sphere mesh. */
    private static final int SPHERE_LON_SEGMENTS = 64;
    /** Vertices per quad (matches {@code VertexFormat.Mode.QUADS}). */
    private static final int VERTICES_PER_QUAD = 4;
    /** Radius multiplier for the corona pass. Must match
     * {@code CORONA_SCALE} in {@code nether_corona.vsh}. */
    private static final float CORONA_SCALE = 1.15f;
    /** Distance from the sphere center to the centerline of the disk's
     * tube, as a multiple of the main sphere radius. With the radial
     * extent below, this places the tube's inner edge at 1.05 *
     * mainRadius (just outside the sphere surface to avoid z-fighting
     * with the sphere's equatorial silhouette, which used to swallow
     * the inner edge whole) and the outer edge at 2.05 * mainRadius. */
    private static final float DISK_MAJOR_RADIUS = 1.55f;
    /** Half-width of the disk's tube in the radial direction, as a
     * multiple of the main sphere radius. Together with
     * {@link #DISK_MAJOR_RADIUS} this controls where the tube's inner
     * and outer edges land: inner at {@code DISK_MAJOR_RADIUS - DISK_RADIAL_EXTENT}
     * (1.05), outer at {@code DISK_MAJOR_RADIUS + DISK_RADIAL_EXTENT}
     * (2.05). Lower than the initial 0.75 so the whole tube is
     * visually narrower. */
    private static final float DISK_RADIAL_EXTENT = 0.50f;
    /** Half-height of the disk's tube in the Y direction, as a multiple
     * of the main sphere radius. This is the "Y extrusion" that gives
     * the disk visible thickness when viewed edge-on and lets the
     * inner edge feather smoothly into the corona. */
    private static final float DISK_VERTICAL_EXTENT = 0.15f;
    /** Number of radial segments around the disk's major axis (around
     * the world Y axis). Matches {@link #SPHERE_LON_SEGMENTS} so the
     * ring has the same angular tessellation as the sphere's equator. */
    private static final int DISK_MAJOR_SEGMENTS = 64;
    /** Number of segments around the tube's cross-section (the minor
     * angle phi). 16 is dense enough to smooth the tube's inner and
     * outer curves without blowing up vertex count. */
    private static final int DISK_MINOR_SEGMENTS = 16;
    /** Scale used to map {@code (1 - cos(phi))} from {@code [0, 2]} to
     * the innerness parameter range {@code [0, 1]}. */
    private static final double INNERNESS_HALF_SCALE = 0.5;
    /** Cycle length in ticks for the swirl animation time. */
    private static final int ANIMATION_CYCLE_TICKS = 64;
    /** Latitude offset subtracted from {@code lat / latSegments} to center phi on zero. */
    private static final double LATITUDE_HALF_OFFSET = 0.5;
    /** Full circle in radians. */
    private static final double TWO_PI = 2.0 * Math.PI;

    /** Pre-generated unit sphere mesh. Every 4 consecutive entries form
     * one quad. Each vertex's XYZ doubles as the unit outward normal. */
    private static final List<Vector3f> SPHERE_MESH = buildSphereMesh();

    /** Pre-generated unit torus mesh for the accretion disk. Every 4
     * consecutive entries form one quad. Each {@link Vector4f} packs the
     * unit-space XYZ position in {@code xyz} and the per-vertex
     * "innerness" parameter (0 at outer edge, 1 at inner edge,
     * 0.5 at top/bottom of the tube) in {@code w}. The BER scales the
     * position by the main sphere's radius at emit time and packs the
     * innerness into {@code Color.r}. */
    private static final List<Vector4f> DISK_MESH = buildDiskMesh();

    private NetherBlackHoleRender() {}

    /**
     * Populates the render state's nether fields by querying the active
     * {@link NetherBehavior} on the BE, if any. Call from the BER's
     * {@code extractRenderState}.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     */
    public static void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        if (be.getBehavior() instanceof NetherBehavior nether) {
            state.netherActive = true;
            state.visibleScale = nether.getVisibleScale();
            state.implodeRadius = nether.getCurrentRadius();
            state.animationTime = computeAnimationTime(be);
            return;
        }
        state.netherActive = false;
    }

    /**
     * Submits the three render passes for the black-hole visual:
     * occluding sphere, additive corona halo, and accretion disk. Call
     * from the BER's {@code submit} when {@code state.netherActive} is
     * true.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    public static void submit(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        float fullRadius = state.implodeRadius + OCCLUSION_MARGIN;
        float visibleRadius = Math.max(BLACKHOLE_MIN_RADIUS, fullRadius * state.visibleScale);
        int color = packBlackholeColor(state.visibleScale, state.animationTime, visibleRadius);
        float coronaRadius = visibleRadius * CORONA_SCALE;

        // Main sphere: solid-black occluder with depth write on.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_BLACKHOLE_TYPE,
            (pose, c) -> emitSphereMesh(pose, c, visibleRadius, color));
        // Corona halo: same mesh at CORONA_SCALE, additive blend. The
        // fragment shader does a proper ray-sphere test against the main
        // sphere (decoded from Color.b) to discard pixels inside the
        // main silhouette, so the visible output is an annular ring.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_CORONA_TYPE,
            (pose, c) -> emitSphereMesh(pose, c, coronaRadius, color));
        // Accretion disk: horizontal annulus in the world XZ plane.
        // Inner radius = visibleRadius, outer = visibleRadius * DISK_OUTER_SCALE.
        // Each vertex carries its own block-local radial distance in
        // Color.r for the fragment shader's strict "no fragments inside
        // the sphere" discard.
        final float diskMainRadius = visibleRadius;
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_DISK_TYPE,
            (pose, c) -> emitDiskMesh(pose, c, diskMainRadius));
    }

    // ── Mesh emit helpers ──────────────────────────────────────────────

    /**
     * Emits the pre-generated unit sphere mesh with each vertex scaled to
     * {@code radius} and translated to the block center.
     *
     * @param pose   the current pose entry
     * @param c      the vertex consumer
     * @param radius world-space sphere radius in blocks
     * @param color  packed ARGB vertex color
     */
    private static void emitSphereMesh(PoseStack.Pose pose, VertexConsumer c,
            float radius, int color) {
        for (Vector3f v : SPHERE_MESH) {
            c.addVertex(pose,
                    BLOCK_CENTER + v.x() * radius,
                    BLOCK_CENTER + v.y() * radius,
                    BLOCK_CENTER + v.z() * radius)
                .setColor(color)
                .setNormal(pose, v.x(), v.y(), v.z());
        }
    }

    /**
     * Emits the pre-generated unit torus mesh scaled to the main
     * sphere's radius. Each vertex stores its unit-space position in
     * {@code xyz} and a per-vertex "innerness" parameter in {@code w};
     * the position is multiplied by {@code mainRadius} and translated
     * to the block center, and the innerness is encoded into the red
     * channel so the fragment shader can drive its brightness falloff.
     *
     * @param pose       the current pose entry
     * @param c          the vertex consumer
     * @param mainRadius world-space main sphere radius in blocks
     */
    private static void emitDiskMesh(PoseStack.Pose pose, VertexConsumer c, float mainRadius) {
        for (Vector4f v : DISK_MESH) {
            int innernessByte = Math.round(clamp01(v.w()) * PROGRESS_BYTE_MAX);
            int color = packDiskColor(innernessByte);
            c.addVertex(pose,
                    BLOCK_CENTER + v.x() * mainRadius,
                    BLOCK_CENTER + v.y() * mainRadius,
                    BLOCK_CENTER + v.z() * mainRadius)
                .setColor(color)
                .setNormal(pose, 0f, 1f, 0f);
        }
    }

    // ── Color packing ──────────────────────────────────────────────────

    /**
     * Packs per-frame state for the sphere and corona shaders into the
     * vertex ARGB color. Channels: R = visible scale, G = animation time,
     * B = main radius normalized by {@link #MAX_ENCODED_RADIUS},
     * A = fixed opaque.
     *
     * @param scale          implosion visible scale in [0, 1]
     * @param animationTime  swirl animation phase in [0, 1]
     * @param visibleRadius  main sphere's current visible radius in world blocks
     * @return the packed ARGB color
     */
    private static int packBlackholeColor(float scale, float animationTime, float visibleRadius) {
        int scaleByte = Math.round(clamp01(scale) * PROGRESS_BYTE_MAX);
        int animByte = Math.round(clamp01(animationTime) * PROGRESS_BYTE_MAX);
        int radiusByte = Math.round(clamp01(visibleRadius / MAX_ENCODED_RADIUS) * PROGRESS_BYTE_MAX);
        return (BLACKHOLE_ALPHA << ALPHA_SHIFT)
            | (scaleByte << RED_CHANNEL_SHIFT)
            | (animByte << GREEN_CHANNEL_SHIFT)
            | (radiusByte << BLUE_CHANNEL_SHIFT);
    }

    /**
     * Packs the per-vertex innerness value for the disk (torus) shader:
     * R = innerness ({@code (1 - cos(phi)) / 2}, where phi is the tube's
     * minor angle). All other channels are unused.
     *
     * @param innernessByte the innerness value already encoded to a byte
     * @return the packed ARGB color
     */
    private static int packDiskColor(int innernessByte) {
        return (BLACKHOLE_ALPHA << ALPHA_SHIFT)
            | (innernessByte << RED_CHANNEL_SHIFT);
    }

    // ── Mesh builders ──────────────────────────────────────────────────

    /**
     * Builds a UV sphere mesh as a list of {@link Vector3f} unit vectors.
     * Every four consecutive entries form one quad matching
     * {@code VertexFormat.Mode.QUADS}. Called once at class init.
     *
     * @return the unit sphere vertex list
     */
    private static List<Vector3f> buildSphereMesh() {
        int capacity = SPHERE_LAT_SEGMENTS * SPHERE_LON_SEGMENTS * VERTICES_PER_QUAD;
        List<Vector3f> out = new ArrayList<>(capacity);
        for (int lat = 0; lat < SPHERE_LAT_SEGMENTS; lat++) {
            double phi0 = Math.PI * ((double) lat / SPHERE_LAT_SEGMENTS - LATITUDE_HALF_OFFSET);
            double phi1 = Math.PI * ((double) (lat + 1) / SPHERE_LAT_SEGMENTS - LATITUDE_HALF_OFFSET);
            for (int lon = 0; lon < SPHERE_LON_SEGMENTS; lon++) {
                double theta0 = TWO_PI * lon / SPHERE_LON_SEGMENTS;
                double theta1 = TWO_PI * (lon + 1) / SPHERE_LON_SEGMENTS;
                out.add(sphereVertex(phi0, theta0));
                out.add(sphereVertex(phi1, theta0));
                out.add(sphereVertex(phi1, theta1));
                out.add(sphereVertex(phi0, theta1));
            }
        }
        return out;
    }

    /** Builds one unit-sphere vertex at spherical coordinates (phi, theta).
     *
     * @param phi   latitude in radians, {@code [-PI/2, PI/2]}
     * @param theta longitude in radians, {@code [0, 2*PI]}
     * @return the unit-sphere vertex as a {@link Vector3f}
     */
    private static Vector3f sphereVertex(double phi, double theta) {
        double cosPhi = Math.cos(phi);
        return new Vector3f(
            (float) (cosPhi * Math.cos(theta)),
            (float) Math.sin(phi),
            (float) (cosPhi * Math.sin(theta)));
    }

    /**
     * Builds a unit torus mesh for the accretion disk as a list of
     * {@link Vector4f}s. The tube has an elliptical cross-section:
     * wide in the radial direction ({@link #DISK_RADIAL_EXTENT}) and
     * narrow in Y ({@link #DISK_VERTICAL_EXTENT}), giving the disk
     * visible Y thickness when viewed edge-on while still reading as
     * a flat ring from above.
     *
     * <p>Every four consecutive entries form one quad matching
     * {@code VertexFormat.Mode.QUADS}. Each vertex packs its unit-space
     * XYZ position in {@code xyz} and a per-vertex "innerness" parameter
     * in {@code w}, computed from the tube's minor angle phi as
     * {@code (1 - cos(phi)) / 2}: 0 at the outer edge, 1 at the inner
     * edge touching the sphere's equator, 0.5 at the top and bottom of
     * the tube. The fragment shader uses this for the brightness
     * gradient that blends the inner edge smoothly into the corona.
     *
     * <p>Inner edge radius: {@code DISK_MAJOR_RADIUS - DISK_RADIAL_EXTENT}
     * (1.0 in unit space). Outer edge radius:
     * {@code DISK_MAJOR_RADIUS + DISK_RADIAL_EXTENT}
     * ({@link #DISK_MAJOR_RADIUS} + {@link #DISK_RADIAL_EXTENT} = 2.5).
     * Both are multiplied by the main sphere's radius at emit time.
     *
     * @return the unit torus vertex list
     */
    private static List<Vector4f> buildDiskMesh() {
        int capacity = DISK_MAJOR_SEGMENTS * DISK_MINOR_SEGMENTS * VERTICES_PER_QUAD;
        List<Vector4f> out = new ArrayList<>(capacity);
        for (int i = 0; i < DISK_MAJOR_SEGMENTS; i++) {
            double theta0 = TWO_PI * i / DISK_MAJOR_SEGMENTS;
            double theta1 = TWO_PI * (i + 1) / DISK_MAJOR_SEGMENTS;
            for (int j = 0; j < DISK_MINOR_SEGMENTS; j++) {
                double phi0 = TWO_PI * j / DISK_MINOR_SEGMENTS;
                double phi1 = TWO_PI * (j + 1) / DISK_MINOR_SEGMENTS;
                // Quad wrapping the tube: (theta_i, phi_j), (theta_{i+1}, phi_j),
                // (theta_{i+1}, phi_{j+1}), (theta_i, phi_{j+1}).
                out.add(torusVertex(theta0, phi0));
                out.add(torusVertex(theta1, phi0));
                out.add(torusVertex(theta1, phi1));
                out.add(torusVertex(theta0, phi1));
            }
        }
        return out;
    }

    /** Builds one unit-torus vertex at (theta, phi). Theta is the major
     * angle around the world Y axis; phi is the minor angle around the
     * tube's cross-section. The resulting position lies on an elliptical
     * torus centered at the block origin with horizontal major radius
     * {@link #DISK_MAJOR_RADIUS}, radial tube half-extent
     * {@link #DISK_RADIAL_EXTENT}, and vertical tube half-extent
     * {@link #DISK_VERTICAL_EXTENT}. The w component encodes innerness:
     * {@code (1 - cos(phi)) / 2}.
     *
     * @param theta major angle in radians, {@code [0, 2*PI]}
     * @param phi   minor angle in radians, {@code [0, 2*PI]}
     * @return the unit-torus vertex with position in xyz and innerness in w
     */
    private static Vector4f torusVertex(double theta, double phi) {
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);
        double radial = DISK_MAJOR_RADIUS + DISK_RADIAL_EXTENT * cosPhi;
        float x = (float) (radial * Math.cos(theta));
        float y = (float) (DISK_VERTICAL_EXTENT * sinPhi);
        float z = (float) (radial * Math.sin(theta));
        float innerness = (float) ((1.0 - cosPhi) * INNERNESS_HALF_SCALE);
        return new Vector4f(x, y, z, innerness);
    }

    // ── Utilities ──────────────────────────────────────────────────────

    /** Derives a deterministic [0, 1) animation phase from the BE's level
     * game time, cycling every {@link #ANIMATION_CYCLE_TICKS} ticks.
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
