package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

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

    /** Tick interval for trail particle spawning (every N ticks). */
    private static final int PARTICLE_TICK_INTERVAL = 2;

    /** Fully opaque black alpha for particle colors. */
    private static final int OPAQUE_BLACK = 0xFF000000;

    /** Trail velocity scale for drip particles. */
    private static final double DRIP_VEL_SCALE = 0.05;

    /** Downward velocity for drip particles. */
    private static final double DRIP_DOWN_VEL = -0.02;

    /** Minimum squared distance from camera before fog particles spawn. */
    private static final double FOG_MIN_DIST_SQ = 9.0;

    /** Base fog particle count before random addition. */
    private static final int FOG_BASE_COUNT = 2;

    /** Random fog particle count range (exclusive upper bound). */
    private static final int FOG_RANDOM_RANGE = 3;

    /** Fog position offset scale behind the blob. */
    private static final double FOG_POS_SCALE = 0.15;

    /** Position spread multiplier for fog particles. */
    private static final double FOG_SPREAD = 2;

    /** Velocity jitter for fog particles. */
    private static final double FOG_VEL_JITTER = 0.02;

    /** Random offset range half-extent. */
    private static final double OFFSET_HALF = 0.5;

    /** Random offset scale. */
    private static final double OFFSET_SCALE = 0.1;

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
        return new RenderContext(poseStack, buffers, camera, gameTime);
    }

    /**
     * Per-frame render state shared across all flight renders.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param camera the render camera
     * @param gameTime the level game time including partial tick
     */
    private record RenderContext(
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffers,
            Camera camera,
            float gameTime
    ) {}

    /**
     * Renders a single flight: core, shell, tail, and particles.
     *
     * @param ctx the per-frame render context
     * @param flight the flight to render
     */
    private static void renderFlight(RenderContext ctx, BlobFlightManager.BlobFlight flight) {
        Vec3 pos = flight.getPosition(0f);
        Vec3 vel = flight.getVelocity(0f);

        translateToFlight(ctx, pos);
        renderFlightLayers(ctx, flight.gooType, vel);
        ctx.poseStack.popPose();

        spawnTrailParticles(pos, vel, flight.gooType, flight);
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
        PoseStack.Pose pose = poseStack.last();

        emitCubeFaces(pose, c, FULL_BRIGHT, hw, uv);
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
        PoseStack.Pose pose = poseStack.last();

        emitColoredCubeFaces(pose, c, FULL_BRIGHT, color, SHELL_HW, uv);
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
     * Emits six faces of an axis-aligned cube centered at the origin using white vertex color.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param hw the half-width of the cube
     * @param uv the UV texture rectangle
     */
    private static void emitCubeFaces(PoseStack.Pose pose, VertexConsumer c, int light,
            float hw, GooRenderUtil.UvRect uv) {
        GooRenderUtil.faceY(pose, c, light, -hw, hw,  hw, -hw, hw, uv,  1f);
        GooRenderUtil.faceY(pose, c, light, -hw, hw, -hw, -hw, hw, uv, NORMAL_NEG);
        GooRenderUtil.faceX(pose, c, light,  hw, -hw, hw, -hw, hw, uv,  1f);
        GooRenderUtil.faceX(pose, c, light, -hw, -hw, hw, -hw, hw, uv, NORMAL_NEG);
        GooRenderUtil.faceZ(pose, c, light, -hw, hw, -hw, hw,  hw, uv,  1f);
        GooRenderUtil.faceZ(pose, c, light, -hw, hw, -hw, hw, -hw, uv, NORMAL_NEG);
    }

    /**
     * Emits six faces of an axis-aligned cube centered at the origin with explicit ARGB color.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param hw the half-width of the cube
     * @param uv the UV texture rectangle
     */
    private static void emitColoredCubeFaces(PoseStack.Pose pose, VertexConsumer c, int light,
            int color, float hw, GooRenderUtil.UvRect uv) {
        shellFaceY(pose, c, light, color, -hw, hw,  hw, -hw, hw, uv,  1f);
        shellFaceY(pose, c, light, color, -hw, hw, -hw, -hw, hw, uv, NORMAL_NEG);
        shellFaceX(pose, c, light, color,  hw, -hw, hw, -hw, hw, uv,  1f);
        shellFaceX(pose, c, light, color, -hw, -hw, hw, -hw, hw, uv, NORMAL_NEG);
        shellFaceZ(pose, c, light, color, -hw, hw, -hw, hw,  hw, uv,  1f);
        shellFaceZ(pose, c, light, color, -hw, hw, -hw, hw, -hw, uv, NORMAL_NEG);
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

    /**
     * Spawns trail particles behind the blob: a viscous slime drip downward
     * and several radial fog puffs along the wake. Throttled to every other tick.
     *
     * @param pos the blob world position
     * @param vel the velocity vector
     * @param type the goo type
     * @param flight the flight instance for tick tracking
     */
    private static void spawnTrailParticles(Vec3 pos, Vec3 vel, GooType type,
            BlobFlightManager.BlobFlight flight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return; }
        if (flight.ticksElapsed % PARTICLE_TICK_INTERVAL != 0) { return; }

        int color = type.getColor();
        spawnDripParticle(mc, pos, vel, color);
        spawnFogParticles(mc, pos, vel, color);
    }

    /**
     * Spawns a single slime drip particle with gentle downward velocity.
     *
     * @param mc the Minecraft client instance
     * @param pos the blob world position
     * @param vel the velocity vector
     * @param color the RGB color of the goo type
     */
    private static void spawnDripParticle(Minecraft mc, Vec3 pos, Vec3 vel, int color) {
        ColorParticleOption dripOption = ColorParticleOption.create(
                GooParticles.GOO_DRIP.get(), color | OPAQUE_BLACK);
        mc.level.addParticle(dripOption,
                pos.x + randomOffset(), pos.y + randomOffset(), pos.z + randomOffset(),
                vel.x * DRIP_VEL_SCALE, DRIP_DOWN_VEL, vel.z * DRIP_VEL_SCALE);
    }

    /**
     * Spawns 2-4 fog puff particles behind the blob, suppressed when close to the camera.
     *
     * @param mc the Minecraft client instance
     * @param pos the blob world position
     * @param vel the velocity vector
     * @param color the RGB color of the goo type
     */
    private static void spawnFogParticles(Minecraft mc, Vec3 pos, Vec3 vel, int color) {
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        if (pos.distanceToSqr(camPos) <= FOG_MIN_DIST_SQ) { return; }

        ColorParticleOption fogOption = ColorParticleOption.create(
                GooParticles.GOO_FOG.get(), color | OPAQUE_BLACK);
        int fogCount = FOG_BASE_COUNT + ThreadLocalRandom.current().nextInt(FOG_RANDOM_RANGE);
        for (int i = 0; i < fogCount; i++) {
            emitSingleFogPuff(mc, fogOption, pos, vel);
        }
    }

    /**
     * Emits one fog puff particle at a jittered position behind the blob.
     *
     * @param mc the Minecraft client instance
     * @param fogOption the color particle option for fog
     * @param pos the blob world position
     * @param vel the velocity vector
     */
    private static void emitSingleFogPuff(Minecraft mc, ColorParticleOption fogOption,
            Vec3 pos, Vec3 vel) {
        mc.level.addParticle(fogOption,
                pos.x - vel.x * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                pos.y - vel.y * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                pos.z - vel.z * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                randomOffset() * FOG_VEL_JITTER, randomOffset() * FOG_VEL_JITTER, randomOffset() * FOG_VEL_JITTER);
    }

    /**
     * Small random offset for particle position jitter.
     *
     * @return a small random offset value
     */
    private static double randomOffset() {
        return (ThreadLocalRandom.current().nextDouble() - OFFSET_HALF) * OFFSET_SCALE;
    }

    // --- Shell face helpers (colored variants of GooRenderUtil.faceX/Y/Z) ---

    /**
     * Y-axis shell face with explicit ARGB color.
     * Winding direction is chosen based on the normal sign.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y normal component
     */
    private static void shellFaceY(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y, float z0, float z1, GooRenderUtil.UvRect uv, float ny) {
        if (ny > 0) {
            shellFaceYPositive(pose, c, light, color, x0, x1, y, z0, z1, uv, ny);
        } else {
            shellFaceYNegative(pose, c, light, color, x0, x1, y, z0, z1, uv, ny);
        }
    }

    /**
     * Emits a positive-normal Y-axis shell face (top face, CCW winding from above).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y normal component
     */
    private static void shellFaceYPositive(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y, float z0, float z1, GooRenderUtil.UvRect uv, float ny) {
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z0, uv.u0(), uv.v0(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z1, uv.u0(), uv.v1(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z1, uv.u1(), uv.v1(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z0, uv.u1(), uv.v0(), 0f, ny, 0f);
    }

    /**
     * Emits a negative-normal Y-axis shell face (bottom face, CW winding from above).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y normal component
     */
    private static void shellFaceYNegative(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y, float z0, float z1, GooRenderUtil.UvRect uv, float ny) {
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z0, uv.u1(), uv.v0(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z1, uv.u1(), uv.v1(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z1, uv.u0(), uv.v1(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z0, uv.u0(), uv.v0(), 0f, ny, 0f);
    }

    /**
     * X-axis shell face with explicit ARGB color.
     * Winding direction is chosen based on the normal sign.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X normal component
     */
    private static void shellFaceX(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x, float y0, float y1, float z0, float z1, GooRenderUtil.UvRect uv, float nx) {
        if (nx > 0) {
            shellFaceXPositive(pose, c, light, color, x, y0, y1, z0, z1, uv, nx);
        } else {
            shellFaceXNegative(pose, c, light, color, x, y0, y1, z0, z1, uv, nx);
        }
    }

    /**
     * Emits a positive-normal X-axis shell face (east face).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X normal component
     */
    private static void shellFaceXPositive(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x, float y0, float y1, float z0, float z1, GooRenderUtil.UvRect uv, float nx) {
        GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z1, uv.u1(), uv.v0(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z1, uv.u1(), uv.v1(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z0, uv.u0(), uv.v1(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z0, uv.u0(), uv.v0(), nx, 0f, 0f);
    }

    /**
     * Emits a negative-normal X-axis shell face (west face).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X normal component
     */
    private static void shellFaceXNegative(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x, float y0, float y1, float z0, float z1, GooRenderUtil.UvRect uv, float nx) {
        GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z0, uv.u1(), uv.v0(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z0, uv.u1(), uv.v1(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z1, uv.u0(), uv.v1(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z1, uv.u0(), uv.v0(), nx, 0f, 0f);
    }

    /**
     * Z-axis shell face with explicit ARGB color.
     * Winding direction is chosen based on the normal sign.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z the Z coordinate
     * @param uv the UV texture rectangle
     * @param nz the Z normal component
     */
    private static void shellFaceZ(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y0, float y1, float z, GooRenderUtil.UvRect uv, float nz) {
        if (nz > 0) {
            shellFaceZPositive(pose, c, light, color, x0, x1, y0, y1, z, uv, nz);
        } else {
            shellFaceZNegative(pose, c, light, color, x0, x1, y0, y1, z, uv, nz);
        }
    }

    /**
     * Emits a positive-normal Z-axis shell face (south face).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z the Z coordinate
     * @param uv the UV texture rectangle
     * @param nz the Z normal component
     */
    private static void shellFaceZPositive(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y0, float y1, float z, GooRenderUtil.UvRect uv, float nz) {
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y1, z, uv.u1(), uv.v0(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y0, z, uv.u1(), uv.v1(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y0, z, uv.u0(), uv.v1(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y1, z, uv.u0(), uv.v0(), 0f, 0f, nz);
    }

    /**
     * Emits a negative-normal Z-axis shell face (north face).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z the Z coordinate
     * @param uv the UV texture rectangle
     * @param nz the Z normal component
     */
    private static void shellFaceZNegative(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y0, float y1, float z, GooRenderUtil.UvRect uv, float nz) {
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y1, z, uv.u0(), uv.v0(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y0, z, uv.u0(), uv.v1(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y0, z, uv.u1(), uv.v1(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y1, z, uv.u1(), uv.v0(), 0f, 0f, nz);
    }
}
