package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.TapBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the canister sitting on a tap's body slot. Follows the same
 * body + gasket + fluid pattern as HubBlockEntityRenderer, positioned
 * at the per-facing canister slot center.
 */
public class TapBlockEntityRenderer
        implements BlockEntityRenderer<TapBlockEntity, TapRenderState> {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Canister body side texture. */
    private static final Identifier CANISTER_SIDE =
        Identifier.fromNamespaceAndPath("goo", "textures/block/canister_side.png");

    /** Copper endcap texture (default canister caps). */
    private static final Identifier COPPER_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    // -- Canister geometry (block coords) --

    /** Canister half-width: 2px. */
    private static final float HW = 2f / 16f;

    /** Bottom of lower gasket: sits on top of tap body (Y=4px). */
    private static final float GASKET_BOT = 4f / 16f;

    /** Top of lower gasket / bottom of body (Y=5px). */
    private static final float BODY_BOT = 5f / 16f;

    /** Top of body / bottom of upper gasket (Y=15px). */
    private static final float BODY_TOP = 15f / 16f;

    /** Top of upper gasket (Y=16px). */
    private static final float GASKET_TOP = 16f / 16f;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    private static final float FLUID_INSET = 0.5f / 16f;

    /** Shared fluid geometry constants for tap canister slot. */
    private static final SlotFluidGeometry.SlotGeometry FLUID_GEOM =
        new SlotFluidGeometry.SlotGeometry(HW, BODY_BOT, BODY_TOP, FLUID_INSET);

    /** Divisor for computing AABB center from min+max. */
    private static final double CENTER_DIVISOR = 2.0;

    // -- Body UV region: canister_side.png [0,0]-[4,8] on 16x16 --

    /** Body side U range: 4px / 16px. */
    private static final float BODY_U1 = 0.25f;

    /** Body side V range: 10px / 16px. */
    private static final float BODY_V1 = 0.625f;

    // -- Gasket UV regions: gasket.png, 16x16 --

    /** Gasket side U start: column 4/16. */
    private static final float GS_U0 = 0.25f;

    /** Gasket side U end: column 8/16. */
    private static final float GS_U1 = 0.5f;

    /** Gasket side V end: row 1/16. */
    private static final float GS_V1 = 0.0625f;

    /**
     * Creates a tap BER.
     *
     * @param context the renderer provider context
     */
    public TapBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public TapRenderState createRenderState() {
        return new TapRenderState();
    }

    /**
     * Snapshots canister presence and fluid data from the block entity.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param partialTick the partial tick for interpolation
     * @param cameraPos the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(TapBlockEntity be, TapRenderState state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.facing = be.getBlockState().getValue(TapBlock.FACING);
        state.hasCanister = !be.getCanister().isEmpty();
        if (state.hasCanister) {
            extractCanisterContents(be.getCanister(), state);
        } else {
            clearContents(state);
        }
    }

    /**
     * Reads compression level and goo fill from the canister stack.
     *
     * @param canister the canister item stack
     * @param state the render state to populate
     */
    private static void extractCanisterContents(ItemStack canister, TapRenderState state) {
        state.matrices = GooEnchantments.getCompressionLevel(canister);
        GooContents contents = CanisterItem.getGooContents(canister);
        if (contents.isEmpty()) {
            clearContents(state);
        } else {
            extractNonEmptyContents(contents, state);
        }
    }

    /**
     * Populates goo type and fill ratio from non-empty canister contents.
     *
     * @param contents the goo contents
     * @param state the render state to populate
     */
    private static void extractNonEmptyContents(GooContents contents, TapRenderState state) {
        long capacity = ContainerCapacity.canisterCapacity(state.matrices);
        state.gooType = contents.largestType();
        state.fill = Math.min(1f, (float) contents.totalVolume() / capacity);
    }

    /**
     * Resets goo type and fill to empty defaults.
     *
     * @param state the render state to clear
     */
    private static void clearContents(TapRenderState state) {
        state.gooType = null;
        state.fill = 0f;
    }

    /**
     * Submits canister geometry if a canister is present.
     *
     * @param state the block state
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState the camera render state
     */
    @Override
    public void submit(TapRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (!state.hasCanister) { return; }
        AABB sb = TapBlock.canisterSlotShape(state.facing).bounds();
        float cx = (float) ((sb.minX + sb.maxX) / CENTER_DIVISOR);
        float cz = (float) ((sb.minZ + sb.maxZ) / CENTER_DIVISOR);
        submitAllParts(poseStack, nodeCollector, state, cx, cz);
    }

    /**
     * Submits body, gaskets, and optional fluid geometry for the canister.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the tap render state
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     */
    private static void submitAllParts(PoseStack poseStack, SubmitNodeCollector nodeCollector,
            TapRenderState state, float cx, float cz) {
        submitBody(poseStack, nodeCollector, state, cx, cz);
        submitGaskets(poseStack, nodeCollector, state, cx, cz);
        if (state.gooType != null && state.fill > 0f) {
            submitFluid(poseStack, nodeCollector, state, cx, cz);
        }
    }

    /**
     * Renders the 4 side faces of the canister body.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     */
    private static void submitBody(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, TapRenderState state,
            float cx, float cz) {
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityCutout(CANISTER_SIDE),
            (pose, c) -> {
                RenderContext ctx = new RenderContext(pose, c, light);
                CuboidBounds box = new CuboidBounds(cx - HW, cx + HW, cz - HW, cz + HW, BODY_BOT, BODY_TOP);
                ctx.emitSides(box, new GooRenderUtil.UvRect(0, 0, BODY_U1, BODY_V1));
            });
    }

    /**
     * Renders copper endcaps at top and bottom of the canister.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     */
    private static void submitGaskets(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, TapRenderState state,
            float cx, float cz) {
        int light = state.lightCoords;
        CuboidBounds base = tapBoundsXZ(cx, cz);
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entitySolid(COPPER_GASKET),
            (pose, c) -> renderGasketPair(new RenderContext(pose, c, light), base));
    }

    /**
     * Renders the top and bottom gasket endcaps for the tap.
     * @param ctx the render context
     * @param base the XZ cuboid bounds for the canister slot
     */
    private static void renderGasketPair(RenderContext ctx, CuboidBounds base) {
        ctx.gasketBox(base.withY(BODY_TOP, GASKET_TOP), GS_U0, GS_U1, GS_V1);
        ctx.gasketBox(base.withY(GASKET_BOT, BODY_BOT), GS_U0, GS_U1, GS_V1);
    }

    /**
     * Computes the XZ cuboid bounds for the tap at the given center.
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @return XZ cuboid bounds centered on the canister with Y zeroed
     */
    private static CuboidBounds tapBoundsXZ(float cx, float cz) {
        return new CuboidBounds(cx - HW, cx + HW, cz - HW, cz + HW, 0, 0);
    }

    /**
     * Renders the fluid surface inside the canister.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     */
    private static void submitFluid(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, TapRenderState state,
            float cx, float cz) {
        int light = state.lightCoords;
        GooType type = state.gooType;
        float fill = state.fill;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> renderFluidGeometry(new RenderContext(pose, c, light), type, fill, cx, cz));
    }

    /**
     * Emits top surface and four side faces for the fluid fill level.
     *
     * @param ctx  the render context
     * @param type the goo type for texture lookup
     * @param fill the fill ratio [0, 1]
     * @param cx   the center X in block coords
     * @param cz   the center Z in block coords
     */
    private static void renderFluidGeometry(RenderContext ctx, GooType type, float fill,
                                            float cx, float cz) {
        CuboidBounds b = SlotFluidGeometry.computeBounds(FLUID_GEOM, cx, cz, fill);
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        SlotFluidGeometry.renderFluidTop(ctx, b, sprite);
        SlotFluidGeometry.renderFluidSides(ctx, b, sprite, fill, FLUID_GEOM);
    }

}
