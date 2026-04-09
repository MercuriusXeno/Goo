package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.model.CanisterGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Fluid surface, stream, and body-side rendering helpers for {@link HubBlockEntityRenderer}.
 * Extracted to keep the parent BER under the PMD method-count threshold.
 */
final class HubFluidRenderer {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Canister body side texture. */
    private static final Identifier CANISTER_SIDE =
        Identifier.fromNamespaceAndPath("goo", "textures/block/canister_side.png");

    /** Canister half-width: 2px. */
    private static final float HW = 2f / 16f;

    /** Top of lower gasket / bottom of body (y=3px). */
    private static final float BODY_BOT = 3f / 16f;

    /** Top of body / bottom of upper gasket (y=13px). */
    private static final float BODY_TOP = 13f / 16f;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    private static final float FLUID_INSET = 0.5f / 16f;

    /** Body side U range: 4px / 16px = 0.25. */
    private static final float BODY_U1 = 0.25f;

    /** Body side V range: 10px / 16px = 0.625. */
    private static final float BODY_V1 = 0.625f;

    /** Canister center positions in block coords (XZ), indexed by slot. */
    private static final float[][] CENTERS = {
        { 8f / 16f,  2f / 16f},   // slot 0 (N)
        {13f / 16f,  3f / 16f},   // slot 1 (NE)
        {14f / 16f,  8f / 16f},   // slot 2 (E)
        {13f / 16f, 13f / 16f},   // slot 3 (SE)
        { 8f / 16f, 14f / 16f},   // slot 4 (S)
        { 3f / 16f, 13f / 16f},   // slot 5 (SW)
        { 2f / 16f,  8f / 16f},   // slot 6 (W)
        { 3f / 16f,  3f / 16f},   // slot 7 (NW)
    };

    private HubFluidRenderer() {
    }

    // -- Body rendering --

    /**
     * Batches all canister body sides into a single draw call.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    static void submitBodies(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityCutout(CANISTER_SIDE),
            (pose, c) -> {
                for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
                    if (state.canisterPresent[i]) { renderBodySides(pose, c, light, i); }
                }
            });
    }

    /**
     * Renders the 4 side faces of a canister body at the given slot.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param slot the slot index
     */
    private static void renderBodySides(PoseStack.Pose pose, VertexConsumer c,
            int light, int slot) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
        float x0 = cx - HW;
        float x1 = cx + HW;
        float z0 = cz - HW;
        float z1 = cz + HW;
        CanisterGeometry.faceNorth(pose, c, light, x0, BODY_BOT, z0, x1, BODY_TOP, 0, BODY_U1, 0, BODY_V1);
        CanisterGeometry.faceSouth(pose, c, light, x0, BODY_BOT, z1, x1, BODY_TOP, 0, BODY_U1, 0, BODY_V1);
        CanisterGeometry.faceWest(pose, c, light, x0, BODY_BOT, z0, BODY_TOP, z1, 0, BODY_U1, 0, BODY_V1);
        CanisterGeometry.faceEast(pose, c, light, x1, BODY_BOT, z0, BODY_TOP, z1, 0, BODY_U1, 0, BODY_V1);
    }

    // -- Fluid rendering --

    /**
     * Batches all fluid surface quads into a single translucent draw call.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    static void submitFluids(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        if (!hasAnyFluid(state)) { return; }
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> renderAllFluids(pose, c, light, state));
    }

    /**
     * Renders fluid surfaces for all filled hub slots in a single batch.
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param state the render state snapshot
     */
    private static void renderAllFluids(PoseStack.Pose pose, VertexConsumer c,
            int light, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.slotType[i] != null && state.slotFill[i] > 0f) {
                renderFluidSurface(pose, c, light, i,
                    state.slotType[i], state.slotFill[i]);
            }
        }
    }

    /**
     * Returns true if any slot has fluid to render.
     *
     * @param state the block state
     * @return true if anyFluid is present
     */
    private static boolean hasAnyFluid(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.slotType[i] != null && state.slotFill[i] > 0f) { return true; }
        }
        return false;
    }

    /**
     * Renders fluid geometry for a single hub slot: top face + 4 side faces.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param slot the slot index
     * @param type the goo type
     * @param fill the fill fraction in [0, 1]
     */
    private static void renderFluidSurface(PoseStack.Pose pose, VertexConsumer c,
            int light, int slot, GooType type, float fill) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
        CuboidBounds b = computeCuboidBounds(cx, cz, fill);

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        renderFluidTop(pose, c, light, b, sprite);
        renderFluidSides(pose, c, light, b, sprite, fill);
    }

    /**
     * Computes the XZ-inset fluid cuboid bounds for a hub slot.
     * @param cx   the slot center X coordinate
     * @param cz   the slot center Z coordinate
     * @param fill the fluid fill fraction (0.0 to 1.0)
     * @return the fluid cuboid bounds
     */
    private static CuboidBounds computeCuboidBounds(float cx, float cz, float fill) {
        float x0 = cx - HW + FLUID_INSET;
        float x1 = cx + HW - FLUID_INSET;
        float z0 = cz - HW + FLUID_INSET;
        float z1 = cz + HW - FLUID_INSET;
        float yTop = BODY_BOT + fill * (BODY_TOP - BODY_BOT);
        return new CuboidBounds(x0, x1, z0, z1, BODY_BOT, yTop);
    }

    /**
     * Renders the top face of a hub fluid surface with scaled UVs.
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param b      the precomputed fluid cuboid bounds
     * @param sprite the fluid texture atlas sprite
     */
    private static void renderFluidTop(PoseStack.Pose pose, VertexConsumer c,
            int light, CuboidBounds b, TextureAtlasSprite sprite) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float su1 = u0 + (sprite.getU1() - u0) * (b.x1() - b.x0());
        float sv1 = v0 + (sprite.getV1() - v0) * (b.z1() - b.z0());
        GooRenderUtil.liquidSurface(pose, c, light, GooRenderUtil.OPAQUE_WHITE,
            b.x0(), b.z0(), b.x1(), b.z1(), b.yTop(), u0, su1, v0, sv1);
    }

    /**
     * Renders the four side faces of a hub fluid surface.
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param b      the precomputed fluid cuboid bounds
     * @param sprite the fluid texture atlas sprite
     * @param fill the fluid fill fraction (0.0 to 1.0)
     */
    private static void renderFluidSides(PoseStack.Pose pose, VertexConsumer c,
            int light, CuboidBounds b, TextureAtlasSprite sprite, float fill) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float fillHeight = fill * (BODY_TOP - BODY_BOT);
        float sideVSpan = (sprite.getV1() - v0) * fillHeight;
        float sideXU1 = u0 + (sprite.getU1() - u0) * (b.x1() - b.x0());
        float sideZU1 = u0 + (sprite.getU1() - u0) * (b.z1() - b.z0());
        CanisterGeometry.faceNorth(pose, c, light, b.x0(), BODY_BOT, b.z0(), b.x1(), b.yTop(), u0, sideXU1, v0, v0 + sideVSpan);
        CanisterGeometry.faceSouth(pose, c, light, b.x0(), BODY_BOT, b.z1(), b.x1(), b.yTop(), u0, sideXU1, v0, v0 + sideVSpan);
        CanisterGeometry.faceWest(pose, c, light, b.x0(), BODY_BOT, b.z0(), b.yTop(), b.z1(), u0, sideZU1, v0, v0 + sideVSpan);
        CanisterGeometry.faceEast(pose, c, light, b.x1(), BODY_BOT, b.z0(), b.yTop(), b.z1(), u0, sideZU1, v0, v0 + sideVSpan);
    }

    // -- Stream rendering --

    /**
     * Batches all active stream cuboids into a single translucent draw call.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    static void submitStreams(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        if (!hasAnyStream(state)) { return; }
        int light = state.lightCoords;
        float anim = state.animationTime;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> renderAllStreams(pose, c, light, anim, state));
    }

    /**
     * Renders stream segments for all active hub slots in a single batch.
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param anim the animation tick fraction
     * @param state the render state snapshot
     */
    private static void renderAllStreams(PoseStack.Pose pose, VertexConsumer c,
            int light, float anim, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.streamType[i] == null) { continue; }
            float cx = CENTERS[i][0];
            float cz = CENTERS[i][1];
            float yTop = BODY_TOP;
            float yBottom = BODY_BOT + state.slotFill[i] * (BODY_TOP - BODY_BOT);
            GooStreamRenderer.renderStream(pose, c, light,
                cx, cz, yTop, yBottom,
                state.streamType[i], state.streamRate[i], anim);
        }
    }

    /**
     * Returns true if any slot has an active stream.
     *
     * @param state the block state
     * @return true if anyStream is present
     */
    private static boolean hasAnyStream(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.streamType[i] != null) { return true; }
        }
        return false;
    }
}
