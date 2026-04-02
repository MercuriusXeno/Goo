package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.TapBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    /** Creates a tap BER. */
    public TapBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public TapRenderState createRenderState() {
        return new TapRenderState();
    }

    /** Snapshots canister presence and fluid data from the block entity. */
    @Override
    public void extractRenderState(TapBlockEntity be, TapRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.facing = be.getBlockState().getValue(TapBlock.FACING);
        ItemStack canister = be.getCanister();
        state.hasCanister = !canister.isEmpty();
        if (state.hasCanister) {
            int compression = GooEnchantments.getCompressionLevel(canister);
            state.matrices = compression;
            GooContents contents = CanisterItem.getGooContents(canister);
            if (!contents.isEmpty()) {
                long capacity = ContainerCapacity.canisterCapacity(compression);
                state.gooType = contents.largestType();
                state.fill = Math.min(1f, (float) contents.totalVolume() / capacity);
            } else {
                state.gooType = null;
                state.fill = 0f;
            }
        } else {
            state.gooType = null;
            state.fill = 0f;
        }
    }

    /** Submits canister geometry if a canister is present. */
    @Override
    public void submit(TapRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (!state.hasCanister) return;

        AABB slotBounds = TapBlock.canisterSlotShape(state.facing).bounds();
        float cx = (float) ((slotBounds.minX + slotBounds.maxX) / 2.0);
        float cz = (float) ((slotBounds.minZ + slotBounds.maxZ) / 2.0);

        submitBody(poseStack, nodeCollector, state, cx, cz);
        submitGaskets(poseStack, nodeCollector, state, cx, cz);
        if (state.gooType != null && state.fill > 0f) {
            submitFluid(poseStack, nodeCollector, state, cx, cz);
        }
    }

    /** Renders the 4 side faces of the canister body. */
    private static void submitBody(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, TapRenderState state,
            float cx, float cz) {
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityCutout(CANISTER_SIDE),
            (pose, c) -> {
                float x0 = cx - HW, x1 = cx + HW, z0 = cz - HW, z1 = cz + HW;
                CanisterGeometry.faceNorth(pose, c, light, x0, BODY_BOT, z0, x1, BODY_TOP, 0, BODY_U1, 0, BODY_V1);
                CanisterGeometry.faceSouth(pose, c, light, x0, BODY_BOT, z1, x1, BODY_TOP, 0, BODY_U1, 0, BODY_V1);
                CanisterGeometry.faceWest(pose, c, light, x0, BODY_BOT, z0, BODY_TOP, z1, 0, BODY_U1, 0, BODY_V1);
                CanisterGeometry.faceEast(pose, c, light, x1, BODY_BOT, z0, BODY_TOP, z1, 0, BODY_U1, 0, BODY_V1);
            });
    }

    /** Renders copper endcaps at top and bottom of the canister. */
    private static void submitGaskets(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, TapRenderState state,
            float cx, float cz) {
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entitySolid(COPPER_GASKET),
            (pose, c) -> {
                float x0 = cx - HW, x1 = cx + HW, z0 = cz - HW, z1 = cz + HW;
                CanisterGeometry.gasketBox(pose, c, light, x0, BODY_TOP, z0, x1, GASKET_TOP, z1, GS_U0, GS_U1, GS_V1);
                CanisterGeometry.gasketBox(pose, c, light, x0, GASKET_BOT, z0, x1, BODY_BOT, z1, GS_U0, GS_U1, GS_V1);
            });
    }

    /** Renders the fluid surface inside the canister. */
    private static void submitFluid(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, TapRenderState state,
            float cx, float cz) {
        int light = state.lightCoords;
        GooType type = state.gooType;
        float fill = state.fill;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                float x0 = cx - HW + FLUID_INSET, x1 = cx + HW - FLUID_INSET;
                float z0 = cz - HW + FLUID_INSET, z1 = cz + HW - FLUID_INSET;
                float y = BODY_BOT + fill * (BODY_TOP - BODY_BOT);

                TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
                float u0 = sprite.getU0(), u1 = sprite.getU1();
                float v0 = sprite.getV0(), v1 = sprite.getV1();
                float cuboidW = x1 - x0, cuboidD = z1 - z0;
                float su1 = u0 + (u1 - u0) * cuboidW;
                float sv1 = v0 + (v1 - v0) * cuboidD;

                GooRenderUtil.liquidSurface(pose, c, light, GooRenderUtil.OPAQUE_WHITE,
                    x0, z0, x1, z1, y, u0, su1, v0, sv1);
                float fillH = fill * (BODY_TOP - BODY_BOT);
                float sideVSpan = (v1 - v0) * fillH;
                float sideXU1 = u0 + (u1 - u0) * cuboidW;
                float sideZU1 = u0 + (u1 - u0) * cuboidD;
                CanisterGeometry.faceNorth(pose, c, light, x0, BODY_BOT, z0, x1, y, u0, sideXU1, v0, v0 + sideVSpan);
                CanisterGeometry.faceSouth(pose, c, light, x0, BODY_BOT, z1, x1, y, u0, sideXU1, v0, v0 + sideVSpan);
                CanisterGeometry.faceWest(pose, c, light, x0, BODY_BOT, z0, y, z1, u0, sideZU1, v0, v0 + sideVSpan);
                CanisterGeometry.faceEast(pose, c, light, x1, BODY_BOT, z0, y, z1, u0, sideZU1, v0, v0 + sideVSpan);
            });
    }
}
