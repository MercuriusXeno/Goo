package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the glowing connection line from a gasket face to its partner block,
 * with multi-pass bloom and pulsing animation.
 */
final class GasketConnectionRenderer {
    /** Base color for the connection glow line (soft cyan-white). */
    private static final int GLOW_CORE_COLOR = ARGB.color(180, 200, 230, 255);

    /** Number of bloom passes around the core line for the glow effect. */
    private static final int GLOW_PASSES = 3;

    /** Alpha multiplier per bloom pass (each layer is fainter). */
    private static final float GLOW_ALPHA_DECAY = 0.4f;

    /** Width multiplier per bloom pass (each layer is wider). */
    private static final float GLOW_WIDTH_STEP = 1.5f;

    /** Sin wave frequency for glow pulsing (~3-second period). */
    private static final float GLOW_PULSE_FREQ = 2.1f;

    /** Sin wave amplitude for glow pulsing (+-15% brightness). */
    private static final float GLOW_PULSE_AMP = 0.15f;

    /** Maximum channel value for alpha clamping. */
    private static final int MAX_CHANNEL = 255;

    /** Midpoint offset for face center calculations. */
    private static final double FACE_MIDPOINT = 0.5;

    /** Game time to radians conversion factor for glow animation. */
    private static final float GLOW_TIME_SCALE = 0.05f;

    private GasketConnectionRenderer() {}

    /**
     * Renders the glowing connection line to the partner block if linked.
     *
     * @param mc the Minecraft instance
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param pos the block position
     * @param bounds the inflated gasket bounds
     * @param role the gasket role
     * @param slot the slot index
     * @param holder the gasket holder
     */
    static void renderConnectionLine(
            Minecraft mc, PoseStack poseStack, Camera camera,
            BlockPos pos, AABB bounds, GasketRole role, int slot,
            com.mercuriusxeno.goo.block.gasket.IGasketHolder holder) {
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Vec3 to = resolveConnectionEndpoint(mc, role, slot, holder);
        if (to == null) { return; }

        Vec3 from = gasketFaceCenter(pos, bounds, role);
        renderGlowLine(poseStack, buf, camera, from, to, mc);
    }

    /**
     * Resolves the partner endpoint for a connection line, or null if the
     * partner is invalid (entity target, unloaded, or unresolvable).
     *
     * @param mc the Minecraft instance
     * @param localRole the local gasket role
     * @param localSlot the local slot index
     * @param localHolder the local gasket holder
     * @return the partner endpoint, or null
     */
    private static Vec3 resolveConnectionEndpoint(
            Minecraft mc, GasketRole localRole, int localSlot,
            com.mercuriusxeno.goo.block.gasket.IGasketHolder localHolder) {
        GasketPartner partner = localHolder.getPartner(localRole, localSlot);
        if (partner == null || partner.isEntityTarget()) { return null; }

        BlockPos partnerPos = partner.pos();
        if (mc.level == null || !mc.level.isLoaded(partnerPos)) { return null; }
        return resolvePartnerEndpoint(mc, partnerPos, partner.slot(), localRole.opposite());
    }

    /**
     * Returns the center of the gasket face on the highlighted block.
     * RECEIVER faces emit from the top of their region; TRANSMITTER from the bottom.
     *
     * @param pos the block position
     * @param bounds the axis-aligned bounding box
     * @param role the gasket role
     * @return the face center in world coordinates
     */
    static Vec3 gasketFaceCenter(BlockPos pos, AABB bounds, GasketRole role) {
        double cx = pos.getX() + (bounds.minX + bounds.maxX) * FACE_MIDPOINT;
        double cz = pos.getZ() + (bounds.minZ + bounds.maxZ) * FACE_MIDPOINT;
        double cy = role == GasketRole.RECEIVER
                ? pos.getY() + bounds.maxY
                : pos.getY() + bounds.minY;
        return new Vec3(cx, cy, cz);
    }

    /**
     * Computes the partner's gasket face center by looking up the block entity
     * and resolving its slot/machine geometry. Returns null if unresolvable.
     *
     * @param mc the Minecraft instance
     * @param partnerPos the partner block position
     * @param partnerSlot the partner slot index
     * @param partnerRole the partner gasket role
     * @return the resolved endpoint, or null if unresolvable
     */
    private static Vec3 resolvePartnerEndpoint(
            Minecraft mc, BlockPos partnerPos, int partnerSlot, GasketRole partnerRole) {
        BlockEntity partnerBe = mc.level.getBlockEntity(partnerPos);
        if (partnerBe == null) { return null; }

        AABB partnerBounds = GasketBoundsResolver.resolveGasketBounds(partnerBe, partnerRole, partnerSlot);
        if (partnerBounds == null) {
            return partnerFallbackCenter(partnerPos, partnerRole);
        }
        return gasketFaceCenter(partnerPos, partnerBounds, partnerRole);
    }

    /**
     * Returns a fallback center point when partner bounds can't be resolved.
     *
     * @param pos the partner block position
     * @param role the partner gasket role
     * @return the center of the block top (receiver) or bottom (transmitter)
     */
    private static Vec3 partnerFallbackCenter(BlockPos pos, GasketRole role) {
        double y = role == GasketRole.RECEIVER ? pos.getY() + 1.0 : pos.getY();
        return new Vec3(pos.getX() + FACE_MIDPOINT, y, pos.getZ() + FACE_MIDPOINT);
    }

    /**
     * Renders a multi-pass glow line between two world-space points.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera the render camera
     * @param from the start world position
     * @param to the end world position
     * @param mc the Minecraft instance
     */
    private static void renderGlowLine(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3 from, Vec3 to, Minecraft mc) {
        float pulse = computeGlowPulse(mc);
        float baseWidth = mc.getWindow().getAppropriateLineWidth();
        Vec3 camPos = camera.position();

        VertexConsumer line = bufferSource.getBuffer(RenderTypes.lines());
        emitGlowPasses(poseStack, line, from.subtract(camPos), to.subtract(camPos), pulse, baseWidth);
        bufferSource.endLastBatch();
    }

    /**
     * Computes the pulse multiplier for glow line brightness oscillation.
     *
     * @param mc the Minecraft instance (provides game time)
     * @return the pulse multiplier, centered on 1.0
     */
    private static float computeGlowPulse(Minecraft mc) {
        float gameTime = mc.level.getGameTime() * GLOW_TIME_SCALE;
        return 1.0f + GLOW_PULSE_AMP * Mth.sin(gameTime * GLOW_PULSE_FREQ);
    }

    /**
     * Emits bloom passes from outermost to core so the core draws on top.
     *
     * @param poseStack the pose stack for rendering
     * @param lineConsumer the vertex consumer for lines
     * @param a the camera-relative start position
     * @param b the camera-relative end position
     * @param pulse the brightness pulse multiplier
     * @param baseWidth the base line width in pixels
     */
    private static void emitGlowPasses(
            PoseStack poseStack, VertexConsumer lineConsumer,
            Vec3 a, Vec3 b, float pulse, float baseWidth) {
        for (int i = GLOW_PASSES - 1; i >= 0; i--) {
            int color = computeGlowPassColor(i, pulse);
            float width = baseWidth * (1.0f + i * GLOW_WIDTH_STEP);
            new LineContext(poseStack.last(), lineConsumer).emitEdge(
                    (float) a.x, (float) a.y, (float) a.z,
                    (float) b.x, (float) b.y, (float) b.z, color, width);
        }
    }

    /**
     * Computes the ARGB color for a single glow bloom pass, applying
     * exponential alpha decay and the pulse multiplier.
     *
     * @param passIndex the bloom pass index (0 = core, higher = outer)
     * @param pulse the brightness pulse multiplier
     * @return the ARGB color for this pass
     */
    private static int computeGlowPassColor(int passIndex, float pulse) {
        float alphaScale = (float) Math.pow(GLOW_ALPHA_DECAY, passIndex) * pulse;
        int alpha = Mth.clamp((int) (ARGB.alpha(GLOW_CORE_COLOR) * alphaScale), 0, MAX_CHANNEL);
        return ARGB.color(alpha,
                ARGB.red(GLOW_CORE_COLOR), ARGB.green(GLOW_CORE_COLOR), ARGB.blue(GLOW_CORE_COLOR));
    }
}
