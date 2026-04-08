package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders canisters attached to hub pipe slots. Each occupied slot
 * draws a canister body with gasket caps at the pipe center position,
 * hanging from the pipe level down to the hub base.
 *
 * <h3>Blockbench Slot Geometry Reference (pixel units, 1/16 block)</h3>
 * <p>Slots 0-5 have finalized geometry. Slots 6-7 (W, NW) are TBD.</p>
 * <pre>
 * Slot 0 (N, 0 deg):
 *   Pipe: [7,11.01,1.25]-[9.1,11.99,6.35] rot=0 origin=[8,11.5,2.75]
 *   Upper gasket: [6,10,0.25]-[10,11,4.25]   Body: [6,3,0.25]-[10,10,4.25]   Lower: [6,2,0.25]-[10,3,4.25]
 * Slot 1 (67.5 deg):
 *   Pipe: [2.75,11,3.9]-[4.85,11.98,9] rot=67.5 origin=[3.75,11.5,5.5]
 *   Upper: [1.25,10,3.25]-[5.25,11,7.25] rot=67.5   Body/Lower: same XZ, Y=3-10/2-3
 * Slot 2 (112.5 deg):
 *   Pipe: [2.7,11,8.9]-[4.8,11.98,14] rot=112.5 origin=[3.75,11.5,10.5]
 *   Upper: [1.25,10,8.75]-[5.25,11,12.75] rot=112.5   Body/Lower: same XZ, Y=3-10/2-3
 * Slot 3 (S, 180 deg):
 *   Pipe: [7,11.01,9.75]-[9.1,11.99,14.85] rot=0 origin=[8,11.5,13.25]
 *   Upper: [8,10,13.75]-[12,11,17.75] rot=-180   Body/Lower: same XZ, Y=3-10/2-3
 * Slot 4 (-115 deg / 245 deg):
 *   Pipe: [11.2,11,8.9]-[13.3,11.98,14] rot=-115 origin=[12.25,11.5,10.5]
 *   Upper: [10.75,10,8.75]-[14.75,11,12.75] rot=-115   Body/Lower: same XZ, Y=3-10/2-3
 * Slot 5 (-67.5 deg / 292.5 deg):
 *   Pipe: [11.15,11,3.9]-[13.25,11.98,9] rot=-67.5 origin=[12.25,11.5,5.5]
 *   Upper: [10.75,10,3.25]-[14.75,11,7.25] rot=-67.5   Body/Lower: same XZ, Y=3-10/2-3
 * Textures: gaskets=#0 (choral_gasket), body=#1 (canister_side)
 * UV caps: up=[8,4,4,0] down=[8,0,4,4] from #1
 * </pre>
 */
public class HubBlockEntityRenderer
        implements BlockEntityRenderer<HubBlockEntity, HubRenderState> {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Canister body side texture. */
    private static final Identifier CANISTER_SIDE =
        Identifier.fromNamespaceAndPath("goo", "textures/block/canister_side.png");

    /** Copper endcap texture (default canister caps). */
    private static final Identifier COPPER_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /** Choral gasket texture (upgraded canister caps). */
    private static final Identifier CHORAL_GASKET =
        Identifier.fromNamespaceAndPath("goo", "textures/block/choral_gasket.png");

    // -- Canister geometry (block coords) --

    /** Canister half-width: 2px. */
    private static final float HW = 2f / 16f;

    /** Bottom of lower gasket: rests on hub base (y=2px). */
    private static final float GASKET_BOT = 2f / 16f;

    /** Top of lower gasket / bottom of body (y=3px). */
    private static final float BODY_BOT = 3f / 16f;

    /** Top of body / bottom of upper gasket (y=13px). */
    private static final float BODY_TOP = 13f / 16f;

    /** Top of upper gasket: meets pipe bottom (y=14px). */
    private static final float GASKET_TOP = 14f / 16f;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    private static final float FLUID_INSET = 0.5f / 16f;

    // -- Body UV region: canister_side.png [0,0]-[4,10] on 16x16 --

    /** Body side U range: 4px / 16px = 0.25. */
    private static final float BODY_U1 = 0.25f;

    /** Body side V range: 10px / 16px = 0.625. */
    private static final float BODY_V1 = 0.625f;

    // -- Gasket UV regions: choral_gasket.png, 16x16 --

    /** Gasket side U start: column 4/16. */
    private static final float GS_U0 = 0.25f;

    /** Gasket side U end: column 8/16. */
    private static final float GS_U1 = 0.5f;

    /** Gasket side V end: row 1/16. */
    private static final float GS_V1 = 0.0625f;

    /** Canister center positions in block coords (XZ), indexed by slot. */
    private static final float[][] CENTERS = {
        { 8f / 16f,  2f / 16f},   // slot 0 (N)
        {13f / 16f,  3f / 16f},   // slot 1 (NE)
        {14f / 16f,  8f / 16f},   // slot 2 (E)
        {13f / 16f, 13f / 16f},   // slot 3 (SE)
        { 8f / 16f, 14f / 16f},   // slot 4 (S)
        { 3f / 16f, 13f / 16f},   // slot 5 (SW)
        { 2f / 16f,  8f / 16f},   // slot 6 (W)
        { 3f / 16f,  3f / 16f},   // slot 7 (NW)
    };

    /**
     * Creates a hub BER. Context is unused.
     *
     * @param context the renderer provider context
     */
    public HubBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public HubRenderState createRenderState() {
        return new HubRenderState();
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
    public void extractRenderState(HubBlockEntity be, HubRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        long gameTick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.animationTime = gameTick + partialTick;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            ItemStack stack = be.getCanister(i);
            state.hasCanister[i] = !stack.isEmpty();
            CanisterMetadata meta = CanisterItem.getMetadata(stack);
            state.hasTopGasket[i] = meta.topGasketId() != null;
            state.hasBottomGasket[i] = meta.bottomGasketId() != null;
            extractSlotFluid(be, state, i);
            extractSlotStream(be, state, i, gameTick);
        }
    }

    /**
     * Extracts dominant goo type and fill fraction for a single hub slot.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param slot the slot index
     */
    private static void extractSlotFluid(HubBlockEntity be, HubRenderState state, int slot) {
        ItemStack canister = be.getCanister(slot);
        int compression = GooEnchantments.getCompressionLevel(canister);
        GooContents contents = be.getSlotGooContents(slot);
        if (contents.isEmpty()) {
            state.slotType[slot] = null;
            state.slotFill[slot] = 0f;
            return;
        }
        long capacity = ContainerCapacity.canisterCapacity(compression);
        state.slotType[slot] = contents.largestType();
        state.slotFill[slot] = Math.min(1f, (float) contents.totalVolume() / capacity);
    }

    /**
     * Extracts active stream data for a single slot from the block entity.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param slot the slot index
     * @param gameTick the current game tick
     */
    private static void extractSlotStream(HubBlockEntity be,
            HubRenderState state, int slot, long gameTick) {
        state.streamType[slot] = be.getSlotStreamType(slot, gameTick);
        state.streamRate[slot] = be.getSlotStreamRate(slot, gameTick);
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
    public void submit(HubRenderState state, PoseStack poseStack,
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
    private static boolean hasAnyCanister(HubRenderState state) {
        for (boolean b : state.hasCanister) {
            if (b) { return true; }
        }
        return false;
    }

    /**
     * Batches all canister body sides into a single draw call.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    private static void submitBodies(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityCutout(CANISTER_SIDE),
            (pose, c) -> {
                for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
                    if (state.hasCanister[i]) { renderBodySides(pose, c, light, i); }
                }
            });
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
            SubmitNodeCollector nodeCollector, HubRenderState state) {
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

    /** Renders copper (non-choral) endcaps for all occupied hub slots.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer for copper endcap geometry
     * @param light packed light value
     * @param state the hub render state snapshot
     */
    private static void renderCopperEndcaps(PoseStack.Pose pose, VertexConsumer c,
            int light, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (!state.hasCanister[i]) { continue; }
            renderEndcaps(pose, c, light, i,
                !state.hasTopGasket[i], !state.hasBottomGasket[i]);
        }
    }

    /** Renders choral endcaps for all occupied hub slots.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer for choral endcap geometry
     * @param light packed light value
     * @param state the hub render state snapshot
     */
    private static void renderChoralEndcaps(PoseStack.Pose pose, VertexConsumer c,
            int light, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (!state.hasCanister[i]) { continue; }
            renderEndcaps(pose, c, light, i,
                state.hasTopGasket[i], state.hasBottomGasket[i]);
        }
    }

    /**
     * Renders the 4 side faces of a canister body at the given slot.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param slot the slot index
     */
    private static void renderBodySides(PoseStack.Pose pose, VertexConsumer c,
            int light, int slot) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
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
     * Returns true if any occupied slot has a non-choral (copper) cap on either end.
     *
     * @param state the block state
     * @return true if anyCopperCap is present
     */
    private static boolean hasAnyCopperCap(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
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
    private static boolean hasAnyChoralCap(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
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
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
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
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        if (!hasAnyFluid(state)) { return; }
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
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
    private static boolean hasAnyFluid(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.slotType[i] != null && state.slotFill[i] > 0f) { return true; }
        }
        return false;
    }

    /**
     * Renders fluid geometry for a single hub slot: top face + 4 side faces.
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
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
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
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        if (!hasAnyStream(state)) { return; }
        int light = state.lightCoords;
        float anim = state.animationTime;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
                    if (state.streamType[i] == null) { continue; }
                    float cx = CENTERS[i][0];
                    float cz = CENTERS[i][1];
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
    private static boolean hasAnyStream(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.streamType[i] != null) { return true; }
        }
        return false;
    }
}
