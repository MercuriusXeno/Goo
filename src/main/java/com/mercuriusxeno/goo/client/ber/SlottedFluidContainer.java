package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Submits the per-slot fluid surface pass shared by Canister and Hub
 * block-entity renderers. The orchestration is identical between them
 * (predicate scan -> single translucent draw call -> per-slot top + side
 * faces); the differences are pure data: slot count, XZ centers,
 * Y-range geometry, and whether vanilla fluids are supported.
 *
 * <p>Vat does not use this runner because vat fluid is a single stack-
 * column body, not a slot-array.
 */
public final class SlottedFluidContainer {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    private SlottedFluidContainer() {
    }

    /**
     * Submits one translucent draw call covering every filled slot.
     * No-op when no slot has fluid.
     *
     * @param poseStack       the pose stack
     * @param nodeCollector   the render node collector
     * @param lightCoords     packed light value for the BE
     * @param slots           per-slot snapshots
     * @param geom            shared slot geometry (HW + body Y range + inset)
     * @param centers         per-slot XZ block-coord centers (parallel to {@code slots})
     * @param supportsVanilla true if {@link SlotState#fluid} should be rendered when no goo type is set
     */
    public static void submitFluids(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                    int lightCoords, SlotState[] slots,
                                    SlotFluidGeometry.SlotGeometry geom, float[][] centers,
                                    boolean supportsVanilla) {
        if (!hasAnyFluid(slots, supportsVanilla)) {
            return;
        }
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, c) -> {
                    RenderContext ctx = new RenderContext(pose, c, lightCoords);
                    renderAllFluids(ctx, slots, geom, centers, supportsVanilla);
                });
    }

    private static void renderAllFluids(RenderContext ctx, SlotState[] slots,
                                        SlotFluidGeometry.SlotGeometry geom, float[][] centers,
                                        boolean supportsVanilla) {
        for (int i = 0; i < slots.length; i++) {
            SlotState s = slots[i];
            if (s.fill <= 0f) {
                continue;
            }
            if (s.type != null) {
                renderGooSurface(ctx, centers[i], s.type, s.fill, geom);
            } else if (supportsVanilla && s.fluid != Fluids.EMPTY) {
                renderVanillaSurface(ctx, centers[i], s.fluid, s.fill, geom);
            }
        }
    }

    private static boolean hasAnyFluid(SlotState[] slots, boolean supportsVanilla) {
        for (SlotState s : slots) {
            if (s.fill <= 0f) {
                continue;
            }
            if (s.type != null) {
                return true;
            }
            if (supportsVanilla && s.fluid != Fluids.EMPTY) {
                return true;
            }
        }
        return false;
    }

    private static void renderGooSurface(RenderContext ctx, float[] center,
                                         com.mercuriusxeno.goo.GooType type, float fill,
                                         SlotFluidGeometry.SlotGeometry geom) {
        CuboidBounds b = SlotFluidGeometry.computeBounds(geom, center[0], center[1], fill);
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        SlotFluidGeometry.renderFluidTop(ctx, b, sprite);
        SlotFluidGeometry.renderFluidSides(ctx, b, sprite, fill, geom);
    }

    private static void renderVanillaSurface(RenderContext ctx, float[] center, Fluid fluid,
                                             float fill, SlotFluidGeometry.SlotGeometry geom) {
        CuboidBounds b = SlotFluidGeometry.computeBounds(geom, center[0], center[1], fill);
        TextureAtlasSprite sprite = CanisterFluidRenderer.lookupVanillaFluidSprite(fluid);
        int tint = CanisterFluidRenderer.getVanillaFluidTint(fluid);
        SlotFluidGeometry.renderFluidTop(ctx, b, sprite, tint);
        SlotFluidGeometry.renderFluidSides(ctx, b, sprite, fill, geom, tint);
    }
}
