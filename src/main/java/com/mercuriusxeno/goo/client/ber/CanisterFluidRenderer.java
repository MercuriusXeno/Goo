package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Fluid surface and gasket endcap rendering helpers for {@link CanisterBlockEntityRenderer}.
 * Extracted to keep the parent BER under the PMD method-count threshold.
 */
public final class CanisterFluidRenderer {

    /** Copper endcap texture (default canister caps). */
    private static final Identifier COPPER_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /** Choral gasket texture (upgraded canister caps). */
    private static final Identifier CHORAL_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/choral_gasket.png");

    /** Default water tint color (plains biome blue). */
    private static final int WATER_TINT = 0xFF3F76E4;

    /** Vanilla water still sprite ID in the block atlas. */
    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");

    /** Vanilla lava still sprite ID in the block atlas. */
    private static final Identifier LAVA_STILL = Identifier.withDefaultNamespace("block/lava_still");

    /** Shared fluid geometry constants for canister slots. */
    private static final SlotFluidGeometry.SlotGeometry FLUID_GEOM =
        new SlotFluidGeometry.SlotGeometry(CanisterGeometry.HW, CanisterGeometry.BODY_BOT,
                CanisterGeometry.BODY_TOP, CanisterGeometry.FLUID_INSET);

    /** Pixels per block for coordinate conversion. */
    private static final float BLOCK_PIXELS = 16f;

    /** Y ranges describing where gasket boxes land in a canister slot. */
    private static final GasketCapRenderer.GasketYRanges GASKET_Y =
        new GasketCapRenderer.GasketYRanges(CanisterGeometry.BODY_BOT, CanisterGeometry.BODY_TOP,
                CanisterGeometry.GASKET_BOT, CanisterGeometry.GASKET_TOP);

    /** Gasket side UV region (uniform across container types). */
    private static final GasketCapRenderer.GasketUv GASKET_UV =
        new GasketCapRenderer.GasketUv(CanisterGeometry.GS_U0, CanisterGeometry.GS_U1,
                CanisterGeometry.GS_V1);

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
        if (GasketCapRenderer.hasAnyCap(state.slots)) {
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
            (pose, c) -> renderCopperEndcaps(new RenderContext(pose, c, light), state));
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
            (pose, c) -> renderChoralEndcaps(new RenderContext(pose, c, light), state));
    }

    /** Renders copper (non-choral) endcaps for all occupied slots.
     *
     * @param ctx   the render context
     * @param state the canister render state snapshot
     */
    private static void renderCopperEndcaps(RenderContext ctx, CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.slots[i].present) { continue; }
            renderEndcaps(ctx, i,
                !state.slots[i].topGasketPresent, !state.slots[i].bottomGasketPresent);
        }
    }

    /** Renders choral endcaps for all occupied slots.
     *
     * @param ctx   the render context
     * @param state the canister render state snapshot
     */
    private static void renderChoralEndcaps(RenderContext ctx, CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.slots[i].present) { continue; }
            renderEndcaps(ctx, i,
                state.slots[i].topGasketPresent, state.slots[i].bottomGasketPresent);
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
            if (state.slots[i].present
                    && (!state.slots[i].topGasketPresent || !state.slots[i].bottomGasketPresent)) {
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
    private static void renderEndcaps(RenderContext ctx, int slot, boolean top, boolean bottom) {
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
            CanisterGeometry.HW);
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
            SubmitNodeCollector nodeCollector, CanisterRenderState state) {
        // FULL_BRIGHT lightmap UV + solid pass = fullbright opaque fluid.
        SlottedFluidContainer.submitFluids(poseStack, nodeCollector, LightCoordsUtil.FULL_BRIGHT,
                state.slots, FLUID_GEOM, CanisterSlotLayout.SLOT_CENTERS_BLOCK, true);
    }

    /**
     * Returns true if the fluid is water or flowing water.
     *
     * @param fluid the fluid to check
     * @return true if water
     */
    /**
     * Returns the tint color for a vanilla fluid. Water uses blue, lava is white.
     *
     * @param fluid the vanilla fluid
     * @return the ARGB tint color
     */
    public static int getVanillaFluidTint(Fluid fluid) {
        return isWater(fluid) ? WATER_TINT : GooRenderUtil.OPAQUE_WHITE;
    }

    private static boolean isWater(Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    /**
     * Looks up the still texture sprite for a vanilla fluid from the block atlas.
     *
     * @param fluid the fluid
     * @return the still texture sprite
     */
    public static TextureAtlasSprite lookupVanillaFluidSprite(Fluid fluid) {
        Identifier spriteId = fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER
                ? WATER_STILL : LAVA_STILL;
        return Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(spriteId);
    }
}
