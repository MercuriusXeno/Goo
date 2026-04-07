package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the crucible's internal mechanisms: fuel platform, support chains,
 * blaze rod, and liquid level. Platform position and blaze rod visibility
 * are driven by the enabled state (redstone gating). Liquid level uses a
 * logarithmic fill curve for front-loaded visual response.
 */
public class CrucibleBlockEntityRenderer
        implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderState> {

    /** Block atlas texture path for render types that need the stitched atlas. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Vanilla iron chain block texture (6x16 usable area in a 16x16 file). */
    private static final Identifier CHAIN_TEXTURE =
        Identifier.withDefaultNamespace("textures/block/iron_chain.png");

    /** Platform texture: custom dark crucible fuel platform. */
    private static final Identifier PLATFORM_TEXTURE =
        Identifier.fromNamespaceAndPath("goo", "textures/block/crucible_fuel_platform.png");

    /** Blaze rod texture: custom crucible fuel rod. */
    private static final Identifier BLAZE_ROD_TEXTURE =
        Identifier.fromNamespaceAndPath("goo", "textures/block/crucible_blaze_rod.png");

    // -- Geometry constants (pixel space, /16 for block coords) --

    /** Platform X/Z bounds in pixels. */
    private static final float PLAT_MIN_X = 6f / 16f;
    private static final float PLAT_MAX_X = 10f / 16f;
    private static final float PLAT_MIN_Z = 6f / 16f;
    private static final float PLAT_MAX_Z = 10f / 16f;
    /** Platform thickness: 1 pixel. */
    private static final float PLAT_THICKNESS = 1f / 16f;

    /** Basin bottom Y (where chains anchor at the top). */
    private static final float BASIN_Y = 9f / 16f;

    // -- Blaze Rod --

    /** Full rod height when platform is at floor level. */
    private static final float ROD_FULL_HEIGHT = BASIN_Y - PLAT_THICKNESS;

    /** Chain half-width in block coords (3/8 pixel: quarter of 3px texture strip). */
    private static final float CHAIN_HW = 0.375f / 16f;
    /** Chain tile height in block coords (4px: quarter of 16px texture height). */
    private static final float CHAIN_TILE = 4f / 16f;

    /** Blaze rod half-width in block coords (1 pixel). */
    private static final float ROD_HW = 1f / 16f;
    /** Blaze rod center X (platform center). */
    private static final float ROD_CX = 8f / 16f;
    /** Blaze rod center Z (platform center). */
    private static final float ROD_CZ = 8f / 16f;

    /** Chain anchor X positions (inset by CHAIN_HW so outer edges are flush). */
    private static final float[] CHAIN_X = {
        6.375f / 16f, 9.625f / 16f, 6.375f / 16f, 9.625f / 16f
    };
    /** Chain anchor Z positions (inset by CHAIN_HW so outer edges are flush). */
    private static final float[] CHAIN_Z = {
        6.375f / 16f, 6.375f / 16f, 9.625f / 16f, 9.625f / 16f
    };

    // -- Liquid level constants --

    /** Basin interior X/Z bounds (inside the 1-pixel walls). */
    private static final float LIQUID_MIN_XZ = 1f / 16f;
    private static final float LIQUID_MAX_XZ = 15f / 16f;
    /** Liquid surface Y range: basin floor to just below rim. */
    private static final float LIQUID_MIN_Y = 10f / 16f;
    private static final float LIQUID_MAX_Y = 15f / 16f;
    /**
     * Volume at which the logarithmic curve reaches ~1.0 (full basin).
     * Tunable: higher values make the curve more gradual.
     */
    static final long LIQUID_LOG_CAP = 64_000L;

    // -- Texture UV grid (pixel positions in a 16x16 texture) --

    /** UV coordinate at 1px in a 16px texture. */
    private static final float UV_1 = 1f / 16f;
    /** UV coordinate at 2px in a 16px texture. */
    private static final float UV_2 = 2f / 16f;
    /** UV coordinate at 3px in a 16px texture. */
    private static final float UV_3 = 3f / 16f;
    /** UV coordinate at 4px in a 16px texture. */
    private static final float UV_4 = 4f / 16f;
    /** UV coordinate at 6px in a 16px texture. */
    private static final float UV_6 = 6f / 16f;
    /** UV coordinate at 8px in a 16px texture. */
    private static final float UV_8 = 8f / 16f;
    /** UV coordinate at 10px in a 16px texture. */
    private static final float UV_10 = 10f / 16f;

    // -- Color constants --

    /** Maximum alpha channel value (1 byte). */
    private static final int MAX_ALPHA = 255;
    /** Byte mask for clamping to [0, 255]. */
    private static final int BYTE_MASK = 0xFF;
    /** RGB channel mask: keeps RGB, clears alpha byte. */
    private static final int RGB_MASK = 0x00FFFFFF;
    /** Bit shift for the alpha channel in ARGB format. */
    private static final int ALPHA_SHIFT = 24;

    /** Negative unit for normal vector components. */
    private static final float NEG_UNIT = -1f;

    // -- Platform UV regions (8x8 area in top-left of 16x16 texture) --

    private static final GooRenderUtil.UvRect PLAT_UV_TOP = new GooRenderUtil.UvRect(0f, 0f, UV_4, UV_4);
    private static final GooRenderUtil.UvRect PLAT_UV_BOTTOM = new GooRenderUtil.UvRect(0f, UV_4, UV_4, UV_8);
    private static final GooRenderUtil.UvRect PLAT_UV_EAST = new GooRenderUtil.UvRect(UV_4, UV_2, UV_8, UV_3);
    private static final GooRenderUtil.UvRect PLAT_UV_NORTH = new GooRenderUtil.UvRect(UV_4, UV_1, UV_8, UV_2);
    private static final GooRenderUtil.UvRect PLAT_UV_WEST = new GooRenderUtil.UvRect(UV_4, 0f, UV_8, UV_1);
    private static final GooRenderUtil.UvRect PLAT_UV_SOUTH = new GooRenderUtil.UvRect(UV_4, UV_3, UV_8, UV_4);

    // -- Rod UV regions (16x16 texture, cap/base are fixed) --

    private static final GooRenderUtil.UvRect ROD_UV_TOP = new GooRenderUtil.UvRect(0f, UV_8, UV_2, UV_10);
    private static final GooRenderUtil.UvRect ROD_UV_BOTTOM = new GooRenderUtil.UvRect(UV_8, 0f, UV_10, UV_2);
    /** V coordinate for the bottom of rod side textures. */
    private static final float ROD_V_BOTTOM = UV_8;

    /**
     * Creates a crucible BER. Context is unused.
     *
     * @param context the renderer provider context
     */
    public CrucibleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // No resources needed from context
    }

    @Override
    public CrucibleRenderState createRenderState() {
        return new CrucibleRenderState();
    }

    @Override
    public void extractRenderState(CrucibleBlockEntity be, CrucibleRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.platformY = be.getPlatformY();
        state.prevPlatformY = be.getPrevPlatformY();
        state.partialTick = partialTick;
        state.hasFuelRod = !be.getFuelRod().isEmpty();
        state.fuelFraction = be.fuelFraction();
        state.poolVolume = be.getPoolVolume();
        state.reservoirVolume = be.getReservoir().totalVolume();
        be.tickDominantType();
        state.dominantType = be.getShownDominantType();
        state.outgoingType = be.getOutgoingDominantType();
        state.crossfadeAlpha = be.getCrossfadeAlpha();
    }

    @Override
    public void submit(CrucibleRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        float platformY = lerpPlatformY(state);

        submitPlatform(poseStack, nodeCollector, platformY, state.lightCoords);
        submitChains(poseStack, nodeCollector, platformY, state.lightCoords);
        if (state.hasFuelRodVisible()) {
            submitBlazeRod(poseStack, nodeCollector, platformY,
                state.fuelFraction, state.lightCoords);
        }
        submitLiquidLevel(poseStack, nodeCollector, state);
    }

    /**
     * Linearly interpolates between prevPlatformY and platformY using partialTick.
     * Standard Minecraft sub-tick interpolation: prev + (current - prev) * partialTick.
     *
     * @param state the block state
     * @return the interpolated value
     */
    private static float lerpPlatformY(CrucibleRenderState state) {
        return state.prevPlatformY
            + (state.platformY - state.prevPlatformY) * state.partialTick;
    }

    // -- Liquid level --

    /**
     * Submits the liquid surface quad(s). Renders two during crossfade for smooth blending.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    private static void submitLiquidLevel(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CrucibleRenderState state) {
        long totalGoo = state.poolVolume + state.reservoirVolume;
        if (totalGoo <= 0 || state.dominantType == null) { return; }

        float fillFraction = computeLogFill(totalGoo, LIQUID_LOG_CAP);
        float surfaceY = LIQUID_MIN_Y + fillFraction * (LIQUID_MAX_Y - LIQUID_MIN_Y);
        int light = state.lightCoords;

        if (state.outgoingType != null) {
            float outAlpha = 1f - state.crossfadeAlpha;
            submitLiquidQuad(poseStack, nodeCollector, state.outgoingType,
                surfaceY, light, outAlpha);
            submitLiquidQuad(poseStack, nodeCollector, state.dominantType,
                surfaceY, light, state.crossfadeAlpha);
        } else {
            submitLiquidQuad(poseStack, nodeCollector, state.dominantType,
                surfaceY, light, 1f);
        }
    }

    /**
     * Submits a single liquid surface quad for one goo type at the given alpha.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param type the goo type
     * @param surfaceY the surface Y height
     * @param light the packed light value
     * @param alpha the alpha transparency [0, 1]
     */
    private static void submitLiquidQuad(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, GooType type,
            float surfaceY, int light, float alpha) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        int a = (int) (alpha * MAX_ALPHA) & BYTE_MASK;
        int color = RGB_MASK | (a << ALPHA_SHIFT);
        nodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, consumer) -> GooRenderUtil.liquidSurface(
                pose, consumer, light, color,
                LIQUID_MIN_XZ, LIQUID_MIN_XZ, LIQUID_MAX_XZ, LIQUID_MAX_XZ,
                surfaceY, sprite.getU0(), sprite.getU1(),
                sprite.getV0(), sprite.getV1())
        );
    }

    /**
     * Computes a logarithmic fill fraction in [0, 1] from volume and cap.
     * Front-loaded: small volumes fill quickly, large volumes approach 1 slowly.
     *
     * @param volume the volume in microblobs
     * @param cap the log curve saturation cap
     * @return the computed logFill
     */
    static float computeLogFill(long volume, long cap) {
        if (volume <= 0) { return 0f; }
        if (volume >= cap) { return 1f; }
        return (float) (Math.log(1.0 + volume) / Math.log(1.0 + cap));
    }

    // -- Platform --

    /**
     * Submits the 4x1x4 platform box at the given Y position.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param bottomY the bottom Y position
     * @param light the packed light value
     */
    private static void submitPlatform(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, float bottomY, int light) {
        float topY = bottomY + PLAT_THICKNESS;
        nodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entitySolid(PLATFORM_TEXTURE),
            (pose, consumer) -> renderPlatformBox(pose, consumer, light,
                PLAT_MIN_X, bottomY, PLAT_MIN_Z,
                PLAT_MAX_X, topY, PLAT_MAX_Z)
        );
    }

    /**
     * Renders the platform box: 6 faces with UV rects from the sprite sheet.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     */
    private static void renderPlatformBox(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float y0, float z0, float x1, float y1, float z1) {
        GooRenderUtil.faceY(pose, c, light, x0, x1, y1, z0, z1, PLAT_UV_TOP, 1f);
        GooRenderUtil.faceY(pose, c, light, x0, x1, y0, z0, z1, PLAT_UV_BOTTOM, NEG_UNIT);
        GooRenderUtil.faceX(pose, c, light, x1, y0, y1, z0, z1, PLAT_UV_EAST, 1f);
        GooRenderUtil.faceX(pose, c, light, x0, y0, y1, z0, z1, PLAT_UV_WEST, NEG_UNIT);
        GooRenderUtil.faceZ(pose, c, light, x0, x1, y0, y1, z0, PLAT_UV_NORTH, NEG_UNIT);
        GooRenderUtil.faceZ(pose, c, light, x0, x1, y0, y1, z1, PLAT_UV_SOUTH, 1f);
    }

    // -- Chains --

    /**
     * Submits all four chain crosses between the platform and basin.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param platformY the platform Y position
     * @param light the packed light value
     */
    private static void submitChains(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, float platformY, int light) {
        float chainBottom = platformY + PLAT_THICKNESS;
        float chainTop = BASIN_Y;
        if (chainBottom >= chainTop) { return; }

        nodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityCutout(CHAIN_TEXTURE),
            (pose, consumer) -> renderAllChains(
                pose, consumer, light, chainBottom, chainTop)
        );
    }

    /**
     * Renders four chain crosses at the platform corners.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param light the packed light value
     * @param bottom whether to render the bottom cap
     * @param top whether to render the top cap
     */
    private static void renderAllChains(PoseStack.Pose pose, VertexConsumer consumer,
            int light, float bottom, float top) {
        for (int i = 0; i < CHAIN_X.length; i++) {
            renderChainCross(pose, consumer, light,
                CHAIN_X[i], CHAIN_Z[i], bottom, top);
        }
    }

    /**
     * Renders a single chain cross (two perpendicular quads) at the given X/Z center.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @param bottom whether to render the bottom cap
     * @param top whether to render the top cap
     */
    private static void renderChainCross(PoseStack.Pose pose, VertexConsumer consumer,
            int light, float cx, float cz, float bottom, float top) {
        renderChainQuadNS(pose, consumer, light, cx, cz, bottom, top);
        renderChainQuadEW(pose, consumer, light, cx, cz, bottom, top);
    }

    /**
     * Tiles NS chain quads with platform-anchored UVs.
     * Chains scroll upward with the platform; top links clip into the basin.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @param bottom whether to render the bottom cap
     * @param top whether to render the top cap
     */
    private static void renderChainQuadNS(PoseStack.Pose pose, VertexConsumer c,
            int light, float cx, float cz, float bottom, float top) {
        tileChainQuad(pose, c, light, cx, cz, bottom, top, 0f, UV_3, true);
    }

    /**
     * Tiles EW chain quads with platform-anchored UVs.
     * Same scrolling behavior as the NS quads.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @param bottom whether to render the bottom cap
     * @param top whether to render the top cap
     */
    private static void renderChainQuadEW(PoseStack.Pose pose, VertexConsumer c,
            int light, float cx, float cz, float bottom, float top) {
        tileChainQuad(pose, c, light, cx, cz, bottom, top, UV_3, UV_6, false);
    }

    /**
     * Tiles chain quads from bottom to top. Full tiles use v=0..1.
     * The topmost partial tile clips from the top: v=(1-fraction)..1.
     * Chain links scroll upward into the basin as the platform rises.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @param bottom whether to render the bottom cap
     * @param top whether to render the top cap
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param ns true for north-south orientation
     */
    private static void tileChainQuad(PoseStack.Pose pose, VertexConsumer c,
            int light, float cx, float cz, float bottom, float top,
            float u0, float u1, boolean ns) {
        float y = bottom;
        while (y < top) {
            float tileTop = Math.min(y + CHAIN_TILE, top);
            float fraction = (tileTop - y) / CHAIN_TILE;
            boolean isPartialTop = y + CHAIN_TILE > top;
            float vStart = isPartialTop ? (1f - fraction) : 0f;
            float vEnd = 1f;
            emitChainTile(pose, c, light, cx, cz, y, tileTop,
                u0, u1, vStart, vEnd, ns);
            y = tileTop;
        }
    }

    /**
     * Dispatches a single chain tile to NS or EW emitter.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @param bot the bot
     * @param top whether to render the top cap
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param vStart the vStart
     * @param vEnd the vEnd
     * @param ns true for north-south orientation
     */
    private static void emitChainTile(PoseStack.Pose pose, VertexConsumer c,
            int light, float cx, float cz, float bot, float top,
            float u0, float u1, float vStart, float vEnd, boolean ns) {
        if (ns) {
            emitNS(pose, c, light, cx, cz, bot, top, u0, u1, vStart, vEnd);
        } else {
            emitEW(pose, c, light, cx, cz, bot, top, u0, u1, vStart, vEnd);
        }
    }

    /**
     * Emits one NS chain tile (front + back) with explicit vStart and vEnd.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @param bot the bot
     * @param top whether to render the top cap
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param vStart the vStart
     * @param vEnd the vEnd
     */
    private static void emitNS(PoseStack.Pose pose, VertexConsumer c, int light,
            float cx, float cz, float bot, float top,
            float u0, float u1, float vStart, float vEnd) {
        GooRenderUtil.vertex(pose, c, light, cx - CHAIN_HW, top, cz, u1, vStart, 0f, 0f, 1f);
        GooRenderUtil.vertex(pose, c, light, cx - CHAIN_HW, bot, cz, u1, vEnd, 0f, 0f, 1f);
        GooRenderUtil.vertex(pose, c, light, cx + CHAIN_HW, bot, cz, u0, vEnd, 0f, 0f, 1f);
        GooRenderUtil.vertex(pose, c, light, cx + CHAIN_HW, top, cz, u0, vStart, 0f, 0f, 1f);

        GooRenderUtil.vertex(pose, c, light, cx + CHAIN_HW, top, cz, u0, vStart, 0f, 0f, NEG_UNIT);
        GooRenderUtil.vertex(pose, c, light, cx + CHAIN_HW, bot, cz, u0, vEnd, 0f, 0f, NEG_UNIT);
        GooRenderUtil.vertex(pose, c, light, cx - CHAIN_HW, bot, cz, u1, vEnd, 0f, 0f, NEG_UNIT);
        GooRenderUtil.vertex(pose, c, light, cx - CHAIN_HW, top, cz, u1, vStart, 0f, 0f, NEG_UNIT);
    }

    /**
     * Emits one EW chain tile (front + back) with explicit vStart and vEnd.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @param bot the bot
     * @param top whether to render the top cap
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param vStart the vStart
     * @param vEnd the vEnd
     */
    private static void emitEW(PoseStack.Pose pose, VertexConsumer c, int light,
            float cx, float cz, float bot, float top,
            float u0, float u1, float vStart, float vEnd) {
        GooRenderUtil.vertex(pose, c, light, cx, top, cz - CHAIN_HW, u0, vStart, 1f, 0f, 0f);
        GooRenderUtil.vertex(pose, c, light, cx, bot, cz - CHAIN_HW, u0, vEnd, 1f, 0f, 0f);
        GooRenderUtil.vertex(pose, c, light, cx, bot, cz + CHAIN_HW, u1, vEnd, 1f, 0f, 0f);
        GooRenderUtil.vertex(pose, c, light, cx, top, cz + CHAIN_HW, u1, vStart, 1f, 0f, 0f);

        GooRenderUtil.vertex(pose, c, light, cx, top, cz + CHAIN_HW, u1, vStart, NEG_UNIT, 0f, 0f);
        GooRenderUtil.vertex(pose, c, light, cx, bot, cz + CHAIN_HW, u1, vEnd, NEG_UNIT, 0f, 0f);
        GooRenderUtil.vertex(pose, c, light, cx, bot, cz - CHAIN_HW, u0, vEnd, NEG_UNIT, 0f, 0f);
        GooRenderUtil.vertex(pose, c, light, cx, top, cz - CHAIN_HW, u0, vStart, NEG_UNIT, 0f, 0f);
    }

    /**
     * Submits the blaze rod as a 2x2 pixel cuboid sitting on the platform.
     * Rod height is fuel-proportional: it sits on the platform and extends
     * upward by fuelFraction * ROD_FULL_HEIGHT. Side UVs recede from the
     * top as the rod shortens, keeping the bottom texture anchored.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param platformY the platform Y position
     * @param fuelFraction the fuel remaining fraction [0, 1]
     * @param light the packed light value
     */
    private static void submitBlazeRod(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, float platformY,
            float fuelFraction, int light) {
        float rodBottom = platformY + PLAT_THICKNESS;
        float rodHeight = fuelFraction * ROD_FULL_HEIGHT;
        if (rodHeight <= 0f) { return; }
        float rodTop = rodBottom + rodHeight;

        float heightFraction = rodHeight / ROD_FULL_HEIGHT;
        float vTop = ROD_V_BOTTOM - heightFraction * ROD_V_BOTTOM;

        nodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entitySolid(BLAZE_ROD_TEXTURE),
            (pose, consumer) -> renderRodBox(pose, consumer, light,
                ROD_CX - ROD_HW, rodBottom, ROD_CZ - ROD_HW,
                ROD_CX + ROD_HW, rodTop, ROD_CZ + ROD_HW, vTop)
        );
    }

    /**
     * Renders the blaze rod box: 6 faces with side UVs cropped from the top.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     * @param vTop the top V texture coordinate
     */
    private static void renderRodBox(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float y0, float z0, float x1, float y1, float z1,
            float vTop) {
        GooRenderUtil.faceY(pose, c, light, x0, x1, y1, z0, z1, ROD_UV_TOP, 1f);
        GooRenderUtil.faceY(pose, c, light, x0, x1, y0, z0, z1, ROD_UV_BOTTOM, NEG_UNIT);
        GooRenderUtil.faceX(pose, c, light, x1, y0, y1, z0, z1,
            new GooRenderUtil.UvRect(UV_4, vTop, UV_6, ROD_V_BOTTOM), 1f);
        GooRenderUtil.faceX(pose, c, light, x0, y0, y1, z0, z1,
            new GooRenderUtil.UvRect(0f, vTop, UV_2, ROD_V_BOTTOM), NEG_UNIT);
        GooRenderUtil.faceZ(pose, c, light, x0, x1, y0, y1, z0,
            new GooRenderUtil.UvRect(UV_2, vTop, UV_4, ROD_V_BOTTOM), NEG_UNIT);
        GooRenderUtil.faceZ(pose, c, light, x0, x1, y0, y1, z1,
            new GooRenderUtil.UvRect(UV_8, vTop, UV_6, ROD_V_BOTTOM), 1f);
    }

}
