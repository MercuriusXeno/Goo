package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.ber.CuboidBounds;
import com.mercuriusxeno.goo.client.ber.FluidFaceEmitter;
import com.mercuriusxeno.goo.client.ber.RenderContext;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Special item renderer for vat items. Renders the vat shell model and fluid
 * fill quads inside the vat body. Reads goo contents from item data components
 * (vats retain goo when picked up).
 */
public class VatSpecialRenderer implements SpecialModelRenderer<VatSpecialRenderer.VatData> {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    // -- Vat geometry in block coords --

    /** Vat body left/front edge (1px inset from block edge). */
    private static final float WALL = 1f / 16f;

    /** Vat body bottom (top of base cap, y=2px). */
    private static final float BODY_BOT = 2f / 16f;

    /** Vat body top (bottom of top cap, y=14px). */
    private static final float BODY_TOP = 14f / 16f;

    /** Vat full bottom (y=0). */
    private static final float VAT_BOT = 0f;

    /** Vat full top (y=16px). */
    private static final float VAT_TOP = 1f;
    /** Fully opaque white in ARGB for untinted quad rendering. */
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    private static final float FLUID_INSET = 0.5f / 16f;

    /** Vertical nudge above vat base to prevent z-fighting at low fill (0.01px). */
    private static final float Y_EPSILON = 0.01f / 16f;

    /** Creates a vat special renderer. */
    public VatSpecialRenderer() {
    }

    /**
     * Extracted render data from the vat item stack.
     *
     * @param gooType the dominant goo type, or null if empty
     * @param fill    the fill fraction [0, 1]
     */
    public record VatData(@Nullable GooType gooType, float fill) {
    }

    /**
     * Extracts goo render data from the vat item stack.
     *
     * @param stack the item stack
     * @return the extracted render data, or null
     */
    @Override
    public @Nullable VatData extractArgument(ItemStack stack) {
        GooContents contents = VatBlockItem.getGooContents(stack);
        if (contents.isEmpty()) { return null; }
        int compression = GooEnchantments.getCompressionLevel(stack);
        long capacity = ContainerCapacity.vatCapacity(compression);
        float fill = Math.min(1f, (float) contents.totalVolume() / capacity);
        return new VatData(contents.largestType(), fill);
    }

    /**
     * Renders the vat shell and fluid fill for the item.
     *
     * @param data the extracted render data
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight the packed light value
     * @param packedOverlay the packed overlay value
     * @param hasFoil whether the item has enchantment foil
     * @param outlineColor the outline color for selected items
     */
    @Override
    public void submit(@Nullable VatData data,
            PoseStack poseStack, SubmitNodeCollector nodeCollector,
            int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();

        submitShell(poseStack, nodeCollector, packedLight);

        if (data != null && data.gooType() != null && data.fill() > 0f) {
            submitFluid(poseStack, nodeCollector, packedLight, data.gooType(), data.fill());
        }

        poseStack.popPose();
    }

    /**
     * Submits the baked vat shell model (cap + body + base).
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight the packed light value
     */
    private static void submitShell(PoseStack poseStack, SubmitNodeCollector nodeCollector,
            int packedLight) {
        QuadCollection model = VatBodyModels.getModel();
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                QuadInstance qi = new QuadInstance();
                qi.setColor(OPAQUE_WHITE);
                qi.setLightCoords(packedLight);
                qi.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                for (BakedQuad quad : model.getAll()) {
                    c.putBakedQuad(pose, quad, qi);
                }
            });
    }

    /**
     * Submits fluid surface geometry inside the vat body.
     * Renders top face + 4 side faces from body bottom up to the fill level.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight the packed light value
     * @param type the goo type
     * @param fill the fill fraction in [0, 1]
     */
    private static void submitFluid(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int packedLight,
            GooType type, float fill) {
        CuboidBounds b = computeFluidBounds(fill);
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> FluidFaceEmitter.emitFluidFaces(
                new RenderContext(pose, c, packedLight), b, type));
    }

    /**
     * Computes the fluid cuboid bounds inset from the vat walls.
     * @param fill the fill fraction [0, 1]
     * @return the cuboid bounds for the fluid volume inside the vat walls
     */
    private static CuboidBounds computeFluidBounds(float fill) {
        float x0 = WALL + FLUID_INSET;
        float x1 = 1f - WALL - FLUID_INSET;
        float z0 = WALL + FLUID_INSET;
        float z1 = 1f - WALL - FLUID_INSET;
        float yBot = BODY_BOT + Y_EPSILON;
        float y = yBot + fill * (BODY_TOP - yBot);
        return new CuboidBounds(x0, x1, z0, z1, yBot, y);
    }

    /**
     * Reports the geometric extents of the vat for GUI rendering.
     * 14x16x14 px block, inset 1px from edges.
     *
     * @param output consumer for extent corner vertices
     */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(WALL, VAT_BOT, WALL));
        output.accept(new Vector3f(1f - WALL, VAT_TOP, 1f - WALL));
    }

    /**
     * Unbaked factory for the vat special renderer. Registered as
     * "goo:vat_goo" in the special model renderer registry.
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<VatData> {

        /** Codec for data-driven item model deserialization. */
        public static final MapCodec<VatSpecialRenderer.Unbaked> MAP_CODEC =
            MapCodec.unit(new VatSpecialRenderer.Unbaked());

        @Override
        public MapCodec<VatSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<VatData> bake(SpecialModelRenderer.BakingContext context) {
            return new VatSpecialRenderer();
        }
    }
}
