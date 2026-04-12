package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.ber.CuboidBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Collection;

/**
 * Renders blob flights as multi-layer animated projectiles.
 * Core: opaque fluid cuboid with pulsing width.
 * Shell: translucent cuboid for slime effect.
 * Tail: billboarded sprite quads behind the velocity vector.
 * Particles: drip sparks and color-tinted bubbles shed along the trail.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BlobFlightRenderer {

    /** Block atlas texture path - same convention as the BERs. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Half-width of the core cuboid (~2.5 pixels). */
    private static final float CORE_HW = 0.08f;
    /** Half-width of the shell cuboid (~5 pixels). */
    private static final float SHELL_HW = 0.15f;
    /** Shell alpha (translucent). */
    private static final int SHELL_ALPHA = 0x60;

    /** Tail quad length behind the blob. */
    private static final float TAIL_LENGTH = 0.4f;
    /** Tail quad half-width. */
    private static final float TAIL_HW = 0.06f;

    /** Full-bright packed light for entity rendering. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Pulse amplitude for core breathing animation. */
    private static final float CORE_PULSE_AMP = 0.1f;

    /** Pulse speed multiplier for core breathing animation. */
    private static final float CORE_PULSE_SPEED = 0.3f;

    /** Normal direction for negative-facing surfaces. */
    private static final float NORMAL_NEG = -1f;

    /** Bit shift for alpha channel in ARGB. */
    private static final int ALPHA_SHIFT = 24;

    /** RGB mask for stripping alpha from a color. */
    private static final int RGB_MASK = 0xFFFFFF;

    /** Tail alpha value (semi-transparent). */
    private static final int TAIL_ALPHA = 0x80;

    /** Threshold for up-vector selection to avoid parallel cross products. */
    private static final double UP_THRESHOLD = 0.9;

    private BlobFlightRenderer() {}

    /**
     * Renders all active blob flights after translucent blocks so the shell blends correctly.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Collection<BlobFlightManager.BlobFlight> flights = BlobFlightManager.getActiveFlights();
        if (flights.isEmpty()) { return; }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return; }

        RenderContext ctx = buildRenderContext(mc, event.getPoseStack());
        for (BlobFlightManager.BlobFlight flight : flights) {
            renderFlight(ctx, flight);
        }
    }

    /**
     * Captures the per-frame rendering state needed by all flight renders.
     *
     * @param mc the Minecraft client instance
     * @param poseStack the pose stack for rendering
     * @return the render context for this frame
     */
    private static RenderContext buildRenderContext(Minecraft mc, PoseStack poseStack) {
        Camera camera = mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = mc.level.getGameTime() + partialTick;
        return new RenderContext(poseStack, buffers, camera, gameTime, partialTick);
    }

    /**
     * Per-frame render state shared across all flight renders.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param camera the render camera
     * @param gameTime the level game time including partial tick
     * @param partialTick the sub-tick interpolation factor for this frame
     */
    private record RenderContext(
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffers,
            Camera camera,
            float gameTime,
            float partialTick
    ) {}

    /**
     * Renders a single flight: core, shell, tail, and particles. The
     * flight position and velocity are sampled at the current partial
     * tick so the blob interpolates smoothly at render FPS rather than
     * snapping once per 20 Hz client tick.
     *
     * @param ctx the per-frame render context
     * @param flight the flight to render
     */
    private static void renderFlight(RenderContext ctx, BlobFlightManager.BlobFlight flight) {
        Vec3 pos = flight.getPosition(ctx.partialTick);
        Vec3 vel = flight.getVelocity(ctx.partialTick);

        translateToFlight(ctx, pos);
        renderFlightLayers(ctx, flight.gooType, vel);
        ctx.poseStack.popPose();

        BlobTrailParticles.spawnTrailParticles(pos, vel, flight.gooType, flight);
    }

    /**
     * Pushes pose and translates to the flight's camera-relative position.
     *
     * @param ctx the per-frame render context
     * @param pos the flight's world position
     */
    private static void translateToFlight(RenderContext ctx, Vec3 pos) {
        Vec3 camPos = ctx.camera.position();
        ctx.poseStack.pushPose();
        ctx.poseStack.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
    }

    /**
     * Renders core, shell, and tail layers for a single flight.
     *
     * @param ctx the per-frame render context
     * @param type the goo type
     * @param vel the velocity vector
     */
    private static void renderFlightLayers(RenderContext ctx, GooType type, Vec3 vel) {
        renderCore(ctx.poseStack, ctx.buffers, type, ctx.gameTime);
        renderShell(ctx.poseStack, ctx.buffers, type);
        renderTail(ctx.poseStack, ctx.buffers, type, vel, ctx.gameTime);
    }

    /**
     * Core: opaque fluid cuboid with sin-pulsing width.
     * Uses entitySolid for fully opaque rendering.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param gameTime the level game time in ticks
     */
    private static void renderCore(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, float gameTime) {
        float pulse = 1.0f + CORE_PULSE_AMP * Mth.sin(gameTime * CORE_PULSE_SPEED);
        float hw = CORE_HW * pulse;

        GooRenderUtil.UvRect uv = spriteToUv(type);
        VertexConsumer c = buffers.getBuffer(RenderTypes.entitySolid(BLOCK_ATLAS_TEXTURE));
        com.mercuriusxeno.goo.client.ber.RenderContext ctx = new com.mercuriusxeno.goo.client.ber.RenderContext(poseStack.last(), c, FULL_BRIGHT);
        CuboidBounds box = new CuboidBounds(-hw, hw, -hw, hw, -hw, hw);
        ctx.emitBox(box, uv);
    }

    /**
     * Shell: larger translucent cuboid with the goo type's color tint.
     * Gives the blob a slime-like outer glow.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param type the goo type
     */
    private static void renderShell(PoseStack poseStack, MultiBufferSource buffers,
            GooType type) {
        int color = (SHELL_ALPHA << ALPHA_SHIFT) | (type.getColor() & RGB_MASK);
        GooRenderUtil.UvRect uv = spriteToUv(type);
        VertexConsumer c = buffers.getBuffer(RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE));
        com.mercuriusxeno.goo.client.ber.RenderContext ctx = new com.mercuriusxeno.goo.client.ber.RenderContext(poseStack.last(), c, FULL_BRIGHT);
        CuboidBounds box = new CuboidBounds(-SHELL_HW, SHELL_HW, -SHELL_HW, SHELL_HW, -SHELL_HW, SHELL_HW);
        ctx.emitBox(color, box, uv);
    }

    /**
     * Looks up the fluid sprite for a goo type and converts it to a UV rect.
     *
     * @param type the goo type
     * @return the UV rectangle for the fluid sprite
     */
    private static GooRenderUtil.UvRect spriteToUv(GooType type) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        return new GooRenderUtil.UvRect(
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
    }

    /**
     * Tail: two crossing quads extending behind the blob along the velocity vector.
     * Gives the projectile a streaking motion feel.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param velocity the velocity direction vector
     * @param gameTime the level game time in ticks
     */
    private static void renderTail(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, Vec3 velocity, float gameTime) {
        GooRenderUtil.UvRect uv = spriteToUv(type);
        int tailColor = (TAIL_ALPHA << ALPHA_SHIFT) | (type.getColor() & RGB_MASK);
        VertexConsumer c = buffers.getBuffer(RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE));
        TailAxes axes = buildTailAxes(velocity);

        emitCrossingTailQuads(poseStack, c, tailColor, axes, uv);
    }

    /**
     * Emits two perpendicular tail quads inside a pose push/pop scope.
     *
     * @param poseStack the pose stack for rendering
     * @param c the vertex consumer
     * @param tailColor the ARGB tail color
     * @param axes the tail coordinate axes
     * @param uv the UV texture rectangle
     */
    private static void emitCrossingTailQuads(PoseStack poseStack, VertexConsumer c,
            int tailColor, TailAxes axes, GooRenderUtil.UvRect uv) {
        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        emitTailQuad(pose, c, FULL_BRIGHT, tailColor, axes.tailEnd, axes.right, TAIL_HW, uv);
        emitTailQuad(pose, c, FULL_BRIGHT, tailColor, axes.tailEnd, axes.up, TAIL_HW, uv);
        poseStack.popPose();
    }

    /**
     * Builds a local coordinate system from the velocity vector for tail rendering.
     *
     * @param velocity the velocity direction vector
     * @return the computed tail axes (right, up, and tail endpoint)
     */
    private static TailAxes buildTailAxes(Vec3 velocity) {
        Vec3 up = (Math.abs(velocity.y) < UP_THRESHOLD)
                ? new Vec3(0, 1, 0)
                : new Vec3(1, 0, 0);
        Vec3 right = velocity.cross(up).normalize();
        Vec3 realUp = right.cross(velocity).normalize();
        Vec3 tailEnd = velocity.scale(-TAIL_LENGTH);
        return new TailAxes(right, realUp, tailEnd);
    }

    /**
     * Holds the local coordinate axes and endpoint for tail quad emission.
     *
     * @param right the perpendicular right axis
     * @param up the perpendicular up axis
     * @param tailEnd the tail endpoint behind the blob
     */
    private record TailAxes(Vec3 right, Vec3 up, Vec3 tailEnd) {}

    /**
     * Emits a single tail quad stretched from origin to tailEnd, with half-width along the axis.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param tailEnd the tail endpoint behind the blob
     * @param axis the perpendicular axis vector
     * @param hw the half-width in block coords
     * @param uv the UV texture rectangle
     */
    private static void emitTailQuad(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            Vec3 tailEnd, Vec3 axis, float hw, GooRenderUtil.UvRect uv) {
        TailCorners corners = buildTailCorners(tailEnd, axis, hw);
        Vec3 normal = tailEnd.normalize().cross(axis);
        emitTailFrontFace(pose, c, light, color, corners, uv, normal);
        emitTailBackFace(pose, c, light, color, corners, uv, normal);
    }

    /**
     * Computes the axis and endpoint offsets for a tail quad's four corners.
     *
     * @param tailEnd the tail endpoint behind the blob
     * @param axis the perpendicular axis vector
     * @param hw the half-width in block coords
     * @return the precomputed corner offsets
     */
    private static TailCorners buildTailCorners(Vec3 tailEnd, Vec3 axis, float hw) {
        return new TailCorners(
                (float) (axis.x * hw), (float) (axis.y * hw), (float) (axis.z * hw),
                (float) tailEnd.x, (float) tailEnd.y, (float) tailEnd.z);
    }

    /**
     * Precomputed axis and endpoint offsets for a tail quad.
     *
     * @param ax the axis X offset scaled by half-width
     * @param ay the axis Y offset scaled by half-width
     * @param az the axis Z offset scaled by half-width
     * @param ex the tail end X coordinate
     * @param ey the tail end Y coordinate
     * @param ez the tail end Z coordinate
     */
    private record TailCorners(float ax, float ay, float az, float ex, float ey, float ez) {}

    /**
     * Emits the front face of a tail quad using precomputed corner offsets.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param tc the precomputed tail corner offsets
     * @param uv the UV texture rectangle
     * @param normal the face normal vector
     */
    private static void emitTailFrontFace(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            TailCorners tc, GooRenderUtil.UvRect uv, Vec3 normal) {
        float nx = (float) normal.x;
        float ny = (float) normal.y;
        float nz = (float) normal.z;
        GooRenderUtil.vertexColored(pose, c, light, color,  tc.ax,  tc.ay,  tc.az, uv.u0(), uv.v0(), nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, -tc.ax, -tc.ay, -tc.az, uv.u1(), uv.v0(), nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, tc.ex - tc.ax, tc.ey - tc.ay, tc.ez - tc.az, uv.u1(), uv.v1(), nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, tc.ex + tc.ax, tc.ey + tc.ay, tc.ez + tc.az, uv.u0(), uv.v1(), nx, ny, nz);
    }

    /**
     * Emits the back face (reverse winding) of a tail quad using precomputed corner offsets.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param tc the precomputed tail corner offsets
     * @param uv the UV texture rectangle
     * @param normal the face normal vector (will be negated for back face)
     */
    private static void emitTailBackFace(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            TailCorners tc, GooRenderUtil.UvRect uv, Vec3 normal) {
        float nx = (float) -normal.x;
        float ny = (float) -normal.y;
        float nz = (float) -normal.z;
        GooRenderUtil.vertexColored(pose, c, light, color, tc.ex + tc.ax, tc.ey + tc.ay, tc.ez + tc.az, uv.u0(), uv.v1(), nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, tc.ex - tc.ax, tc.ey - tc.ay, tc.ez - tc.az, uv.u1(), uv.v1(), nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, -tc.ax, -tc.ay, -tc.az, uv.u1(), uv.v0(), nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color,  tc.ax,  tc.ay,  tc.az, uv.u0(), uv.v0(), nx, ny, nz);
    }

}
