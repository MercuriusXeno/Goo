package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.client.ber.CanisterFluidRenderer;
import com.mercuriusxeno.goo.client.ber.CuboidBounds;
import com.mercuriusxeno.goo.client.ber.RenderContext;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Special item renderer for canister items. Renders the canister body model,
 * gasket caps, and fluid fill quads inside the canister geometry, matching
 * the in-world BER appearance. Reads goo contents from item data components.
 */
public class CanisterSpecialRenderer implements SpecialModelRenderer<CanisterSpecialRenderer.GooData> {

    /**
     * Block atlas texture path for fluid sprite lookups.
     */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /**
     * Copper endcap texture (default canister caps).
     */
    private static final Identifier COPPER_GASKET =
            Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /**
     * Choral gasket texture (upgraded canister caps).
     */
    private static final Identifier CHORAL_GASKET =
            Identifier.fromNamespaceAndPath("goo", "textures/block/choral_gasket.png");

    /**
     * Fully opaque white in ARGB for untinted quad rendering.
     */
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    // -- Item-form geometry (single canister centered in block) --

    /** Center of the canister in block coordinates. */
    private static final float CENTER = 8f / 16f;

    /** Min X/Z boundary of the canister body. */
    private static final float BODY_MIN_XZ = CENTER - CanisterGeometry.HW;

    /** Max X/Z boundary of the canister body. */
    private static final float BODY_MAX_XZ = CENTER + CanisterGeometry.HW;

    /**
     * Creates a canister special renderer.
     */
    public CanisterSpecialRenderer() {
    }

    /**
     * Computes the fill fraction [0,1] for the canister's current contents.
     *
     * @param stack   the canister item stack
     * @param content the fluid content to compute fill from
     * @return fill fraction clamped to [0,1]
     */
    private static float computeFillFraction(ItemStack stack, CanisterFluidContent content) {
        int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(stack);
        int capacity = ContainerCapacity.canisterCapacity(compression);
        return Math.min(1f, (float) content.amount() / capacity);
    }

    /**
     * Submits fluid geometry only when the data contains a non-empty fill.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param data          the goo data, or null
     */
    private static void submitFluidIfPresent(PoseStack poseStack,
                                             SubmitNodeCollector nodeCollector, int packedLight,
                                             @Nullable GooData data) {
        if (data == null || data.fill() <= 0f) {
            return;
        }
        if (data.gooType() != null) {
            submitFluid(poseStack, nodeCollector, packedLight, data.gooType(), data.fill());
        } else if (data.vanillaFluid() != null) {
            submitVanillaFluid(poseStack, nodeCollector, packedLight,
                    data.vanillaFluid(), data.fill());
        }
    }

    /**
     * Submits the baked canister body model at the item center position.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     */
    private static void submitBody(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   int packedLight) {
        QuadCollection model = CanisterBodyModels.getModel();
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> emitBodyQuads(pose, c, packedLight, model));
    }

    /**
     * Emits all baked quads from the canister body model with opaque white tint.
     *
     * @param pose        the pose matrix entry
     * @param c           the vertex consumer
     * @param packedLight the packed light value
     * @param model       the baked quad collection
     */
    private static void emitBodyQuads(PoseStack.Pose pose, VertexConsumer c,
                                      int packedLight, QuadCollection model) {
        QuadInstance qi = new QuadInstance();
        qi.setColor(OPAQUE_WHITE);
        qi.setLightCoords(packedLight);
        qi.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (BakedQuad quad : model.getAll()) {
            c.putBakedQuad(pose, quad, qi);
        }
    }

    /**
     * Submits endcap geometry. Every canister always gets top and bottom caps -
     * copper by default, choral when upgraded. Two draw calls batch each texture.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param data          the goo data, or null
     */
    private static void submitGaskets(PoseStack poseStack,
                                      SubmitNodeCollector nodeCollector, int packedLight,
                                      @Nullable GooData data) {
        boolean hasTopChoral = data != null && data.hasTopGasket();
        boolean hasBottomChoral = data != null && data.hasBottomGasket();
        submitCopperEndcaps(poseStack, nodeCollector, packedLight, hasTopChoral, hasBottomChoral);
        submitChoralEndcaps(poseStack, nodeCollector, packedLight, hasTopChoral, hasBottomChoral);
    }

    /**
     * Submits copper (default) endcap quads for caps without choral upgrades.
     *
     * @param poseStack       the pose stack for rendering
     * @param nodeCollector   the render node collector
     * @param packedLight     the packed light value
     * @param hasTopChoral    true if top has choral upgrade
     * @param hasBottomChoral true if bottom has choral upgrade
     */
    private static void submitCopperEndcaps(PoseStack poseStack,
                                            SubmitNodeCollector nodeCollector, int packedLight,
                                            boolean hasTopChoral, boolean hasBottomChoral) {
        boolean copperTop = !hasTopChoral;
        boolean copperBottom = !hasBottomChoral;
        if (copperTop || copperBottom) {
            submitEndcapBatch(poseStack, nodeCollector, packedLight,
                    COPPER_GASKET, copperTop, copperBottom);
        }
    }

    /**
     * Submits choral (upgraded) endcap quads for caps with choral gaskets.
     *
     * @param poseStack       the pose stack for rendering
     * @param nodeCollector   the render node collector
     * @param packedLight     the packed light value
     * @param hasTopChoral    true if top has choral upgrade
     * @param hasBottomChoral true if bottom has choral upgrade
     */
    private static void submitChoralEndcaps(PoseStack poseStack,
                                            SubmitNodeCollector nodeCollector, int packedLight,
                                            boolean hasTopChoral, boolean hasBottomChoral) {
        if (hasTopChoral || hasBottomChoral) {
            submitEndcapBatch(poseStack, nodeCollector, packedLight,
                    CHORAL_GASKET, hasTopChoral, hasBottomChoral);
        }
    }

    /**
     * Submits a single endcap draw call for the given texture and cap flags.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param texture       the endcap texture identifier
     * @param top           true to render top endcap
     * @param bottom        true to render bottom endcap
     */
    private static void submitEndcapBatch(PoseStack poseStack,
                                          SubmitNodeCollector nodeCollector, int packedLight,
                                          Identifier texture, boolean top, boolean bottom) {
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(texture),
                (pose, c) -> emitEndcapQuads(new RenderContext(pose, c, packedLight), top, bottom));
    }

    /**
     * Emits gasket box quads for the requested top and/or bottom endcaps.
     *
     * @param ctx    the render context
     * @param top    true to render the top endcap
     * @param bottom true to render the bottom endcap
     */
    private static void emitEndcapQuads(RenderContext ctx, boolean top, boolean bottom) {
        if (top) {
            ctx.gasketBox(new CuboidBounds(BODY_MIN_XZ, BODY_MAX_XZ, BODY_MIN_XZ, BODY_MAX_XZ,
                    CanisterGeometry.BODY_TOP, CanisterGeometry.GASKET_TOP),
                    CanisterGeometry.GS_U0, CanisterGeometry.GS_U1, CanisterGeometry.GS_V1);
        }
        if (bottom) {
            ctx.gasketBox(new CuboidBounds(BODY_MIN_XZ, BODY_MAX_XZ, BODY_MIN_XZ, BODY_MAX_XZ,
                    CanisterGeometry.GASKET_BOT, CanisterGeometry.BODY_BOT),
                    CanisterGeometry.GS_U0, CanisterGeometry.GS_U1, CanisterGeometry.GS_V1);
        }
    }

    /**
     * Submits fluid surface geometry inside the canister body.
     * Renders top face + 4 side faces from body bottom up to the fill level.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param type          the goo type
     * @param fill          the fill fraction in [0, 1]
     */
    private static void submitFluid(PoseStack poseStack,
                                    SubmitNodeCollector nodeCollector, int packedLight,
                                    GooType type, float fill) {
        CuboidBounds b = new CuboidBounds(
                CENTER - CanisterGeometry.HW + CanisterGeometry.FLUID_INSET,
                CENTER + CanisterGeometry.HW - CanisterGeometry.FLUID_INSET,
                CENTER - CanisterGeometry.HW + CanisterGeometry.FLUID_INSET,
                CENTER + CanisterGeometry.HW - CanisterGeometry.FLUID_INSET,
                CanisterGeometry.BODY_BOT,
                CanisterGeometry.BODY_BOT + fill * (CanisterGeometry.BODY_TOP - CanisterGeometry.BODY_BOT));
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> FluidFaceEmitter.emitFluidFaces(
                        new RenderContext(pose, c, packedLight), b, type));
    }

    /**
     * Submits vanilla fluid (water/lava) surface geometry inside the canister body.
     * Uses the same geometry as goo fluid but with vanilla fluid textures and tint.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param fluid         the vanilla fluid
     * @param fill          the fill fraction in [0, 1]
     */
    private static void submitVanillaFluid(PoseStack poseStack,
                                           SubmitNodeCollector nodeCollector, int packedLight,
                                           Fluid fluid, float fill) {
        CuboidBounds b = new CuboidBounds(
                CENTER - CanisterGeometry.HW + CanisterGeometry.FLUID_INSET,
                CENTER + CanisterGeometry.HW - CanisterGeometry.FLUID_INSET,
                CENTER - CanisterGeometry.HW + CanisterGeometry.FLUID_INSET,
                CENTER + CanisterGeometry.HW - CanisterGeometry.FLUID_INSET,
                CanisterGeometry.BODY_BOT,
                CanisterGeometry.BODY_BOT + fill * (CanisterGeometry.BODY_TOP - CanisterGeometry.BODY_BOT));
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> {
                    TextureAtlasSprite sprite =
                            CanisterFluidRenderer.lookupVanillaFluidSprite(fluid);
                    int tint = CanisterFluidRenderer.getVanillaFluidTint(fluid);
                    RenderContext ctx = new RenderContext(pose, c, packedLight);
                    FluidFaceEmitter.emitFluidFaces(ctx, b, sprite, tint);
                });
    }

    /**
     * Extracts goo render data from the canister item stack.
     *
     * @param stack the canister item stack
     * @return render data, or null if the canister is empty (no visual change)
     */
    @Override
    public @Nullable GooData extractArgument(ItemStack stack) {
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        boolean hasTop = meta.topGasketId() != null;
        boolean hasBottom = meta.bottomGasketId() != null;
        CanisterFluidContent content = CanisterItem.getFluidContent(stack);
        if (content.isEmpty()) {
            return new GooData(null, null, 0f, hasTop, hasBottom);
        }
        float fill = computeFillFraction(stack, content);
        GooType gooType = content.getGooType();
        Fluid vanillaFluid =
                gooType == null ? content.fluid() : null;
        return new GooData(gooType, vanillaFluid, fill, hasTop, hasBottom);
    }

    /**
     * Renders the canister body, gaskets, and fluid fill for the item.
     *
     * @param data          extracted goo data (may be null for empty canisters)
     * @param poseStack     the current pose stack
     * @param nodeCollector the render node collector
     * @param packedLight   packed light value
     * @param packedOverlay packed overlay value
     * @param hasFoil       whether the item has enchantment foil
     * @param outlineColor  outline color for selected items
     */
    @Override
    public void submit(@Nullable GooData data,
                       PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();

        submitBody(poseStack, nodeCollector, packedLight);
        submitGaskets(poseStack, nodeCollector, packedLight, data);
        submitFluidIfPresent(poseStack, nodeCollector, packedLight, data);

        poseStack.popPose();
    }

    /**
     * Reports the geometric extents of the canister for GUI rendering.
     * Covers the full canister volume: 4px wide, 12px tall, 4px deep,
     * centered at (8, 6, 8) in block coordinates.
     *
     * @param output consumer for extent corner vertices
     */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float x0 = CENTER - CanisterGeometry.HW;
        float x1 = CENTER + CanisterGeometry.HW;
        float z0 = CENTER - CanisterGeometry.HW;
        float z1 = CENTER + CanisterGeometry.HW;
        output.accept(new Vector3f(x0, CanisterGeometry.GASKET_BOT, z0));
        output.accept(new Vector3f(x1, CanisterGeometry.GASKET_TOP, z1));
    }

    /**
     * Extracted render data from the canister item stack. For goo fluids,
     * gooType is set. For vanilla fluids (water/lava), vanillaFluid is set.
     *
     * @param gooType         the goo type, or null for vanilla/empty
     * @param vanillaFluid    the vanilla fluid, or null for goo/empty
     * @param fill            the fill fraction [0, 1]
     * @param hasTopGasket    whether a choral gasket is installed on top
     * @param hasBottomGasket whether a choral gasket is installed on bottom
     */
    public record GooData(@Nullable GooType gooType,
                          @Nullable Fluid vanillaFluid,
                          float fill, boolean hasTopGasket, boolean hasBottomGasket) {
    }

    /**
     * Unbaked factory for the canister special renderer. Registered as
     * "goo:canister_goo" in the special model renderer registry.
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<GooData> {

        /**
         * Codec for data-driven item model deserialization.
         */
        public static final MapCodec<CanisterSpecialRenderer.Unbaked> MAP_CODEC =
                MapCodec.unit(new CanisterSpecialRenderer.Unbaked());

        @Override
        public MapCodec<CanisterSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<GooData> bake(SpecialModelRenderer.BakingContext context) {
            return new CanisterSpecialRenderer();
        }
    }
}
