package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import com.mercuriusxeno.goo.client.model.CanisterBodyModels;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
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

    /** Model center offset: baked models are centered at (8/16, 8/16) in XZ. */
    private static final float MODEL_CENTER = 0.5f;

    /** Pixels per block for coordinate conversion. */
    private static final float BLOCK_PIXELS = 16f;
    /** Y coordinate of the canister body top (11 pixels up). */
    private static final float STREAM_Y_TOP = 11f / 16f;
    /** Y coordinate of the canister body bottom (1 pixel up). */
    private static final float STREAM_Y_BOT = 1f / 16f;
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
            extractSlot(be, state, i, gameTick);
        }
    }

    /**
     * Extracts all render data for a single canister slot.
     *
     * @param be the block entity instance
     * @param state the render state snapshot
     * @param slot the slot index
     * @param gameTick the current game tick
     */
    private static void extractSlot(CanisterBlockEntity be, CanisterRenderState state,
            int slot, long gameTick) {
        ItemStack stack = be.getCanister(slot);
        state.canisterPresent[slot] = !stack.isEmpty();
        extractSlotFluid(be, state, slot);
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        state.topGasketPresent[slot] = meta.topGasketId() != null;
        state.bottomGasketPresent[slot] = meta.bottomGasketId() != null;
        extractSlotStream(be, state, slot, gameTick);
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
        state.streamType[slot] = be.containerState().getSlotStreamType(slot, gameTick);
        state.streamRate[slot] = be.containerState().getSlotStreamRate(slot, gameTick);
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
        CanisterFluidContent content = be.getSlotFluidContent(slot);
        if (content.isEmpty()) {
            state.slotType[slot] = null;
            state.slotFill[slot] = 0f;
        } else {
            populateFilledSlot(be, state, slot, content);
        }
    }

    /**
     * Populates render state for a slot with goo contents.
     * @param be the block entity instance
     * @param state the render state snapshot
     * @param slot the slot index
     * @param content the non-empty fluid content for this slot
     */
    private static void populateFilledSlot(CanisterBlockEntity be,
            CanisterRenderState state, int slot, CanisterFluidContent content) {
        long cap = ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(be.getCanister(slot)));
        state.slotType[slot] = content.getGooType();
        state.slotFill[slot] = logFill(content.amount(), cap);
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
        CanisterFluidRenderer.submitGaskets(poseStack, nodeCollector, state);
        CanisterFluidRenderer.submitFluids(poseStack, nodeCollector, state);
        submitStreams(poseStack, nodeCollector, state);
    }

    /**
     * Returns true if any slot has a canister.
     *
     * @param state the block state
     * @return true if anyCanister is present
     */
    private static boolean hasAnyCanister(CanisterRenderState state) {
        for (boolean b : state.canisterPresent) {
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
            if (!state.canisterPresent[i]) { continue; }
            submitSlotBody(poseStack, nodeCollector, light, i);
        }
    }

    /**
     * Submits a single baked canister body at the given slot's grid position.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param light the packed light value
     * @param slot the slot index
     */
    private static void submitSlotBody(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int light, int slot) {
        float cx = CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS;
        float cz = CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS;
        poseStack.pushPose();
        poseStack.translate(cx - MODEL_CENTER, 0, cz - MODEL_CENTER);
        submitBodyModel(poseStack, nodeCollector, light);
        poseStack.popPose();
    }

    /**
     * Emits the baked canister body model at the current pose position.
     * @param poseStack     the current pose transformation stack
     * @param nodeCollector the render node collector for geometry submission
     * @param light         the packed light level for shading
     */
    private static void submitBodyModel(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int light) {
        QuadCollection model = CanisterBodyModels.getModel();
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> renderBakedQuads(pose, c, light, model));
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
            (pose, c) -> renderAllStreams(new RenderContext(pose, c, light), anim, state));
    }

    /**
     * Renders stream segments for all active slots in a single batch.
     *
     * @param ctx   the render context
     * @param anim  the animation tick fraction
     * @param state the render state snapshot
     */
    private static void renderAllStreams(RenderContext ctx, float anim, CanisterRenderState state) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (state.streamType[i] == null) { continue; }
            renderSlotStream(ctx, anim, state, i);
        }
    }

    /**
     * Renders a single slot's goo stream segment.
     * @param ctx the render context
     * @param anim the animation tick fraction
     * @param state the render state snapshot
     * @param slot the slot index
     */
    private static void renderSlotStream(RenderContext ctx, float anim, CanisterRenderState state, int slot) {
        float cx = CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS;
        float cz = CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS;
        float yBottom = STREAM_Y_BOT + state.slotFill[slot] * (STREAM_Y_TOP - STREAM_Y_BOT);
        GooStreamRenderer.renderStream(ctx,
            cx, cz, STREAM_Y_TOP, yBottom,
            state.streamType[slot], state.streamRate[slot], anim);
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
