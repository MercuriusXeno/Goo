package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.TapBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.model.CanisterGeometry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
            (pose, c) -> renderBodyFaces(pose, c, light, cx, cz));
    }

    /**
     * Emits the four side faces of the canister body.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     */
    private static void renderBodyFaces(PoseStack.Pose pose, VertexConsumer c, int light, float cx, float cz) {
        float x0 = cx - HW;
        float x1 = cx + HW;
        float z0 = cz - HW;
        float z1 = cz + HW;
        CanisterGeometry.faceNorth(pose, c, light, x0, BODY_BOT, z0, x1, BODY_TOP, 0, BODY_U1, 0, BODY_V1);
        CanisterGeometry.faceSouth(pose, c, light, x0, BODY_BOT, z1, x1, BODY_TOP, 0, BODY_U1, 0, BODY_V1);
        CanisterGeometry.faceWest(pose, c, light, x0, BODY_BOT, z0, BODY_TOP, z1, 0, BODY_U1, 0, BODY_V1);
        CanisterGeometry.faceEast(pose, c, light, x1, BODY_BOT, z0, BODY_TOP, z1, 0, BODY_U1, 0, BODY_V1);
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
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entitySolid(COPPER_GASKET),
            (pose, c) -> renderGasketBoxes(pose, c, light, cx, cz));
    }

    /**
     * Emits upper and lower gasket endcaps.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     */
    private static void renderGasketBoxes(PoseStack.Pose pose, VertexConsumer c, int light, float cx, float cz) {
        float x0 = cx - HW;
        float x1 = cx + HW;
        float z0 = cz - HW;
        float z1 = cz + HW;
        CanisterGeometry.gasketBox(pose, c, light, x0, BODY_TOP, z0, x1, GASKET_TOP, z1, GS_U0, GS_U1, GS_V1);
        CanisterGeometry.gasketBox(pose, c, light, x0, GASKET_BOT, z0, x1, BODY_BOT, z1, GS_U0, GS_U1, GS_V1);
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
            (pose, c) -> renderFluidGeometry(pose, c, light, type, fill, cx, cz));
    }

    /**
     * Emits top surface and four side faces for the fluid fill level.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param type the goo type for texture lookup
     * @param fill the fill ratio [0, 1]
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     */
    private static void renderFluidGeometry(PoseStack.Pose pose, VertexConsumer c,
            int light, GooType type, float fill, float cx, float cz) {
        float x0 = cx - HW + FLUID_INSET;
        float x1 = cx + HW - FLUID_INSET;
        float z0 = cz - HW + FLUID_INSET;
        float z1 = cz + HW - FLUID_INSET;
        float y = BODY_BOT + fill * (BODY_TOP - BODY_BOT);

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        renderFluidTop(pose, c, light, sprite, x0, z0, x1, z1, y);
        renderFluidSides(pose, c, light, sprite, x0, z0, x1, z1, y, fill);
    }

    /**
     * Renders the horizontal liquid surface quad.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param sprite the fluid texture sprite
     * @param x0 the minimum X bound
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param z1 the maximum Z bound
     * @param y the Y coordinate of the surface
     */
    private static void renderFluidTop(PoseStack.Pose pose, VertexConsumer c,
            int light, TextureAtlasSprite sprite,
            float x0, float z0, float x1, float z1, float y) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float su1 = u0 + (sprite.getU1() - u0) * (x1 - x0);
        float sv1 = v0 + (sprite.getV1() - v0) * (z1 - z0);
        GooRenderUtil.liquidSurface(pose, c, light, GooRenderUtil.OPAQUE_WHITE,
                x0, z0, x1, z1, y, u0, su1, v0, sv1);
    }

    /**
     * Renders four side faces of the fluid column from body bottom to fill height.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param sprite the fluid texture sprite
     * @param x0 the minimum X bound
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param z1 the maximum Z bound
     * @param y the Y coordinate of the fill level
     * @param fill the fill ratio [0, 1]
     */
    private static void renderFluidSides(PoseStack.Pose pose, VertexConsumer c,
            int light, TextureAtlasSprite sprite,
            float x0, float z0, float x1, float z1, float y, float fill) {
        float[] uv = computeSideUvs(sprite, x1 - x0, z1 - z0, fill);
        CanisterGeometry.faceNorth(pose, c, light, x0, BODY_BOT, z0, x1, y, uv[UV_U0], uv[UV_X_U1], uv[UV_V0], uv[UV_V1]);
        CanisterGeometry.faceSouth(pose, c, light, x0, BODY_BOT, z1, x1, y, uv[UV_U0], uv[UV_X_U1], uv[UV_V0], uv[UV_V1]);
        CanisterGeometry.faceWest(pose, c, light, x0, BODY_BOT, z0, y, z1, uv[UV_U0], uv[UV_Z_U1], uv[UV_V0], uv[UV_V1]);
        CanisterGeometry.faceEast(pose, c, light, x1, BODY_BOT, z0, y, z1, uv[UV_U0], uv[UV_Z_U1], uv[UV_V0], uv[UV_V1]);
    }

    /** Side UV array indices. */
    private static final int UV_U0 = 0;
    private static final int UV_X_U1 = 1;
    private static final int UV_V0 = 2;
    private static final int UV_V1 = 3;
    private static final int UV_Z_U1 = 4;

    /**
     * Computes side face UV coordinates: [u0, sideXU1, v0, v0+vSpan, sideZU1].
     *
     * @param sprite the fluid texture sprite
     * @param cuboidW the width of the fluid cuboid
     * @param cuboidD the depth of the fluid cuboid
     * @param fill the fill ratio [0, 1]
     * @return array of [u0, sideXU1, v0, v0+sideVSpan, sideZU1]
     */
    private static float[] computeSideUvs(TextureAtlasSprite sprite, float cuboidW, float cuboidD, float fill) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float uRange = sprite.getU1() - u0;
        float vRange = sprite.getV1() - v0;
        float sideVSpan = vRange * fill * (BODY_TOP - BODY_BOT);
        return new float[]{u0, u0 + uRange * cuboidW, v0, v0 + sideVSpan, u0 + uRange * cuboidD};
    }
}
