package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.model.CanisterGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
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

    /** Pixels per block for coordinate conversion. */
    private static final float BLOCK_PIXELS = 16f;

    /** Gasket side U start: column 4/16. */
    private static final float GS_U0 = 0.25f;

    /** Gasket side U end: column 8/16. */
    private static final float GS_U1 = 0.5f;

    /** Gasket side V end: row 1/16. */
    private static final float GS_V1 = 0.0625f;

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
        if (hasAnyChoralCap(state)) {
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
            (pose, c) -> renderCopperEndcaps(pose, c, light, state));
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
            (pose, c) -> renderChoralEndcaps(pose, c, light, state));
    }

    /** Renders copper (non-choral) endcaps for all occupied slots.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer for copper endcap geometry
     * @param light packed light value
     * @param state the canister render state snapshot
     */
    private static void renderCopperEndcaps(PoseStack.Pose pose, VertexConsumer c,
            int light, CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.canisterPresent[i]) { continue; }
            renderEndcaps(pose, c, light, i,
                !state.topGasketPresent[i], !state.bottomGasketPresent[i]);
        }
    }

    /** Renders choral endcaps for all occupied slots.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer for choral endcap geometry
     * @param light packed light value
     * @param state the canister render state snapshot
     */
    private static void renderChoralEndcaps(PoseStack.Pose pose, VertexConsumer c,
            int light, CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.canisterPresent[i]) { continue; }
            renderEndcaps(pose, c, light, i,
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
     * Returns true if any occupied slot has a choral gasket on either end.
     *
     * @param state the block state
     * @return true if anyChoralCap is present
     */
    private static boolean hasAnyChoralCap(CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (state.topGasketPresent[i] || state.bottomGasketPresent[i]) { return true; }
        }
        return false;
    }

    /**
     * Renders endcap boxes for a slot on the specified sides.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param slot the slot index
     * @param top whether to render the top cap
     * @param bottom whether to render the bottom cap
     */
    private static void renderEndcaps(PoseStack.Pose pose, VertexConsumer c,
            int light, int slot, boolean top, boolean bottom) {
        if (!top && !bottom) { return; }
        float cx = CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS;
        float cz = CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS;
        renderEndcapBoxes(pose, c, light, cx, cz, top, bottom);
    }

    /**
     * Emits gasket boxes at the given center for the requested cap sides.
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param cx   the slot center X coordinate
     * @param cz   the slot center Z coordinate
     * @param top    true to render the top endcap
     * @param bottom true to render the bottom endcap
     */
    private static void renderEndcapBoxes(PoseStack.Pose pose, VertexConsumer c,
            int light, float cx, float cz, boolean top, boolean bottom) {
        float x0 = cx - HW;
        float x1 = cx + HW;
        float z0 = cz - HW;
        float z1 = cz + HW;
        if (top) {
            CanisterGeometry.gasketBox(pose, c, light, x0, BODY_TOP, z0, x1, GASKET_TOP, z1, GS_U0, GS_U1, GS_V1);
        }
        if (bottom) {
            CanisterGeometry.gasketBox(pose, c, light, x0, GASKET_BOT, z0, x1, BODY_BOT, z1, GS_U0, GS_U1, GS_V1);
        }
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
        CuboidBounds b = computeBounds(cx, cz, fill);
        GooRenderUtil.UvRect topUv = scaledTopUv(b, type);
        ctx.liquidSurface(GooRenderUtil.OPAQUE_WHITE, b, topUv);
        emitFluidSides(ctx, b, type, fill);
    }

    /**
     * Computes the XZ-inset fluid cuboid bounds for a canister slot.
     *
     * @param cx   the slot center X coordinate
     * @param cz   the slot center Z coordinate
     * @param fill the fluid fill fraction (0.0 to 1.0)
     * @return the fluid cuboid bounds
     */
    private static CuboidBounds computeBounds(float cx, float cz, float fill) {
        float x0 = cx - HW + FLUID_INSET;
        float x1 = cx + HW - FLUID_INSET;
        float z0 = cz - HW + FLUID_INSET;
        float z1 = cz + HW - FLUID_INSET;
        float yTop = BODY_BOT + fill * (BODY_TOP - BODY_BOT);
        return new CuboidBounds(x0, x1, z0, z1, BODY_BOT, yTop);
    }

    /**
     * Computes a UV rect scaled to the cuboid's XZ footprint for the top face.
     *
     * @param b    the cuboid bounds
     * @param type the goo type for sprite lookup
     * @return the scaled UV rect
     */
    private static GooRenderUtil.UvRect scaledTopUv(CuboidBounds b, GooType type) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float su1 = u0 + (sprite.getU1() - u0) * (b.x1() - b.x0());
        float sv1 = v0 + (sprite.getV1() - v0) * (b.z1() - b.z0());
        return new GooRenderUtil.UvRect(u0, v0, su1, sv1);
    }

    /**
     * Emits the four side faces with UV scaled per axis.
     *
     * @param ctx  the render context
     * @param b    the cuboid bounds
     * @param type the goo type for sprite lookup
     * @param fill the fluid fill fraction (0.0 to 1.0)
     */
    private static void emitFluidSides(RenderCtx ctx, CuboidBounds b, GooType type, float fill) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float fillHeight = fill * (BODY_TOP - BODY_BOT);
        float sideVSpan = (sprite.getV1() - v0) * fillHeight;
        GooRenderUtil.UvRect xUv = new GooRenderUtil.UvRect(u0, v0,
            u0 + (sprite.getU1() - u0) * (b.x1() - b.x0()), v0 + sideVSpan);
        GooRenderUtil.UvRect zUv = new GooRenderUtil.UvRect(u0, v0,
            u0 + (sprite.getU1() - u0) * (b.z1() - b.z0()), v0 + sideVSpan);
        ctx.emitFace(b, xUv, Direction.NORTH);
        ctx.emitFace(b, xUv, Direction.SOUTH);
        ctx.emitFace(b, zUv, Direction.WEST);
        ctx.emitFace(b, zUv, Direction.EAST);
    }
}
