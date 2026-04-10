package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ber.LineCtx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Renders a glowing dashed arc polyline from the glove hand to the target,
 * previewing the throw trajectory with multi-pass bloom.
 */
final class ArcRenderer {
    /** Dash segment length in world units. */
    private static final float DASH_ON = 0.5f;
    /** Gap length between dashes in world units. */
    private static final float DASH_OFF = 0.5f;
    /** Full dash cycle length (segment + gap = 1 meter). */
    private static final float DASH_CYCLE = DASH_ON + DASH_OFF;
    /** Scroll speed in world units per second - dashes flow toward the target. */
    private static final float SCROLL_SPEED = 1.5f;
    /** Core alpha for the arc dashes. */
    private static final int ARC_ALPHA = 180;
    /** Distance between polyline sample points on the arc. */
    private static final float SAMPLE_SPACING = 0.25f;
    /** Number of bloom passes for the glow effect (core + outer halos). */
    private static final int ARC_GLOW_PASSES = 3;
    /** Width multiplier step per bloom pass. */
    private static final float ARC_GLOW_WIDTH_STEP = 1.5f;
    /** Alpha decay per bloom pass (exponential). */
    private static final float ARC_GLOW_ALPHA_DECAY = 0.35f;

    /** Minimum arc segment count. */
    private static final int MIN_ARC_SEGMENTS = 8;
    /** Maximum arc segment count. */
    private static final int MAX_ARC_SEGMENTS = 128;
    /** Maximum alpha channel value. */
    private static final int MAX_ALPHA = 255;
    /** Half segment midpoint for dash calculations. */
    private static final float DASH_MID = 0.5f;

    /** Ticks-per-second divisor for converting game time to seconds. */
    private static final float TICKS_PER_SECOND = 20.0f;

    private ArcRenderer() {}

    /**
     * Renders a glowing animated dashed arc from the glove hand to the target,
     * previewing the throw trajectory.
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the active camera
     * @param player       the local player
     * @param end          the target endpoint position
     * @param rgb          the RGB color for tinting
     * @param partialTick  the partial tick for animation
     * @param grannyArc    if true, uses the boosted granny-arc peak height
     */
    static void renderTargetArc(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, Vec3 end,
            int rgb, float partialTick, boolean grannyArc) {
        Vec3 start = GooTargetHighlighter.getGloveHandPosition(player, camera);
        Vec3[] points = sampleArcPoints(start, end, grannyArc);
        float dashOffset = computeDashOffset(partialTick);
        Minecraft mc = Minecraft.getInstance();
        emitDashedGlow(poseStack, bufferSource, camera, points,
                points.length - 1, rgb, dashOffset,
                mc.getWindow().getAppropriateLineWidth());
    }

    /**
     * Samples the throw arc polyline from start to end.
     *
     * @param start     arc origin (hand position)
     * @param end       arc destination (target center)
     * @param grannyArc true for boosted granny-arc peak height
     * @return sampled polyline points
     */
    private static Vec3[] sampleArcPoints(Vec3 start, Vec3 end, boolean grannyArc) {
        double distance = start.distanceTo(end);
        double travelTicks = ThrowArc.travelTicks(distance);
        double peak = computeArcPeak(travelTicks, grannyArc);
        int segments = Mth.clamp(
                (int) (distance / SAMPLE_SPACING),
                MIN_ARC_SEGMENTS, MAX_ARC_SEGMENTS);
        return ThrowArc.sampleArc(start, end, peak, segments);
    }

    /**
     * Selects the arc peak height based on whether this is a granny arc.
     *
     * @param travelTicks estimated travel time in ticks
     * @param grannyArc   true for boosted granny-arc peak
     * @return the arc peak height
     */
    private static double computeArcPeak(double travelTicks, boolean grannyArc) {
        return grannyArc
                ? ThrowArc.grannyPeak(travelTicks)
                : ThrowArc.basePeak(travelTicks);
    }

    /**
     * Computes the scrolling dash offset based on game time.
     *
     * @param partialTick the partial tick for smooth animation
     * @return dash offset in world units
     */
    private static float computeDashOffset(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level.getGameTime() + partialTick)
                / TICKS_PER_SECOND * SCROLL_SPEED;
    }

    /**
     * Emits multi-pass glowing dashed lines for the sampled arc polyline.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera the render camera
     * @param points the sampled arc polyline points
     * @param segments the number of arc segments
     * @param rgb the RGB color value
     * @param dashOffset the dash scroll offset
     * @param baseWidth the base line width
     */
    private static void emitDashedGlow(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3[] points, int segments,
            int rgb, float dashOffset, float baseWidth) {
        Vec3 cam = camera.position();
        LineCtx ctx = new LineCtx(poseStack.last(), bufferSource.getBuffer(GooRenderTypes.LINES_GLOW));
        for (int pass = ARC_GLOW_PASSES - 1; pass >= 0; pass--) {
            emitDashedPass(ctx, cam, points, segments,
                    dashOffset, computeGlowPassColor(rgb, pass),
                    baseWidth * (1.0f + pass * ARC_GLOW_WIDTH_STEP));
        }
        bufferSource.endLastBatch();
    }

    /**
     * Computes the ARGB color for a bloom pass with exponential alpha decay.
     *
     * @param rgb  the base RGB color
     * @param pass the bloom pass index (0 = core, higher = dimmer)
     * @return the ARGB color with decayed alpha
     */
    private static int computeGlowPassColor(int rgb, int pass) {
        float alphaScale = (float) Math.pow(ARC_GLOW_ALPHA_DECAY, pass);
        int alpha = Mth.clamp((int) (ARC_ALPHA * alphaScale), 0, MAX_ALPHA);
        return ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }

    /**
     * Emits one pass of dashed line segments for the arc polyline.
     *
     * @param ctx        the line render context
     * @param cam        the camera position
     * @param points     the sampled arc polyline points
     * @param segments   the number of arc segments
     * @param dashOffset the dash scroll offset
     * @param color      the ARGB color value
     * @param width      the line width
     */
    private static void emitDashedPass(
            LineCtx ctx, Vec3 cam, Vec3[] points, int segments,
            float dashOffset, int color, float width) {
        float arcLen = 0f;
        for (int i = 0; i < segments; i++) {
            arcLen = emitDashSegment(ctx, cam,
                    points[i], points[i + 1],
                    arcLen, dashOffset, color, width);
        }
    }

    /**
     * Emits a single dash segment if it falls in the "on" phase of the pattern.
     *
     * @param ctx        the line render context
     * @param cam        camera position
     * @param a          segment start
     * @param b          segment end
     * @param arcLen     accumulated arc length before this segment
     * @param dashOffset the dash scroll offset
     * @param color      the ARGB color
     * @param width      the line width
     * @return the updated accumulated arc length
     */
    private static float emitDashSegment(
            LineCtx ctx, Vec3 cam, Vec3 a, Vec3 b, float arcLen,
            float dashOffset, int color, float width) {
        float segLen = (float) a.distanceTo(b);
        if (isDashOn(arcLen + segLen * DASH_MID, dashOffset)) {
            ctx.emitEdge(
                (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z),
                (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z),
                color, width);
        }
        return arcLen + segLen;
    }

    /**
     * Returns true if the dash at the given arc-length is in the "on" phase.
     *
     * @param midArcLen the midArcLen
     * @param dashOffset the dash scroll offset
     * @return true if dashOn
     */
    private static boolean isDashOn(float midArcLen, float dashOffset) {
        float phase = (midArcLen - dashOffset) % DASH_CYCLE;
        if (phase < 0) { phase += DASH_CYCLE; }
        return phase < DASH_ON;
    }
}
