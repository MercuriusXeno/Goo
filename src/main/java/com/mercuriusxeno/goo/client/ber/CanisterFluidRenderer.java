package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Fluid surface and gasket endcap rendering helpers for {@link CanisterBlockEntityRenderer}.
 * Extracted to keep the parent BER under the PMD method-count threshold.
 */
final class CanisterFluidRenderer {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Copper endcap texture (default canister caps). */
    private static final Identifier COPPER_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /** Choral gasket texture (upgraded canister caps). */
    private static final Identifier CHORAL_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/choral_gasket.png");

    /** Canister half-width: 2px. */
    private static final float HW = 2f / 16f;

    /** Top of lower gasket / bottom of body (y=1px). */
    private static final float BODY_BOT = 1f / 16f;

    /** Top of body / bottom of upper gasket (y=11px). */
    private static final float BODY_TOP = 11f / 16f;

    /** Bottom of lower gasket (y=0). */
    private static final float GASKET_BOT = 0f;

    /** Top of upper gasket (y=12px). */
    private static final float GASKET_TOP = 12f / 16f;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    private static final float FLUID_INSET = 0.5f / 16f;

    /** Shared fluid geometry constants for canister slots. */
    private static final SlotFluidGeometry.SlotGeometry FLUID_GEOM =
        new SlotFluidGeometry.SlotGeometry(HW, BODY_BOT, BODY_TOP, FLUID_INSET);

    /** Pixels per block for coordinate conversion. */
    private static final float BLOCK_PIXELS = 16f;

    /** Gasket side U start: column 4/16. */
    private static final float GS_U0 = 0.25f;

    /** Gasket side U end: column 8/16. */
    private static final float GS_U1 = 0.5f;

    /** Gasket side V end: row 1/16. */
    private static final float GS_V1 = 0.0625f;

    /** Y ranges describing where gasket boxes land in a canister slot. */
    private static final GasketCapRenderer.GasketYRanges GASKET_Y =
        new GasketCapRenderer.GasketYRanges(BODY_BOT, BODY_TOP, GASKET_BOT, GASKET_TOP);

    /** Gasket side UV region (uniform across container types). */
    private static final GasketCapRenderer.GasketUv GASKET_UV =
        new GasketCapRenderer.GasketUv(GS_U0, GS_U1, GS_V1);

    private CanisterFluidRenderer() {
    }

    // -- Gasket rendering --

    /**
     * Submits endcap geometry for all occupied slots. Every canister always gets
     * top and bottom caps - copper by default, choral when upgraded. Two draw
     * calls batch each texture separately.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    static void submitGaskets(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CanisterRenderState state) {
        int light = state.lightCoords;
        if (hasAnyCopperCap(state)) {
            submitCopperCaps(poseStack, nodeCollector, light, state);
        }
        if (GasketCapRenderer.hasAnyCap(state.topGasketPresent, state.bottomGasketPresent)) {
            submitChoralCaps(poseStack, nodeCollector, light, state);
        }
    }

    /**
     * Submits copper (non-choral) endcap geometry in a single draw call.
     * @param poseStack the current pose transformation stack
     * @param nodeCollector the render node collector for geometry submission
     * @param light the packed light level for shading
     * @param state the render state snapshot
     */
    private static void submitCopperCaps(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int light, CanisterRenderState state) {
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entitySolid(COPPER_GASKET),
            (pose, c) -> renderCopperEndcaps(new RenderCtx(pose, c, light), state));
    }

    /**
     * Submits choral gasket endcap geometry in a single draw call.
     * @param poseStack the current pose transformation stack
     * @param nodeCollector the render node collector for geometry submission
     * @param light the packed light level for shading
     * @param state the render state snapshot
     */
    private static void submitChoralCaps(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int light, CanisterRenderState state) {
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entitySolid(CHORAL_GASKET),
            (pose, c) -> renderChoralEndcaps(new RenderCtx(pose, c, light), state));
    }

    /** Renders copper (non-choral) endcaps for all occupied slots.
     *
     * @param ctx   the render context
     * @param state the canister render state snapshot
     */
    private static void renderCopperEndcaps(RenderCtx ctx, CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.canisterPresent[i]) { continue; }
            renderEndcaps(ctx, i,
                !state.topGasketPresent[i], !state.bottomGasketPresent[i]);
        }
    }

    /** Renders choral endcaps for all occupied slots.
     *
     * @param ctx   the render context
     * @param state the canister render state snapshot
     */
    private static void renderChoralEndcaps(RenderCtx ctx, CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.canisterPresent[i]) { continue; }
            renderEndcaps(ctx, i,
                state.topGasketPresent[i], state.bottomGasketPresent[i]);
        }
    }

    /**
     * Returns true if any occupied slot has a non-choral (copper) cap on either end.
     *
     * @param state the block state
     * @return true if anyCopperCap is present
     */
    private static boolean hasAnyCopperCap(CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (state.canisterPresent[i]
                    && (!state.topGasketPresent[i] || !state.bottomGasketPresent[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Delegates to {@link GasketCapRenderer#renderEndcaps} with this container's
     * Y ranges and UV constants. Resolves the slot index to XZ coordinates via
     * {@link CanisterSlotLayout#SLOT_CENTERS}.
     *
     * @param ctx    the render context
     * @param slot   the slot index
     * @param top    whether to render the top cap
     * @param bottom whether to render the bottom cap
     */
    private static void renderEndcaps(RenderCtx ctx, int slot, boolean top, boolean bottom) {
        GasketCapRenderer.renderEndcaps(ctx, slotBoundsXZ(slot), GASKET_Y, GASKET_UV, top, bottom);
    }

    /**
     * Computes the XZ cuboid bounds for a canister slot at index. Container-
     * specific: canister centers live in pixel space and are divided by
     * {@link #BLOCK_PIXELS} before delegating to {@link GasketCapRenderer#slotBoundsXZ}.
     *
     * @param slot the slot index in the canister grid
     * @return XZ cuboid bounds centered on the slot with Y zeroed
     */
    private static CuboidBounds slotBoundsXZ(int slot) {
        return GasketCapRenderer.slotBoundsXZ(
            CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS,
            CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS,
            HW);
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
            SubmitNodeCollector nodeCollector, CanisterRenderState state) {
        if (!hasAnyFluid(state)) { return; }
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> renderAllFluids(new RenderCtx(pose, c, light), state));
    }

    /**
     * Renders fluid surfaces for all filled slots in a single batch.
     *
     * @param ctx   the render context
     * @param state the render state snapshot
     */
    private static void renderAllFluids(RenderCtx ctx, CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
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
    private static boolean hasAnyFluid(CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (state.slotType[i] != null && state.slotFill[i] > 0f) { return true; }
        }
        return false;
    }

    /**
     * Renders fluid geometry for a single slot: top face + 4 side faces
     * from the canister body bottom up to the fill level. UVs are scaled
     * to the cuboid's fraction of a 16px block to avoid texture squishing.
     *
     * @param ctx  the render context
     * @param slot the slot index
     * @param type the goo type
     * @param fill the fill fraction in [0, 1]
     */
    private static void renderFluidSurface(RenderCtx ctx, int slot, GooType type, float fill) {
        float cx = CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS;
        float cz = CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS;
        CuboidBounds b = SlotFluidGeometry.computeBounds(FLUID_GEOM, cx, cz, fill);
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        SlotFluidGeometry.renderFluidTop(ctx, b, sprite);
        SlotFluidGeometry.renderFluidSides(ctx, b, sprite, fill, FLUID_GEOM);
    }
}
