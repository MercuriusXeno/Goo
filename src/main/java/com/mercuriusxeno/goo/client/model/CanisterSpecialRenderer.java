package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.ber.CuboidBounds;
import com.mercuriusxeno.goo.client.ber.FluidFaceEmitter;
import com.mercuriusxeno.goo.client.ber.RenderCtx;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * Special item renderer for canister items. Renders the canister body model,
 * gasket caps, and fluid fill quads inside the canister geometry, matching
 * the in-world BER appearance. Reads goo contents from item data components.
 */
public class CanisterSpecialRenderer implements SpecialModelRenderer<CanisterSpecialRenderer.GooData> {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Copper endcap texture (default canister caps). */
    private static final Identifier COPPER_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /** Choral gasket texture (upgraded canister caps). */
    private static final Identifier CHORAL_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/choral_gasket.png");

    /** Fully opaque white in ARGB for untinted quad rendering. */
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    // -- Canister geometry (block coords, centered at 0.5 in XZ) --

    /** Canister half-width: 2px. */
    private static final float HW = 2f / 16f;

    /** Bottom of lower gasket (y=0). */
    private static final float GASKET_BOT = 0f;

    /** Top of lower gasket / bottom of body (y=1px). */
    private static final float BODY_BOT = 1f / 16f;

    /** Top of body / bottom of upper gasket (y=11px). */
    private static final float BODY_TOP = 11f / 16f;

    /** Top of upper gasket (y=12px). */
    private static final float GASKET_TOP = 12f / 16f;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    private static final float FLUID_INSET = 0.5f / 16f;

    /** Center of the canister in block coordinates. */
    private static final float CENTER = 8f / 16f;

    /** Min X/Z boundary of the canister body. */
    private static final float BODY_MIN_XZ = CENTER - HW;

    /** Max X/Z boundary of the canister body. */
    private static final float BODY_MAX_XZ = CENTER + HW;

    // -- Gasket UV: choral_gasket.png, 16x16 --

    /** Gasket side U start: column 4/16. */
    private static final float GS_U0 = 0.25f;

    /** Gasket side U end: column 8/16. */
    private static final float GS_U1 = 0.5f;

    /** Gasket side V end: row 1/16. */
    private static final float GS_V1 = 0.0625f;

    /** Creates a canister special renderer. */
    public CanisterSpecialRenderer() {
    }

    /**
     * Extracted render data from the canister item stack. Null fields
     * indicate an empty canister (no fluid to render).
     *
     * @param gooType     the dominant goo type, or null if empty
     * @param fill        the fill fraction [0, 1]
     * @param hasTopGasket whether a choral gasket is installed on top
     * @param hasBottomGasket whether a choral gasket is installed on bottom
     */
    public record GooData(@Nullable GooType gooType, float fill,
            boolean hasTopGasket, boolean hasBottomGasket) {
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
        GooContents contents = CanisterItem.getGooContents(stack);
        if (contents.isEmpty()) { return new GooData(null, 0f, hasTop, hasBottom); }
        float fill = computeFillFraction(stack, contents);
        return new GooData(contents.largestType(), fill, hasTop, hasBottom);
    }

    /**
     * Computes the fill fraction [0,1] for the canister's current contents.
     *
     * @param stack    the canister item stack
     * @param contents the goo contents to measure
     * @return fill fraction clamped to [0,1]
     */
    private static float computeFillFraction(ItemStack stack, GooContents contents) {
        int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(stack);
        long capacity = ContainerCapacity.canisterCapacity(compression);
        return Math.min(1f, (float) contents.totalVolume() / capacity);
    }

    /**
     * Renders the canister body, gaskets, and fluid fill for the item.
     *
     * @param data           extracted goo data (may be null for empty canisters)
     * @param poseStack      the current pose stack
     * @param nodeCollector  the render node collector
     * @param packedLight    packed light value
     * @param packedOverlay  packed overlay value
     * @param hasFoil        whether the item has enchantment foil
     * @param outlineColor   outline color for selected items
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
        if (data != null && data.gooType() != null && data.fill() > 0f) {
            submitFluid(poseStack, nodeCollector, packedLight, data.gooType(), data.fill());
        }
    }

    /**
     * Submits the baked canister body model at the item center position.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight the packed light value
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
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight the packed light value
     * @param data the goo data, or null
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
            RenderTypes.entitySolid(texture),
            (pose, c) -> emitEndcapQuads(new RenderCtx(pose, c, packedLight), top, bottom));
    }

    /**
     * Emits gasket box quads for the requested top and/or bottom endcaps.
     *
     * @param ctx    the render context
     * @param top    true to render the top endcap
     * @param bottom true to render the bottom endcap
     */
    private static void emitEndcapQuads(RenderCtx ctx, boolean top, boolean bottom) {
        if (top) {
            ctx.gasketBox(new CuboidBounds(BODY_MIN_XZ, BODY_MAX_XZ, BODY_MIN_XZ, BODY_MAX_XZ,
                BODY_TOP, GASKET_TOP), GS_U0, GS_U1, GS_V1);
        }
        if (bottom) {
            ctx.gasketBox(new CuboidBounds(BODY_MIN_XZ, BODY_MAX_XZ, BODY_MIN_XZ, BODY_MAX_XZ,
                GASKET_BOT, BODY_BOT), GS_U0, GS_U1, GS_V1);
        }
    }

    /**
     * Submits fluid surface geometry inside the canister body.
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
        CuboidBounds b = new CuboidBounds(
            CENTER - HW + FLUID_INSET, CENTER + HW - FLUID_INSET,
            CENTER - HW + FLUID_INSET, CENTER + HW - FLUID_INSET,
            BODY_BOT, BODY_BOT + fill * (BODY_TOP - BODY_BOT));
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> FluidFaceEmitter.emitFluidFaces(
                new RenderCtx(pose, c, packedLight), b, type));
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
        float x0 = CENTER - HW;
        float x1 = CENTER + HW;
        float z0 = CENTER - HW;
        float z1 = CENTER + HW;
        output.accept(new Vector3f(x0, GASKET_BOT, z0));
        output.accept(new Vector3f(x1, GASKET_TOP, z1));
    }

    /**
     * Unbaked factory for the canister special renderer. Registered as
     * "goo:canister_goo" in the special model renderer registry.
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<GooData> {

        /** Codec for data-driven item model deserialization. */
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
