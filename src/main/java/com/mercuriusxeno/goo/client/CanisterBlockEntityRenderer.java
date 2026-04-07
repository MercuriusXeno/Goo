package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders canisters in the 3x3 grid of a multi-canister block. Each occupied
 * slot draws a canister body (4x10x4 px) with gasket caps (4x1x4 px top and bottom)
 * at the grid position defined by the slot layout.
 */
public class CanisterBlockEntityRenderer
        implements BlockEntityRenderer<CanisterBlockEntity, CanisterRenderState> {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Copper endcap texture (default canister caps). */
    private static final Identifier COPPER_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /** Choral gasket texture (upgraded canister caps). */
    private static final Identifier CHORAL_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/choral_gasket.png");

    // -- Canister geometry (block coords) --

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

    /** Model center offset: baked models are centered at (8/16, 8/16) in XZ. */
    private static final float MODEL_CENTER = 0.5f;

    // -- Gasket UV: choral_gasket.png, 16x16 --

    /** Gasket side U start: column 4/16. */
    private static final float GS_U0 = 0.25f;

    /** Gasket side U end: column 8/16. */
    private static final float GS_U1 = 0.5f;

    /** Gasket side V end: row 1/16. */
    private static final float GS_V1 = 0.0625f;

    /** Pixels per block for coordinate conversion. */
    private static final float BLOCK_PIXELS = 16f;
    /** Full white color for baked quad rendering. */
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    /**
     * Creates a canister BER. Context is unused.
     *
     * @param context the renderer provider context
     */
    public CanisterBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public CanisterRenderState createRenderState() {
        return new CanisterRenderState();
    }

    /**
     * Snapshots canister presence, fluid fill, and stream state from the block entity.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param partialTick the partial tick for interpolation
     * @param cameraPos the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(CanisterBlockEntity be, CanisterRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        long gameTick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.animationTime = gameTick + partialTick;
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            ItemStack stack = be.getCanister(i);
            state.hasCanister[i] = !stack.isEmpty();
            extractSlotFluid(be, state, i);
            CanisterMetadata meta = CanisterItem.getMetadata(stack);
            state.hasTopGasket[i] = meta.topGasketId() != null;
            state.hasBottomGasket[i] = meta.bottomGasketId() != null;
            extractSlotStream(be, state, i, gameTick);
        }
    }

    /**
     * Extracts active stream data for a single slot from the block entity.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param slot the slot index
     * @param gameTick the current game tick
     */
    private static void extractSlotStream(CanisterBlockEntity be,
            CanisterRenderState state, int slot, long gameTick) {
        state.streamType[slot] = be.getSlotStreamType(slot, gameTick);
        state.streamRate[slot] = be.getSlotStreamRate(slot, gameTick);
    }

    /**
     * Extracts dominant goo type and fill fraction for a single slot.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param slot the slot index
     */
    private static void extractSlotFluid(CanisterBlockEntity be,
            CanisterRenderState state, int slot) {
        ItemStack canister = be.getCanister(slot);
        int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(canister);
        GooContents contents = be.getSlotGooContents(slot);
        if (contents.isEmpty()) {
            state.slotType[slot] = null;
            state.slotFill[slot] = 0f;
            return;
        }
        long capacity = ContainerCapacity.canisterCapacity(compression);
        state.slotType[slot] = contents.largestType();
        state.slotFill[slot] = logFill(contents.totalVolume(), capacity);
    }

    /**
     * Computes fill fraction as total volume divided by capacity.
     * Clamped to [0, 1].
     *
     * @param amount the current amount
     * @param capacity the maximum capacity
     * @return the result
     */
    private static float logFill(long amount, long capacity) {
        if (amount <= 0 || capacity <= 0) { return 0f; }
        return Math.min(1f, (float) amount / capacity);
    }

    /**
     * Submits canister geometry for all occupied slots.
     *
     * @param state the block state
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState the camera render state
     */
    @Override
    public void submit(CanisterRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (!hasAnyCanister(state)) { return; }
        submitBodies(poseStack, nodeCollector, state);
        submitGaskets(poseStack, nodeCollector, state);
        submitFluids(poseStack, nodeCollector, state);
        submitStreams(poseStack, nodeCollector, state);
    }

    /**
     * Returns true if any slot has a canister.
     *
     * @param state the block state
     * @return true if anyCanister is present
     */
    private static boolean hasAnyCanister(CanisterRenderState state) {
        for (boolean b : state.hasCanister) {
            if (b) { return true; }
        }
        return false;
    }

    /**
     * Submits baked body models for each occupied slot at its grid position.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    private static void submitBodies(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CanisterRenderState state) {
        int light = state.lightCoords;
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.hasCanister[i]) { continue; }
            float cx = CanisterSlotLayout.SLOT_CENTERS[i][0] / BLOCK_PIXELS;
            float cz = CanisterSlotLayout.SLOT_CENTERS[i][1] / BLOCK_PIXELS;
            QuadCollection model = CanisterBodyModels.getModel();
            poseStack.pushPose();
            poseStack.translate(cx - MODEL_CENTER, 0, cz - MODEL_CENTER);
            nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> renderBakedQuads(pose, c, light, model));
            poseStack.popPose();
        }
    }

    /**
     * Renders all quads from a baked model.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param model the baked quad collection
     */
    private static void renderBakedQuads(PoseStack.Pose pose, VertexConsumer c,
            int light, QuadCollection model) {
        QuadInstance qi = new QuadInstance();
        qi.setColor(OPAQUE_WHITE);
        qi.setLightCoords(light);
        qi.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (BakedQuad quad : model.getAll()) {
            c.putBakedQuad(pose, quad, qi);
        }
    }

    /**
     * Submits endcap geometry for all occupied slots. Every canister always gets
     * top and bottom caps - copper by default, choral when upgraded. Two draw
     * calls batch each texture separately.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    private static void submitGaskets(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CanisterRenderState state) {
        int light = state.lightCoords;
        // Copper endcaps: caps that are NOT choral-upgraded
        if (hasAnyCopperCap(state)) {
            nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entitySolid(COPPER_GASKET),
                (pose, c) -> renderCopperEndcaps(pose, c, light, state));
        }
        // Choral gaskets: upgraded caps
        if (hasAnyChoralCap(state)) {
            nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entitySolid(CHORAL_GASKET),
                (pose, c) -> renderChoralEndcaps(pose, c, light, state));
        }
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
            if (!state.hasCanister[i]) { continue; }
            renderEndcaps(pose, c, light, i,
                !state.hasTopGasket[i], !state.hasBottomGasket[i]);
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
            if (!state.hasCanister[i]) { continue; }
            renderEndcaps(pose, c, light, i,
                state.hasTopGasket[i], state.hasBottomGasket[i]);
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
            if (state.hasCanister[i]
                    && (!state.hasTopGasket[i] || !state.hasBottomGasket[i])) {
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
            if (state.hasTopGasket[i] || state.hasBottomGasket[i]) { return true; }
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
    private static void submitFluids(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CanisterRenderState state) {
        if (!hasAnyFluid(state)) { return; }
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
                    if (state.slotType[i] != null && state.slotFill[i] > 0f) {
                        renderFluidSurface(pose, c, light, i,
                            state.slotType[i], state.slotFill[i]);
                    }
                }
            });
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
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param slot the slot index
     * @param type the goo type
     * @param fill the fill fraction in [0, 1]
     */
    private static void renderFluidSurface(PoseStack.Pose pose, VertexConsumer c,
            int light, int slot, GooType type, float fill) {
        float cx = CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS;
        float cz = CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS;
        float x0 = cx - HW + FLUID_INSET;
        float x1 = cx + HW - FLUID_INSET;
        float z0 = cz - HW + FLUID_INSET;
        float z1 = cz + HW - FLUID_INSET;
        float y = BODY_BOT + fill * (BODY_TOP - BODY_BOT);

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        float cuboidWidth = x1 - x0;
        float cuboidDepth = z1 - z0;
        float su1 = u0 + (u1 - u0) * cuboidWidth;
        float sv1 = v0 + (v1 - v0) * cuboidDepth;

        GooRenderUtil.liquidSurface(pose, c, light, GooRenderUtil.OPAQUE_WHITE,
            x0, z0, x1, z1, y, u0, su1, v0, sv1);
        float fillHeight = fill * (BODY_TOP - BODY_BOT);
        float sideVSpan = (v1 - v0) * fillHeight;
        float sideXU1 = u0 + (u1 - u0) * cuboidWidth;
        float sideZU1 = u0 + (u1 - u0) * cuboidDepth;
        CanisterGeometry.faceNorth(pose, c, light, x0, BODY_BOT, z0, x1, y, u0, sideXU1, v0, v0 + sideVSpan);
        CanisterGeometry.faceSouth(pose, c, light, x0, BODY_BOT, z1, x1, y, u0, sideXU1, v0, v0 + sideVSpan);
        CanisterGeometry.faceWest(pose, c, light, x0, BODY_BOT, z0, y, z1, u0, sideZU1, v0, v0 + sideVSpan);
        CanisterGeometry.faceEast(pose, c, light, x1, BODY_BOT, z0, y, z1, u0, sideZU1, v0, v0 + sideVSpan);
    }

    // -- Stream rendering --

    /**
     * Batches all active stream cuboids into a single translucent draw call.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    private static void submitStreams(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CanisterRenderState state) {
        if (!hasAnyStream(state)) { return; }
        int light = state.lightCoords;
        float anim = state.animationTime;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
                    if (state.streamType[i] == null) { continue; }
                    float cx = CanisterSlotLayout.SLOT_CENTERS[i][0] / BLOCK_PIXELS;
                    float cz = CanisterSlotLayout.SLOT_CENTERS[i][1] / BLOCK_PIXELS;
                    float yTop = BODY_TOP;
                    float yBottom = BODY_BOT + state.slotFill[i] * (BODY_TOP - BODY_BOT);
                    GooStreamRenderer.renderStream(pose, c, light,
                        cx, cz, yTop, yBottom,
                        state.streamType[i], state.streamRate[i], anim);
                }
            });
    }

    /**
     * Returns true if any slot has an active stream.
     *
     * @param state the block state
     * @return true if anyStream is present
     */
    private static boolean hasAnyStream(CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (state.streamType[i] != null) { return true; }
        }
        return false;
    }

}
