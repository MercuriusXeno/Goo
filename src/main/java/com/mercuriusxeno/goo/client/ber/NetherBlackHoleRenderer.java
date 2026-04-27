package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.ability.world.NetherBehavior;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.lens.NetherLensEffect;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Nether black-hole render path: the three-pass sphere/corona/disk
 * submission triggered from {@link ChainMarkerBlockEntityRenderer} when a nether
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
 *       - flat annulus ring in the XZ plane. Inner radius floats just
 *       past the sphere silhouette, outer radius is driven by a
 *       separate expansion curve on {@link NetherBehavior} so the disk
 *       sweeps outward independent of the sphere's growth (not in
 *       lockstep). Brightness is strictly radial in the shader, so the
 *       ring reads identically from any viewing angle.</li>
 * </ol>
 */
public final class NetherBlackHoleRenderer {

    /**
     * Offset to get block center from integer position.
     */
    private static final float BLOCK_CENTER = 0.5f;
    /**
     * Minimum visible radius for the black-hole sphere so it never collapses to a single pixel.
     */
    private static final float BLACKHOLE_MIN_RADIUS = 0.25f;
    /**
     * Extra world-space margin added to the effective implosion radius so
     * the sphere fully occludes the blast zone.
     */
    private static final float OCCLUSION_MARGIN = 0.75f;
    /**
     * Solid alpha (0xFF) for the blackhole sphere vertices.
     */
    private static final int BLACKHOLE_ALPHA = 0xFF;
    /**
     * Maximum byte value for a 0..1 to byte mapping.
     */
    private static final int PROGRESS_BYTE_MAX = 255;
    /**
     * Bit shift for the alpha channel in an ARGB color.
     */
    private static final int ALPHA_SHIFT = 24;
    /**
     * Bit shift for the red channel in an ARGB color.
     */
    private static final int RED_CHANNEL_SHIFT = 16;
    /**
     * Bit shift for the green channel in an ARGB color.
     */
    private static final int GREEN_CHANNEL_SHIFT = 8;
    /**
     * Bit shift for the blue channel in an ARGB color.
     */
    private static final int BLUE_CHANNEL_SHIFT = 0;
    /**
     * Maximum encodable radius for the {@code Color.b} channel (in world blocks).
     * Must match the {@code MAX_ENCODED_RADIUS} constants in
     * {@code nether_corona.vsh} and {@code nether_disk.vsh}. 16 sits
     * safely above the max actual visible radius (~11 for stack-4 nether).
     */
    private static final float MAX_ENCODED_RADIUS = 16f;

    /**
     * Number of latitude bands on the sphere mesh (excluding poles).
     */
    private static final int SPHERE_LAT_SEGMENTS = 32;
    /**
     * Number of longitude segments around the sphere mesh.
     */
    private static final int SPHERE_LON_SEGMENTS = 64;
    /**
     * Vertices per quad (matches {@code VertexFormat.Mode.QUADS}).
     */
    private static final int VERTICES_PER_QUAD = 4;
    /**
     * Radius multiplier for the corona pass. Must match
     * {@code CORONA_SCALE} in {@code nether_corona.vsh}. Good values
     * are 1.08 for a thin corona, 1.15 for a thicker one.
     */
    private static final float CORONA_SCALE = 1.08f;
    /**
     * Disk's inner edge, as a multiple of the current sphere radius.
     * Sits just past the sphere surface so the inner rim hugs the
     * silhouette without z-fighting the sphere's equator.
     */
    private static final float DISK_INNER_SPHERE_MULT = 1.06f;
    /**
     * Disk's outer edge at full {@code diskExpansionScale}, as a
     * multiple of the full (pre-scaled) blast radius. The disk sweeps
     * from the inner edge out to this multiple over the effect
     * lifetime on a curve independent of the sphere's visible scale.
     */
    private static final float DISK_OUTER_FULL_MULT = 2.8f;
    /**
     * Minimum outer edge overshoot past the inner edge, as a multiple
     * of the current sphere radius. Prevents the ring from collapsing
     * to zero width when the expansion curve is near zero at the very
     * start of the effect.
     */
    private static final float DISK_MIN_RING_WIDTH = 0.25f;
    /**
     * Number of angular segments around the annulus. Matches
     * {@link #SPHERE_LON_SEGMENTS} so the disc has the same angular
     * tessellation as the sphere's equator.
     */
    private static final int DISK_ANGULAR_SEGMENTS = 64;
    /**
     * Floats per entry in {@link #DISK_ANGULAR_SAMPLES}. Each angular
     * sample packs {@code (cos, sin, angularT)} as three consecutive
     * floats.
     */
    private static final int DISK_SAMPLE_STRIDE = 3;
    /**
     * Offset within one {@link #DISK_SAMPLE_STRIDE}-float sample for
     * the cos component.
     */
    private static final int DISK_SAMPLE_COS_OFFSET = 0;
    /**
     * Offset within one {@link #DISK_SAMPLE_STRIDE}-float sample for
     * the sin component.
     */
    private static final int DISK_SAMPLE_SIN_OFFSET = 1;
    /**
     * Offset within one {@link #DISK_SAMPLE_STRIDE}-float sample for
     * the angularT component.
     */
    private static final int DISK_SAMPLE_ANG_OFFSET = 2;
    /**
     * Radial T value packed into Color.r for inner-edge vertices.
     */
    private static final float RADIAL_T_INNER = 0f;
    /**
     * Radial T value packed into Color.r for outer-edge vertices.
     */
    private static final float RADIAL_T_OUTER = 1f;
    /**
     * Cycle length in ticks for the swirl animation time.
     */
    private static final int ANIMATION_CYCLE_TICKS = 64;
    /**
     * Latitude offset subtracted from {@code lat / latSegments} to center phi on zero.
     */
    private static final double LATITUDE_HALF_OFFSET = 0.5;
    /**
     * Full circle in radians.
     */
    private static final double TWO_PI = 2.0 * Math.PI;

    /**
     * Pre-generated unit sphere mesh. Every 4 consecutive entries form
     * one quad. Each vertex's XYZ doubles as the unit outward normal.
     */
    private static final List<Vector3f> SPHERE_MESH = buildSphereMesh();

    /**
     * Pre-computed angular samples around the disc. Entry {@code i}
     * holds {@code (cosTheta_i, sinTheta_i, angularT_i)} where
     * {@code angularT_i = i / DISK_ANGULAR_SEGMENTS} in [0, 1]. Emitting
     * the disc quads just reads pairs of consecutive entries and does
     * the inner/outer radius multiply per frame.
     */
    private static final float[] DISK_ANGULAR_SAMPLES = buildDiskAngularSamples();

    private NetherBlackHoleRenderer() {
    }

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
            state.diskExpansionScale = nether.getDiskExpansionScale();
            state.implodeRadius = nether.getCurrentRadius();
            state.animationTime = computeAnimationTime(be);
            markLensActive(be, state);
            return;
        }
        state.netherActive = false;
    }

    /**
     * Reports this hole to the screen-space lens post-effect so it
     * can warp the main framebuffer around the sphere's screen
     * position. Uses the current visible sphere radius (not the full
     * implode radius) so the lens contracts with the sphere during
     * EXPAND/CONTRACT phases instead of always occupying the full
     * blast radius. Skips marking when the sphere is invisible
     * (visibleScale <= 0) so a DONE-phase behavior doesn't leave a
     * stale lens in place for the frame or two before the BE removes
     * itself.
     *
     * @param be    the chain marker block entity
     * @param state the populated render state for this frame
     */
    private static void markLensActive(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        if (state.visibleScale <= 0f) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Vec3 center = new Vec3(
                pos.getX() + BLOCK_CENTER,
                pos.getY() + BLOCK_CENTER,
                pos.getZ() + BLOCK_CENTER);
        float fullRadius = state.implodeRadius + OCCLUSION_MARGIN;
        float visibleRadius = Math.max(BLACKHOLE_MIN_RADIUS, fullRadius * state.visibleScale);
        NetherLensEffect.markHoleActive(center, visibleRadius);
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

        // Decouple disk geometry from sphere scale. The disk's inner
        // edge pins just past the current sphere surface (so the rim
        // hugs the silhouette as the sphere grows), while the outer
        // edge sweeps outward on its own curve (diskExpansionScale)
        // reaching DISK_OUTER_FULL_MULT * fullRadius at peak expansion.
        // This breaks the "balloon in lockstep with sphere" look and
        // reads as a shockwave-style outward bloom instead.
        float diskInnerRadius = visibleRadius * DISK_INNER_SPHERE_MULT;
        float diskOuterRadiusRaw = fullRadius * DISK_OUTER_FULL_MULT * state.diskExpansionScale;
        float diskOuterRadius = Math.max(
                diskInnerRadius + visibleRadius * DISK_MIN_RING_WIDTH,
                diskOuterRadiusRaw);

        // Main sphere: solid-black occluder with depth write on.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_BLACKHOLE_TYPE,
                (pose, c) -> emitSphereMesh(pose, c, visibleRadius, color));
        // Corona halo: same mesh at CORONA_SCALE, additive blend. The
        // fragment shader does a proper ray-sphere test against the main
        // sphere (decoded from Color.b) to discard pixels inside the
        // main silhouette, so the visible output is an annular ring.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_CORONA_TYPE,
                (pose, c) -> emitSphereMesh(pose, c, coronaRadius, color));
        // Accretion disk: flat annular ring in the world XZ plane.
        // Brightness is strictly radial in the fragment shader (no
        // minor-angle term), so the ring reads the same from any
        // viewing angle. "Edge-on shows a line" is intentional - the
        // far rim gets folded back into view once Phase C's lensing
        // post-process is wired in.
        final float innerR = diskInnerRadius;
        final float outerR = diskOuterRadius;
        final float animPhase = state.animationTime;
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_DISK_TYPE,
                (pose, c) -> emitDiskMesh(pose, c, innerR, outerR, animPhase));
    }


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
     * Emits the flat accretion-disc annulus as a ring of quads in the
     * world XZ plane. Inner vertices sit on a circle at {@code innerR},
     * outer vertices on a circle at {@code outerR}. Each vertex packs
     * the radial T (0 at inner, 1 at outer), the angular T (0..1 around
     * the ring), and the global animation phase into the vertex color
     * so the fragment shader can drive its radial brightness curve and
     * swirl animation without any uniform setup.
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
        // DISK_ANGULAR_SAMPLES is laid out as DISK_SAMPLE_STRIDE-float
        // triples {cos, sin, angularT}; see the field docstring. The
        // trailing sample at index DISK_ANGULAR_SEGMENTS wraps back to
        // 0 with angularT = 1 to close the ring on a continuous UV.
        for (int i = 0; i < DISK_ANGULAR_SEGMENTS; i++) {
            int i0 = i * DISK_SAMPLE_STRIDE;
            int i1 = (i + 1) * DISK_SAMPLE_STRIDE;
            float cos0 = DISK_ANGULAR_SAMPLES[i0 + DISK_SAMPLE_COS_OFFSET];
            float sin0 = DISK_ANGULAR_SAMPLES[i0 + DISK_SAMPLE_SIN_OFFSET];
            float ang0 = DISK_ANGULAR_SAMPLES[i0 + DISK_SAMPLE_ANG_OFFSET];
            float cos1 = DISK_ANGULAR_SAMPLES[i1 + DISK_SAMPLE_COS_OFFSET];
            float sin1 = DISK_ANGULAR_SAMPLES[i1 + DISK_SAMPLE_SIN_OFFSET];
            float ang1 = DISK_ANGULAR_SAMPLES[i1 + DISK_SAMPLE_ANG_OFFSET];
            // Quad winding (inner0 → inner1 → outer1 → outer0) keeps the
            // ring's top face oriented +Y. Culling is disabled so the
            // winding doesn't matter for visibility, but consistent
            // winding keeps any future depth/normal usage sane.
            emitDiskVertex(pose, c, cos0, sin0, innerR, ang0, RADIAL_T_INNER, animByte);
            emitDiskVertex(pose, c, cos1, sin1, innerR, ang1, RADIAL_T_INNER, animByte);
            emitDiskVertex(pose, c, cos1, sin1, outerR, ang1, RADIAL_T_OUTER, animByte);
            emitDiskVertex(pose, c, cos0, sin0, outerR, ang0, RADIAL_T_OUTER, animByte);
        }
    }

    /**
     * Writes a single disc vertex. Position is at block center offset
     * by {@code (cos * r, 0, sin * r)}, color packs radial/angular T
     * and the shared animation phase, normal is the disc's +Y face.
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


    /**
     * Packs per-frame state for the sphere and corona shaders into the
     * vertex ARGB color. Channels: R = visible scale, G = animation time,
     * B = main radius normalized by {@link #MAX_ENCODED_RADIUS},
     * A = fixed opaque.
     *
     * @param scale         implosion visible scale in [0, 1]
     * @param animationTime swirl animation phase in [0, 1]
     * @param visibleRadius main sphere's current visible radius in world blocks
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
     * Packs per-vertex disc coordinates for the accretion-disc shader:
     * R = radialT (0 inner, 1 outer), G = angularT (0..1 around the ring),
     * B = animPhase (0..1 global animation phase), A = fixed opaque.
     *
     * @param radialByte  radialT already encoded to a byte
     * @param angularByte angularT already encoded to a byte
     * @param animByte    animation phase already encoded to a byte
     * @return the packed ARGB color
     */
    private static int packDiskColor(int radialByte, int angularByte, int animByte) {
        return (BLACKHOLE_ALPHA << ALPHA_SHIFT)
                | (radialByte << RED_CHANNEL_SHIFT)
                | (angularByte << GREEN_CHANNEL_SHIFT)
                | (animByte << BLUE_CHANNEL_SHIFT);
    }


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

    /**
     * Builds one unit-sphere vertex at spherical coordinates (phi, theta).
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
     * Pre-computes the angular samples used by {@link #emitDiskMesh} at
     * emit time. Returns a float array laid out as
     * {@link #DISK_SAMPLE_STRIDE}-float triples
     * {@code (cosTheta, sinTheta, angularT)} of length
     * {@code (DISK_ANGULAR_SEGMENTS + 1) * DISK_SAMPLE_STRIDE}. The
     * trailing sample (index {@link #DISK_ANGULAR_SEGMENTS}) wraps back
     * to {@code cos(0)} and {@code sin(0)} but with {@code angularT = 1},
     * so the seam quad has a continuous angular UV instead of wrapping
     * from 1 back to 0.
     *
     * @return the angular sample table
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


    /**
     * Derives a deterministic [0, 1) animation phase from the BE's level
     * game time, cycling every {@link #ANIMATION_CYCLE_TICKS} ticks.
     *
     * @param be the chain marker block entity
     * @return the animation phase for the shader
     */
    private static float computeAnimationTime(ChainMarkerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return 0f;
        }
        long tick = level.getGameTime() % ANIMATION_CYCLE_TICKS;
        return (float) tick / ANIMATION_CYCLE_TICKS;
    }

    /**
     * Clamps {@code v} to {@code [0, 1]}.
     *
     * @param v the value to clamp
     * @return the clamped value
     */
    private static float clamp01(float v) {
        return Math.min(1f, Math.max(0f, v));
    }
}
