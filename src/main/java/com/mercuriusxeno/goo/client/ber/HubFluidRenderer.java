package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
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

    /** Shared fluid geometry constants for hub slots. */
    private static final SlotFluidGeometry.SlotGeometry FLUID_GEOM =
        new SlotFluidGeometry.SlotGeometry(HW, BODY_BOT, BODY_TOP, FLUID_INSET);

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
                RenderContext ctx = new RenderContext(pose, c, light);
                for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
                    if (state.canisterPresent[i]) { renderBodySides(ctx, i); }
                }
            });
    }

    /**
     * Renders the 4 side faces of a canister body at the given slot.
     *
     * @param ctx  the render context
     * @param slot the slot index
     */
    private static void renderBodySides(RenderContext ctx, int slot) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
        CuboidBounds box = new CuboidBounds(cx - HW, cx + HW, cz - HW, cz + HW, BODY_BOT, BODY_TOP);
        GooRenderUtil.UvRect uv = new GooRenderUtil.UvRect(0, 0, BODY_U1, BODY_V1);
        ctx.emitSides(box, uv);
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
            (pose, c) -> renderAllFluids(new RenderContext(pose, c, light), state));
    }

    /**
     * Renders fluid surfaces for all filled hub slots in a single batch.
     *
     * @param ctx   the render context
     * @param state the render state snapshot
     */
    private static void renderAllFluids(RenderContext ctx, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.slotType[i] != null && state.slotFill[i] > 0f) {
                renderFluidSurface(ctx, i, state.slotType[i], state.slotFill[i]);
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
     * @param ctx  the render context
     * @param slot the slot index
     * @param type the goo type
     * @param fill the fill fraction in [0, 1]
     */
    private static void renderFluidSurface(RenderContext ctx, int slot, GooType type, float fill) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
        CuboidBounds b = SlotFluidGeometry.computeBounds(FLUID_GEOM, cx, cz, fill);
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        SlotFluidGeometry.renderFluidTop(ctx, b, sprite);
        SlotFluidGeometry.renderFluidSides(ctx, b, sprite, fill, FLUID_GEOM);
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
            (pose, c) -> renderAllStreams(new RenderContext(pose, c, light), anim, state));
    }

    /**
     * Renders stream segments for all active hub slots in a single batch.
     *
     * @param ctx   the render context
     * @param anim  the animation tick fraction
     * @param state the render state snapshot
     */
    private static void renderAllStreams(RenderContext ctx, float anim, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.streamType[i] == null) { continue; }
            renderSlotStream(ctx, anim, state, i);
        }
    }

    /**
     * Renders a single slot's goo stream segment.
     * @param ctx the render context
     * @param anim the animation tick fraction
     * @param state the render state snapshot
     * @param slot the slot index
     */
    private static void renderSlotStream(RenderContext ctx, float anim, HubRenderState state, int slot) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
        float yBottom = BODY_BOT + state.slotFill[slot] * (BODY_TOP - BODY_BOT);
        GooStreamRenderer.renderStream(ctx,
            cx, cz, BODY_TOP, yBottom,
            state.streamType[slot], state.streamRate[slot], anim);
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
