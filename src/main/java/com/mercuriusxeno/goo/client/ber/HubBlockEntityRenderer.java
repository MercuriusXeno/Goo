package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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

    /** Y ranges describing where gasket boxes land in a hub slot. */
    private static final GasketCapRenderer.GasketYRanges GASKET_Y =
        new GasketCapRenderer.GasketYRanges(BODY_BOT, BODY_TOP, GASKET_BOT, GASKET_TOP);

    /** Gasket side UV region (uniform across container types). */
    private static final GasketCapRenderer.GasketUv GASKET_UV =
        new GasketCapRenderer.GasketUv(GS_U0, GS_U1, GS_V1);

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
            extractSlot(be, state, i, gameTick);
        }
    }

    /**
     * Extracts all render data for a single hub canister slot.
     *
     * @param be the block entity instance
     * @param state the render state snapshot
     * @param slot the slot index
     * @param gameTick the current game tick
     */
    private static void extractSlot(HubBlockEntity be, HubRenderState state,
            int slot, long gameTick) {
        ItemStack stack = be.getCanister(slot);
        state.canisterPresent[slot] = !stack.isEmpty();
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        state.topGasketPresent[slot] = meta.topGasketId() != null;
        state.bottomGasketPresent[slot] = meta.bottomGasketId() != null;
        extractSlotFluid(be, state, slot);
        extractSlotStream(be, state, slot, gameTick);
    }

    /**
     * Extracts dominant goo type and fill fraction for a single hub slot.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param slot the slot index
     */
    private static void extractSlotFluid(HubBlockEntity be, HubRenderState state, int slot) {
        CanisterFluidContent content = be.getSlotFluidContent(slot);
        if (content.isEmpty()) {
            state.slotType[slot] = null;
            state.slotFill[slot] = 0f;
            return;
        }
        populateFilledSlot(be, state, slot, content);
    }

    /**
     * Populates render state for a slot with goo contents.
     * @param be the block entity instance
     * @param state the render state snapshot
     * @param slot the slot index
     * @param content the non-empty fluid content for this slot
     */
    private static void populateFilledSlot(HubBlockEntity be, HubRenderState state,
            int slot, CanisterFluidContent content) {
        long capacity = ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(be.getCanister(slot)));
        state.slotType[slot] = content.getGooType();
        state.slotFill[slot] = Math.min(1f, (float) content.amount() / capacity);
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
        state.streamType[slot] = be.containerState().getSlotStreamType(slot, gameTick);
        state.streamRate[slot] = be.containerState().getSlotStreamRate(slot, gameTick);
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
        HubFluidRenderer.submitBodies(poseStack, nodeCollector, state);
        submitGaskets(poseStack, nodeCollector, state);
        HubFluidRenderer.submitFluids(poseStack, nodeCollector, state);
        HubFluidRenderer.submitStreams(poseStack, nodeCollector, state);
    }

    /**
     * Returns true if any slot has a canister.
     *
     * @param state the block state
     * @return true if anyCanister is present
     */
    private static boolean hasAnyCanister(HubRenderState state) {
        for (boolean b : state.canisterPresent) {
            if (b) { return true; }
        }
        return false;
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
        if (hasAnyCopperCap(state)) {
            submitCopperCaps(poseStack, nodeCollector, light, state);
        }
        if (GasketCapRenderer.hasAnyCap(state.topGasketPresent, state.bottomGasketPresent)) {
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
            SubmitNodeCollector nodeCollector, int light, HubRenderState state) {
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
            SubmitNodeCollector nodeCollector, int light, HubRenderState state) {
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entitySolid(CHORAL_GASKET),
            (pose, c) -> renderChoralEndcaps(new RenderContext(pose, c, light), state));
    }

    /** Renders copper (non-choral) endcaps for all occupied hub slots.
     *
     * @param ctx   the render context
     * @param state the hub render state snapshot
     */
    private static void renderCopperEndcaps(RenderContext ctx, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (!state.canisterPresent[i]) { continue; }
            renderEndcaps(ctx, i,
                !state.topGasketPresent[i], !state.bottomGasketPresent[i]);
        }
    }

    /** Renders choral endcaps for all occupied hub slots.
     *
     * @param ctx   the render context
     * @param state the hub render state snapshot
     */
    private static void renderChoralEndcaps(RenderContext ctx, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (!state.canisterPresent[i]) { continue; }
            renderEndcaps(ctx, i,
                state.topGasketPresent[i], state.bottomGasketPresent[i]);
        }
    }

    /**
     * Returns true if any occupied slot has a non-choral (copper) cap on either end.
     *
     * @param state the block state
     * @return true if anyCopperCap is present
     */
    private static boolean hasAnyCopperCap(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.canisterPresent[i]
                    && (!state.topGasketPresent[i] || !state.bottomGasketPresent[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Delegates to {@link GasketCapRenderer#renderEndcaps} with this container's
     * Y ranges and UV constants.
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
     * Computes the XZ cuboid bounds for a hub slot at index. Hub centers are
     * already in block-local space, so they are passed to
     * {@link GasketCapRenderer#slotBoundsXZ} unchanged.
     *
     * @param slot the slot index in the hub ring
     * @return XZ cuboid bounds centered on the slot with Y zeroed
     */
    private static CuboidBounds slotBoundsXZ(int slot) {
        return GasketCapRenderer.slotBoundsXZ(CENTERS[slot][0], CENTERS[slot][1], HW);
    }
}
