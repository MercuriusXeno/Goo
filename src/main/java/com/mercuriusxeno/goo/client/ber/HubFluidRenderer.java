package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * Fluid surface, stream, and body-side rendering helpers for {@link HubBlockEntityRenderer}.
 * Extracted to keep the parent BER under the PMD method-count threshold.
 */
final class HubFluidRenderer {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Canister body side sprite identifier on the BLOCKS atlas. */
    private static final Identifier CANISTER_SIDE_SPRITE =
        Identifier.fromNamespaceAndPath("goo", "block/canister_side");

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
        // Canister walls are translucent so the goo inside is visible.
        // Bind the BLOCKS atlas (not the standalone canister_side texture)
        // so this submission shares its RenderType key with the fluid
        // submission. Same RenderType = same buffer = sortOnUpload handles
        // depth ordering between body and fluid primitives. Cross-buffer
        // ordering hell avoided.
        TextureAtlasSprite sprite = GooRenderUtil.lookupBlockSprite(CANISTER_SIDE_SPRITE);
        GooRenderUtil.UvRect uv = GooRenderUtil.spriteSubRect(sprite, 0f, 0f, BODY_U1, BODY_V1);
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                RenderContext ctx = new RenderContext(pose, c, light);
                for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
                    if (state.slots[i].present) { renderBodySides(ctx, i, uv); }
                }
            });
    }

    /**
     * Renders the 4 side faces of a canister body at the given slot.
     *
     * @param ctx  the render context
     * @param slot the slot index
     * @param uv   the atlas UV rect for the canister body sprite sub-region
     */
    private static void renderBodySides(RenderContext ctx, int slot, GooRenderUtil.UvRect uv) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
        CuboidBounds box = new CuboidBounds(cx - HW, cx + HW, cz - HW, cz + HW, BODY_BOT, BODY_TOP);
        ctx.emitSides(box, uv);
    }

    // -- Fluid rendering --

    /**
     * Submits all fluid surfaces via the shared {@link SlottedFluidContainer} runner.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the render state snapshot
     */
    static void submitFluids(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        // FULL_BRIGHT lightmap UV per fluid vertex makes the lightmap
        // multiplication a no-op. Body submission above shares the same
        // RenderType (entityTranslucent on BLOCKS atlas), so sortOnUpload
        // handles depth ordering of body+fluid primitives together.
        SlottedFluidContainer.submitFluids(poseStack, nodeCollector, LightCoordsUtil.FULL_BRIGHT,
                state.slots, FLUID_GEOM, CENTERS, false);
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
            if (state.slots[i].streamType == null) { continue; }
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
        float yBottom = BODY_BOT + state.slots[slot].fill * (BODY_TOP - BODY_BOT);
        GooStreamRenderer.renderStream(ctx,
            cx, cz, BODY_TOP, yBottom,
            state.slots[slot].streamType, state.slots[slot].streamRate, anim);
    }

    /**
     * Returns true if any slot has an active stream.
     *
     * @param state the block state
     * @return true if anyStream is present
     */
    private static boolean hasAnyStream(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.slots[i].streamType != null) { return true; }
        }
        return false;
    }
}
