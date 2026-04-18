package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.HubBlock;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.ReactorBlock;
import com.mercuriusxeno.goo.block.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.TapBlockEntity;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterSlotResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import org.jspecify.annotations.Nullable;

/**
 * Custom block outline renderer for hub and canister blocks.
 * Draws only the targeted slot's outline instead of the full selection shape,
 * breaking the circular dependency between getShape and raycast resolution.
 * Also renders a wireframe placement preview when the player holds a canister.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SlotOutlineRenderer {
    /** Block-local coordinate to pixel conversion factor. */
    private static final double BLOCK_PIXELS = 16.0;

    /** Default slot index for canister (center slot). */
    private static final int DEFAULT_CANISTER_SLOT = 4;

    private SlotOutlineRenderer() {}

    /**
     * Intercepts outline extraction for hub and canister blocks,
     * adding a custom renderer that highlights only the targeted slot.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onExtractOutline(ExtractBlockOutlineRenderStateEvent event) {
        Block block = event.getBlockState().getBlock();
        if (block instanceof HubBlock) {
            addHubRenderer(event);
        } else if (block instanceof CanisterBlock) {
            addCanisterRenderer(event);
        } else if (block instanceof TapBlock) {
            addTapRenderer(event);
        } else if (block instanceof ReactorBlock) {
            addReactorRenderer(event);
        }
    }

    // --- Hub ---

    /**
     * Adds a custom renderer for the hub: frame + targeted occupied slot + preview.
     *
     * @param event the event instance
     */
    private static void addHubRenderer(ExtractBlockOutlineRenderStateEvent event) {
        BlockHitResult hit = event.getHitResult();
        BlockPos pos = event.getBlockPos();
        VoxelShape outlineShape = computeHubOutline(hit, pos, event);
        AABB preview = computeHubPreview(hit, pos, event);
        event.addCustomRenderer(slotRenderer(outlineShape, preview));
    }

    /**
     * Computes the hub outline: frame + the single occupied slot the player is aiming at.
     *
     * @param hit the block hit result
     * @param pos the block position
     * @param event the event instance
     * @return the computed hubOutline
     */
    private static VoxelShape computeHubOutline(
            BlockHitResult hit, BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        int slot = HubBlock.hitSlot(hit, pos);
        if (slot < 0) { return HubBlock.frameShape(); }
        if (event.getLevel().getBlockEntity(pos) instanceof HubBlockEntity be
                && !be.getCanister(slot).isEmpty()) {
            return Shapes.or(HubBlock.frameShape(), HubBlock.slotShape(slot));
        }
        return HubBlock.frameShape();
    }

    /**
     * Computes the placement preview bounds for a hub slot by projecting
     * the player's view ray onto the hit face plane of the block.
     *
     * @param hit the block hit result
     * @param pos the block position
     * @param event the event instance
     * @return the computed hubPreview
     */
    private static @Nullable AABB computeHubPreview(
            BlockHitResult hit, BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        if (!isPlayerHoldingCanister()) { return null; }
        if (!(event.getLevel().getBlockEntity(pos) instanceof HubBlockEntity be)) { return null; }
        int slot = projectToHubSlot(hit, pos);
        if (slot < 0 || !be.getCanister(slot).isEmpty()) { return null; }
        return HubBlock.slotShape(slot).bounds();
    }

    /**
     * Finds the nearest hub slot from the hit result's contact point.
     *
     * @param hit the block hit result
     * @param pos the block position
     * @return the result
     */
    private static int projectToHubSlot(BlockHitResult hit, BlockPos pos) {
        Vec3 loc = hit.getLocation();
        double px = (loc.x - pos.getX()) * BLOCK_PIXELS;
        double pz = (loc.z - pos.getZ()) * BLOCK_PIXELS;
        return HubBlock.nearestSlot(px, pz);
    }

    // --- Canister ---

    /**
     * Adds a custom renderer for the canister block: targeted slot + preview.
     *
     * @param event the event instance
     */
    private static void addCanisterRenderer(ExtractBlockOutlineRenderStateEvent event) {
        BlockHitResult hit = event.getHitResult();
        BlockPos pos = event.getBlockPos();
        VoxelShape outlineShape = computeCanisterOutline(hit, pos, event);
        AABB preview = computeCanisterPreview(hit, pos, event);
        event.addCustomRenderer(slotRenderer(outlineShape, preview));
    }

    /**
     * Computes the canister outline: just the single slot the player is aiming at.
     *
     * @param hit the block hit result
     * @param pos the block position
     * @param event the event instance
     * @return the computed canisterOutline
     */
    private static VoxelShape computeCanisterOutline(
            BlockHitResult hit, BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        int slot = CanisterBlock.hitSlot(hit, pos);
        if (slot < 0) {
            return event.getBlockState().getShape(event.getLevel(), pos);
        }
        return CanisterBlock.slotShape(slot);
    }

    /**
     * Computes the placement preview bounds when directly hitting an occupied
     * canister slot. Uses face-offset resolution to find the adjacent empty slot.
     * Pass-through previews (ray through empty space) are handled by
     * {@link CanisterPlacementOverlay}.
     *
     * @param hit the block hit result
     * @param pos the block position
     * @param event the event instance
     * @return the computed canisterPreview
     */
    private static @Nullable AABB computeCanisterPreview(
            BlockHitResult hit, BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        if (!isPlayerHoldingCanister()) { return null; }
        if (!(event.getLevel().getBlockEntity(pos) instanceof CanisterBlockEntity be)) { return null; }

        int slot = CanisterSlotResolver.resolveAndConstrain(
                hit.getLocation(), pos, hit.getDirection(), be);
        if (slot < 0) { return null; }
        return CanisterBlock.slotShape(slot).bounds();
    }

    // --- Tap ---

    /**
     * Adds a custom renderer for the tap: standard outline + canister slot wireframe preview.
     *
     * @param event the event instance
     */
    private static void addTapRenderer(ExtractBlockOutlineRenderStateEvent event) {
        BlockPos pos = event.getBlockPos();
        VoxelShape outlineShape = event.getBlockState().getShape(event.getLevel(), pos);
        AABB preview = computeTapPreview(pos, event.getBlockState(), event);
        event.addCustomRenderer(slotRenderer(outlineShape, preview));
    }

    /**
     * Returns canister slot bounds as a placement preview when the player holds
     * a canister and the tap's canister slot is empty.
     *
     * @param pos the block position
     * @param state the block state
     * @param event the event instance
     * @return the computed tapPreview
     */
    private static @Nullable AABB computeTapPreview(
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            ExtractBlockOutlineRenderStateEvent event) {
        if (!isPlayerHoldingCanister()) { return null; }
        if (!(event.getLevel().getBlockEntity(pos) instanceof TapBlockEntity tap)) { return null; }
        if (!tap.getCanister().isEmpty()) { return null; }
        net.minecraft.core.Direction facing = state.getValue(TapBlock.FACING);
        return TapBlock.canisterSlotShape(facing).bounds();
    }

    // --- Rendering ---

    /**
     * Adds a custom renderer for the reactor: standard outline + output
     * canister slot wireframe preview when holding a canister.
     *
     * @param event the event instance
     */
    private static void addReactorRenderer(ExtractBlockOutlineRenderStateEvent event) {
        BlockPos pos = event.getBlockPos();
        VoxelShape outlineShape = event.getBlockState().getShape(event.getLevel(), pos);
        AABB preview = computeReactorPreview(pos, event);
        event.addCustomRenderer(slotRenderer(outlineShape, preview));
    }

    /**
     * Returns the output canister slot bounds as a placement preview
     * when the player holds a canister and the slot is empty.
     *
     * @param pos   the block position
     * @param event the event instance
     * @return the preview bounds, or null
     */
    private static @Nullable AABB computeReactorPreview(
            BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        if (!isPlayerHoldingCanister()) { return null; }
        if (!(event.getLevel().getBlockEntity(pos) instanceof ReactorBlockEntity reactor)) {
            return null;
        }
        if (!reactor.getOutputCanister().isEmpty()) { return null; }
        net.minecraft.core.Direction facing =
                event.getBlockState().getValue(ReactorBlock.FACING);
        return ReactorBlock.outputSlotShape(facing).bounds();
    }

    /**
     * Returns true if the local player is holding a canister item in their main hand.
     *
     * @return true if playerHoldingCanister
     */
    private static boolean isPlayerHoldingCanister() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem().getItem() instanceof CanisterItem;
    }

    /**
     * Creates a custom outline renderer that draws the given shape
     * with standard block outline styling, plus an optional placement preview.
     *
     * @param shape the voxel shape to render
     * @param preview the placement preview bounds, or null
     * @return the custom outline renderer
     */
    private static CustomBlockOutlineRenderer slotRenderer(
            VoxelShape shape, @Nullable AABB preview) {
        return (renderState, bufferSource, poseStack, translucent, levelRenderState) ->
                SlotOutlineDrawing.renderOutline(renderState, bufferSource, poseStack, translucent,
                        levelRenderState, shape, preview);
    }

}
