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

        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = mc.level.getGameTime() + partialTick;

        for (BlobFlightManager.BlobFlight flight : flights) {
            renderFlight(poseStack, buffers, camera, flight, partialTick, gameTime);
        }
    }

    /**
     * Renders a single flight: core, shell, tail, and particles.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param camera the render camera
     * @param flight the flight
     * @param partialTick the partial tick for interpolation
     * @param gameTime the level game time in ticks
     */
    private static void renderFlight(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            Camera camera, BlobFlightManager.BlobFlight flight,
            float partialTick, float gameTime) {
        Vec3 pos = flight.getPosition(partialTick);
        Vec3 vel = flight.getVelocity(partialTick);

        double ox = pos.x - camera.position().x;
        double oy = pos.y - camera.position().y;
        double oz = pos.z - camera.position().z;

        poseStack.pushPose();
        poseStack.translate(ox, oy, oz);

        int light = FULL_BRIGHT;

        renderCore(poseStack, buffers, flight.gooType, light, gameTime);
        renderShell(poseStack, buffers, flight.gooType, light);
        renderTail(poseStack, buffers, flight.gooType, vel, light, gameTime);

        poseStack.popPose();

        spawnTrailParticles(pos, vel, flight.gooType, flight);
    }

    /**
     * Core: opaque fluid cuboid with sin-pulsing width.
     * Uses entitySolid for fully opaque rendering.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param light the packed light value
     * @param gameTime the level game time in ticks
     */
    private static void renderCore(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, int light, float gameTime) {
        float pulse = 1.0f + CORE_PULSE_AMP * Mth.sin(gameTime * CORE_PULSE_SPEED);
        float hw = CORE_HW * pulse;

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        GooRenderUtil.UvRect uv = new GooRenderUtil.UvRect(
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());

        VertexConsumer c = buffers.getBuffer(RenderTypes.entitySolid(BLOCK_ATLAS_TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        // 6 faces of the core cube
        GooRenderUtil.faceY(pose, c, light, -hw, hw,  hw, -hw, hw, uv,  1f);  // top
        GooRenderUtil.faceY(pose, c, light, -hw, hw, -hw, -hw, hw, uv, NORMAL_NEG);  // bottom
        GooRenderUtil.faceX(pose, c, light,  hw, -hw, hw, -hw, hw, uv,  1f);  // east
        GooRenderUtil.faceX(pose, c, light, -hw, -hw, hw, -hw, hw, uv, NORMAL_NEG);  // west
        GooRenderUtil.faceZ(pose, c, light, -hw, hw, -hw, hw,  hw, uv,  1f);  // south
        GooRenderUtil.faceZ(pose, c, light, -hw, hw, -hw, hw, -hw, uv, NORMAL_NEG);  // north
    }

    /**
     * Shell: larger translucent cuboid with the goo type's color tint.
     * Gives the blob a slime-like outer glow.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param light the packed light value
     */
    private static void renderShell(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, int light) {
        float hw = SHELL_HW;
        int baseColor = type.getColor();
        int color = (SHELL_ALPHA << ALPHA_SHIFT) | (baseColor & RGB_MASK);

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        GooRenderUtil.UvRect uv = new GooRenderUtil.UvRect(
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());

        VertexConsumer c = buffers.getBuffer(RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        // 6 faces of the shell cube, with translucent color tint
        shellFaceY(pose, c, light, color, -hw, hw,  hw, -hw, hw, uv,  1f);  // top
        shellFaceY(pose, c, light, color, -hw, hw, -hw, -hw, hw, uv, NORMAL_NEG);  // bottom
        shellFaceX(pose, c, light, color,  hw, -hw, hw, -hw, hw, uv,  1f);  // east
        shellFaceX(pose, c, light, color, -hw, -hw, hw, -hw, hw, uv, NORMAL_NEG);  // west
        shellFaceZ(pose, c, light, color, -hw, hw, -hw, hw,  hw, uv,  1f);  // south
        shellFaceZ(pose, c, light, color, -hw, hw, -hw, hw, -hw, uv, NORMAL_NEG);  // north
    }

    /**
     * Tail: two crossing quads extending behind the blob along the velocity vector.
     * Gives the projectile a streaking motion feel.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param velocity the velocity direction vector
     * @param light the packed light value
     * @param gameTime the level game time in ticks
     */
    private static void renderTail(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, Vec3 velocity, int light, float gameTime) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        int baseColor = type.getColor();
        // Tail is semi-transparent
        int tailColor = (TAIL_ALPHA << ALPHA_SHIFT) | (baseColor & RGB_MASK);

        VertexConsumer c = buffers.getBuffer(RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE));

        // Build a local coordinate system from the velocity vector
        Vec3 forward = velocity;
        // Find a vector not parallel to forward for cross product
        Vec3 up = (Math.abs(forward.y) < UP_THRESHOLD) ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = forward.cross(up).normalize();
        Vec3 realUp = right.cross(forward).normalize();

        // Tail extends backward from the blob center along -forward
        Vec3 tailEnd = forward.scale(-TAIL_LENGTH);

        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();

        // Quad 1: along right axis
        emitTailQuad(pose, c, light, tailColor, tailEnd, right, TAIL_HW, u0, u1, v0, v1);
        // Quad 2: along up axis (perpendicular crossing quad)
        emitTailQuad(pose, c, light, tailColor, tailEnd, realUp, TAIL_HW, u0, u1, v0, v1);

        poseStack.popPose();
    }

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
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param v0 the minimum V texture coordinate
     * @param v1 the maximum V texture coordinate
     */
    private static void emitTailQuad(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            Vec3 tailEnd, Vec3 axis, float hw, float u0, float u1, float v0, float v1) {
        // Four corners: two at origin +-axis*hw, two at tailEnd +-axis*hw
        float ax = (float) (axis.x * hw);
        float ay = (float) (axis.y * hw);
        float az = (float) (axis.z * hw);
        float ex = (float) tailEnd.x;
        float ey = (float) tailEnd.y;
        float ez = (float) tailEnd.z;

        // Normal: cross of tail direction with axis
        Vec3 normal = tailEnd.normalize().cross(axis);
        float nx = (float) normal.x;
        float ny = (float) normal.y;
        float nz = (float) normal.z;

        // Front face
        GooRenderUtil.vertexColored(pose, c, light, color,  ax,  ay,  az, u0, v0, nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, -ax, -ay, -az, u1, v0, nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, ex - ax, ey - ay, ez - az, u1, v1, nx, ny, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, ex + ax, ey + ay, ez + az, u0, v1, nx, ny, nz);

        // Back face (reverse winding)
        GooRenderUtil.vertexColored(pose, c, light, color, ex + ax, ey + ay, ez + az, u0, v1, -nx, -ny, -nz);
        GooRenderUtil.vertexColored(pose, c, light, color, ex - ax, ey - ay, ez - az, u1, v1, -nx, -ny, -nz);
        GooRenderUtil.vertexColored(pose, c, light, color, -ax, -ay, -az, u1, v0, -nx, -ny, -nz);
        GooRenderUtil.vertexColored(pose, c, light, color,  ax,  ay,  az, u0, v0, -nx, -ny, -nz);
    }

    /**
     * Spawns trail particles behind the blob: a viscous slime drip downward
     * and several radial fog puffs along the wake. Throttled to every other tick.
     *
     * @param pos the block position
     * @param vel the vel
     * @param type the goo type
     * @param flight the flight
     */
    private static void spawnTrailParticles(Vec3 pos, Vec3 vel, GooType type,
            BlobFlightManager.BlobFlight flight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return; }

        // Only spawn every other tick to keep particle count reasonable
        if (flight.ticksElapsed % PARTICLE_TICK_INTERVAL != 0) { return; }

        int color = type.getColor();

        // Slime drip: gentle downward velocity, gravity handles the rest
        ColorParticleOption dripOption = ColorParticleOption.create(
                GooParticles.GOO_DRIP.get(), color | OPAQUE_BLACK);
        mc.level.addParticle(dripOption,
                pos.x + randomOffset(), pos.y + randomOffset(), pos.z + randomOffset(),
                vel.x * DRIP_VEL_SCALE, DRIP_DOWN_VEL, vel.z * DRIP_VEL_SCALE);

        // Fog puffs: 2-4 radial gradient billboards behind the blob.
        // Suppressed near the player so fog doesn't obscure the throw origin.
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        if (pos.distanceToSqr(camPos) > FOG_MIN_DIST_SQ) { // > 3 blocks
            ColorParticleOption fogOption = ColorParticleOption.create(
                    GooParticles.GOO_FOG.get(), color | OPAQUE_BLACK);
            int fogCount = FOG_BASE_COUNT + ThreadLocalRandom.current().nextInt(FOG_RANDOM_RANGE);
            for (int i = 0; i < fogCount; i++) {
                mc.level.addParticle(fogOption,
                        pos.x - vel.x * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                        pos.y - vel.y * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                        pos.z - vel.z * FOG_POS_SCALE + randomOffset() * FOG_SPREAD,
                        randomOffset() * FOG_VEL_JITTER, randomOffset() * FOG_VEL_JITTER, randomOffset() * FOG_VEL_JITTER);
            }
        }
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
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z0, uv.u0(), uv.v0(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z1, uv.u0(), uv.v1(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z1, uv.u1(), uv.v1(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z0, uv.u1(), uv.v0(), 0f, ny, 0f);
        } else {
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z0, uv.u1(), uv.v0(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z1, uv.u1(), uv.v1(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z1, uv.u0(), uv.v1(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z0, uv.u0(), uv.v0(), 0f, ny, 0f);
        }
    }

    /**
     * X-axis shell face with explicit ARGB color.
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
            GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z1, uv.u1(), uv.v0(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z1, uv.u1(), uv.v1(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z0, uv.u0(), uv.v1(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z0, uv.u0(), uv.v0(), nx, 0f, 0f);
        } else {
            GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z0, uv.u1(), uv.v0(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z0, uv.u1(), uv.v1(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z1, uv.u0(), uv.v1(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z1, uv.u0(), uv.v0(), nx, 0f, 0f);
        }
    }

    /**
     * Z-axis shell face with explicit ARGB color.
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
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y1, z, uv.u1(), uv.v0(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y0, z, uv.u1(), uv.v1(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y0, z, uv.u0(), uv.v1(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y1, z, uv.u0(), uv.v0(), 0f, 0f, nz);
        } else {
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y1, z, uv.u0(), uv.v0(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y0, z, uv.u0(), uv.v1(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y0, z, uv.u1(), uv.v1(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y1, z, uv.u1(), uv.v0(), 0f, 0f, nz);
        }
    }
}
