package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
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
 * Renders the crucible's liquid level surface.
 */
public class CrucibleBlockEntityRenderer
        implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderState> {

    /** Block atlas texture path for render types that need the stitched atlas. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    // -- Liquid level constants --

    /** Basin interior X/Z bounds (inside the 2-pixel walls). */
    private static final float LIQUID_MIN_XZ = 2f / 16f;
    private static final float LIQUID_MAX_XZ = 14f / 16f;
    /** Liquid surface Y range. */
    private static final float LIQUID_MIN_Y = 13f / 16f;
    private static final float LIQUID_MAX_Y = 15f / 16f;
    /**
     * Volume at which the logarithmic curve reaches ~1.0 (full basin).
     * Tunable: higher values make the curve more gradual.
     */
    static final int LIQUID_LOG_CAP = 64_000;

    // -- Color constants --

    /** Maximum alpha channel value (1 byte). */
    private static final int MAX_ALPHA = 255;
    /** Byte mask for clamping to [0, 255]. */
    private static final int BYTE_MASK = 0xFF;
    /** RGB channel mask: keeps RGB, clears alpha byte. */
    private static final int RGB_MASK = 0x00FFFFFF;
    /** Bit shift for the alpha channel in ARGB format. */
    private static final int ALPHA_SHIFT = 24;

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
        extractPoolState(be, state);
    }

    /**
     * Copies pool volumes and crossfade dominant-type fields from the block entity.
     *
     * @param be the crucible block entity
     * @param state the render state to populate
     */
    private static void extractPoolState(CrucibleBlockEntity be, CrucibleRenderState state) {
        state.poolVolume = be.getPoolVolume();
        state.reservoirVolume = be.getReservoir().totalVolume();
        extractCrossfade(be, state);
    }

    /**
     * Ticks the dominant-type fader and copies crossfade fields to the render state.
     * @param be the crucible block entity
     * @param state the render state to populate
     */
    private static void extractCrossfade(CrucibleBlockEntity be, CrucibleRenderState state) {
        if (be.getLevel() != null) {
            be.dominantTypeFader.tick(
                be.getReservoir().largestType(), be.getLevel().getGameTime());
        }
        state.dominantType = be.dominantTypeFader.getShownType();
        state.outgoingType = be.dominantTypeFader.getOutgoingType();
        state.crossfadeAlpha = be.dominantTypeFader.getCrossfadeAlpha();
    }

    @Override
    public void submit(CrucibleRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        submitLiquidLevel(poseStack, nodeCollector, state);
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
        int totalGoo = state.poolVolume + state.reservoirVolume;
        if (totalGoo <= 0 || state.dominantType == null) { return; }

        float fillFraction = computeLogFill(totalGoo, LIQUID_LOG_CAP);
        float surfaceY = LIQUID_MIN_Y + fillFraction * (LIQUID_MAX_Y - LIQUID_MIN_Y);
        submitLiquidQuads(poseStack, nodeCollector, state, surfaceY);
    }

    /**
     * Submits one or two liquid quads depending on whether a crossfade is active.
     * During crossfade, the outgoing type fades out while the incoming type fades in.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the crucible render state (dominantType must be non-null)
     * @param surfaceY the computed liquid surface Y height
     */
    private static void submitLiquidQuads(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CrucibleRenderState state,
            float surfaceY) {
        int light = state.lightCoords;
        if (state.outgoingType != null) {
            submitCrossfadeQuads(poseStack, nodeCollector, state, surfaceY, light);
        } else {
            submitLiquidQuad(poseStack, nodeCollector, state.dominantType,
                surfaceY, light, 1f);
        }
    }

    /**
     * Submits two overlapping liquid quads for a crossfade transition:
     * the outgoing type fading out and the incoming type fading in.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the crucible render state with crossfade fields
     * @param surfaceY the computed liquid surface Y height
     * @param light the packed light value
     */
    private static void submitCrossfadeQuads(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CrucibleRenderState state,
            float surfaceY, int light) {
        float outAlpha = 1f - state.crossfadeAlpha;
        submitLiquidQuad(poseStack, nodeCollector, state.outgoingType,
            surfaceY, light, outAlpha);
        submitLiquidQuad(poseStack, nodeCollector, state.dominantType,
            surfaceY, light, state.crossfadeAlpha);
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
        int color = packArgb(alpha);
        nodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, consumer) -> emitLiquidSurface(
                pose, consumer, light, color, surfaceY, sprite)
        );
    }

    /**
     * Packs an alpha fraction [0, 1] into a full-white ARGB int (0xAARRGGBB).
     *
     * @param alpha the alpha transparency [0, 1]
     * @return the packed ARGB color with white RGB channels
     */
    private static int packArgb(float alpha) {
        int a = (int) (alpha * MAX_ALPHA) & BYTE_MASK;
        return RGB_MASK | (a << ALPHA_SHIFT);
    }

    /**
     * Emits a single liquid surface quad using the basin interior bounds.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param light the packed light value
     * @param color the packed ARGB color
     * @param surfaceY the liquid surface Y height
     * @param sprite the fluid texture atlas sprite
     */
    private static void emitLiquidSurface(PoseStack.Pose pose, VertexConsumer consumer,
            int light, int color, float surfaceY, TextureAtlasSprite sprite) {
        GooRenderUtil.liquidSurface(
            pose, consumer, light, color,
            LIQUID_MIN_XZ, LIQUID_MIN_XZ, LIQUID_MAX_XZ, LIQUID_MAX_XZ,
            surfaceY, sprite.getU0(), sprite.getU1(),
            sprite.getV0(), sprite.getV1());
    }

    /**
     * Computes a logarithmic fill fraction in [0, 1] from volume and cap.
     * Front-loaded: small volumes fill quickly, large volumes approach 1 slowly.
     *
     * @param volume the volume in microblobs
     * @param cap the log curve saturation cap
     * @return the computed logFill
     */
    static float computeLogFill(int volume, int cap) {
        if (volume <= 0) { return 0f; }
        if (volume >= cap) { return 1f; }
        return (float) (Math.log(1.0 + volume) / Math.log(1.0 + cap));
    }

}
