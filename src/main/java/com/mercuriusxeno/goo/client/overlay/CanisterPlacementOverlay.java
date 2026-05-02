package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.CutawayInteractionHelper;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterPlacementValidator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-side overlay that renders a green wireframe placement preview when
 * the player holds a canister and aims at a solid surface. Shows where the
 * new canister block would be placed and which slot would be targeted.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CanisterPlacementOverlay {

    /**
     * Pixels per block for hit-location to pixel-space conversion.
     */
    private static final double PIXELS_PER_BLOCK = 16.0;

    /**
     * Translucent green color for the placement preview.
     */
    private static final int PREVIEW_COLOR = ARGB.color(180, 100, 255, 100);

    /**
     * Cached new-block placement target, updated each tick.
     */
    private static @Nullable PlacementTarget cachedPlacement;

    private CanisterPlacementOverlay() {
    }

    /**
     * Returns the current new-block placement target, or null.
     *
     * @return the cachedPlacement
     */
    public static @Nullable PlacementTarget getCachedPlacement() {
        return cachedPlacement;
    }

    /**
     * Recomputes the placement preview target each client tick.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        cachedPlacement = computePlacement();
    }

    /**
     * Computes placement target. When the player aims at a surface and the
     * placement position already has a canister block, shows the insertion
     * preview on it. Otherwise shows new-block placement preview.
     *
     * @return the placement target, or null if placement is not valid
     */
    private static @Nullable PlacementTarget computePlacement() {
        Minecraft mc = Minecraft.getInstance();
        if (!isHoldingCanister(mc)) {
            return null;
        }
        BlockHitResult bhr = getValidBlockHit(mc);
        if (bhr == null) {
            return null;
        }
        BlockState hitState = mc.level.getBlockState(bhr.getBlockPos());
        if (isGooMachineBlock(hitState.getBlock())) {
            return null;
        }
        if (isMachineInteraction(mc, hitState, bhr)) {
            return null;
        }

        BlockPos placePos = bhr.getBlockPos().relative(bhr.getDirection());
        return resolveTarget(mc, bhr, placePos);
    }

    /**
     * Returns true if a non-sneaking player clicked a machine's interactive region.
     *
     * @param mc       the Minecraft client instance
     * @param hitState the block state at the hit position
     * @param bhr      the block hit result
     * @return true if the hit targets a machine hollow
     */
    private static boolean isMachineInteraction(Minecraft mc, BlockState hitState, BlockHitResult bhr) {
        if (mc.player != null && mc.player.isSecondaryUseActive()) {
            return false;
        }
        return isMachineHollowInteraction(hitState, bhr, hitState.getBlock(), bhr.getBlockPos());
    }

    private static boolean isMachineHollowInteraction(BlockState hitState, BlockHitResult bhr, Block block, BlockPos pos) {
        return (block instanceof PlexerBlock && CutawayInteractionHelper.isCutawayClick(hitState, pos, bhr))
                || (block instanceof ReactorBlock && ReactorBlock.isHollowClick(hitState, pos, bhr));
    }

    /**
     * Returns true if the local player exists and is holding a canister in their main hand.
     *
     * @param mc the Minecraft client instance
     * @return true if the player is holding a canister
     */
    private static boolean isHoldingCanister(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && mc.level != null
                && player.getMainHandItem().getItem() instanceof CanisterItem;
    }

    /**
     * Returns the current block hit result if it is a non-miss block target.
     *
     * @param mc the Minecraft client instance
     * @return the block hit result, or null if the crosshair is not targeting a block
     */
    private static @Nullable BlockHitResult getValidBlockHit(Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult bhr)) {
            return null;
        }
        if (bhr.getType() == HitResult.Type.MISS) {
            return null;
        }
        return bhr;
    }

    /**
     * Returns true if the block is a canister, hub, or tap (not valid placement targets).
     *
     * @param block the block to check
     * @return true if the block is a canister, hub, or tap
     */
    private static boolean isGooMachineBlock(Block block) {
        return block instanceof CanisterBlock
                || block instanceof HubBlock
                || block instanceof TapBlock;
    }

    /**
     * Resolves the placement target at placePos, handling insertion and new-block cases.
     *
     * @param mc       the Minecraft client instance
     * @param bhr      the block hit result from the aimed surface
     * @param placePos the block position where the new canister would be placed
     * @return the resolved placement target, or null if placement is invalid
     */
    private static @Nullable PlacementTarget resolveTarget(
            Minecraft mc, BlockHitResult bhr, BlockPos placePos) {
        if (mc.level.getBlockState(placePos).getBlock() instanceof CanisterBlock) {
            return computeInsertionTarget(mc, bhr, placePos);
        }
        if (!mc.level.getBlockState(placePos).canBeReplaced()) {
            return null;
        }
        int slot = slotFromHitLocation(bhr, placePos);
        if (!CanisterPlacementValidator.isSlotAllowed(mc.level, placePos, slot)) {
            return null;
        }
        return new PlacementTarget(placePos, slot);
    }

    /**
     * Computes which empty slot to preview when the placement position
     * already has a canister block. Uses nearest-slot from hit coordinates.
     *
     * @param mc          the Minecraft client instance
     * @param bhr         the block hit result
     * @param canisterPos the canister block position
     * @return the insertion target for the nearest empty slot, or null if none available
     */
    private static @Nullable PlacementTarget computeInsertionTarget(
            Minecraft mc, BlockHitResult bhr, BlockPos canisterPos) {
        if (!(mc.level.getBlockEntity(canisterPos) instanceof CanisterBlockEntity be)) {
            return null;
        }
        Vec3 loc = bhr.getLocation();
        float px = (float) ((loc.x - canisterPos.getX()) * PIXELS_PER_BLOCK);
        float pz = (float) ((loc.z - canisterPos.getZ()) * PIXELS_PER_BLOCK);
        int slot = CanisterSlotLayout.nearestSlot(px, pz);
        if (slot < 0 || !be.getCanister(slot).isEmpty()) {
            return null;
        }
        if (!CanisterPlacementValidator.isSlotAllowed(mc.level, canisterPos, slot)) {
            return null;
        }
        return new PlacementTarget(canisterPos, slot);
    }

    /**
     * Computes the canister grid cell from the BlockHitResult location.
     * The hit location is on the solid block's face, which shares a plane
     * with the new block's entry face.
     *
     * @param bhr      the block hit result
     * @param placePos the placement block position
     * @return the grid slot index for the hit location
     */
    private static int slotFromHitLocation(BlockHitResult bhr, BlockPos placePos) {
        Vec3 loc = bhr.getLocation();
        float px = (float) ((loc.x - placePos.getX()) * PIXELS_PER_BLOCK);
        float pz = (float) ((loc.z - placePos.getZ()) * PIXELS_PER_BLOCK);
        Direction entryFace = bhr.getDirection().getOpposite();
        return CanisterSlotLayout.placementSlot(entryFace, px, pz);
    }

    /**
     * Hooks into the block outline event to render the new-block placement preview.
     * Adds a non-suppressing renderer so the vanilla outline still draws.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onExtractOutline(ExtractBlockOutlineRenderStateEvent event) {
        Block block = event.getBlockState().getBlock();
        if (block instanceof CanisterBlock || block instanceof HubBlock
                || block instanceof TapBlock) {
            return;
        }

        if (cachedPlacement != null) {
            AABB bounds = CanisterBlock.slotShape(cachedPlacement.slot()).bounds();
            event.addCustomRenderer(previewRendererAt(cachedPlacement.pos(), bounds));
        }
    }

    /**
     * Creates a renderer that draws a wireframe at the given position and bounds.
     *
     * @param pos    the block position
     * @param bounds the axis-aligned bounding box
     * @return the custom outline renderer
     */
    private static net.neoforged.neoforge.client.CustomBlockOutlineRenderer previewRendererAt(
            BlockPos pos, AABB bounds) {
        return (renderState, bufferSource, poseStack, translucent, levelRenderState) ->
                renderPreview(renderState, bufferSource, poseStack, translucent,
                        levelRenderState, pos, bounds);
    }

    /**
     * Renders the wireframe preview at the target block position.
     *
     * @param renderState      the block outline render state
     * @param bufferSource     the buffer source for rendering
     * @param poseStack        the pose stack for rendering
     * @param translucent      whether the current pass is translucent
     * @param levelRenderState the level render state
     * @param targetPos        the target block position
     * @param bounds           the axis-aligned bounding box
     * @return false always (does not suppress other outline renderers)
     */
    private static boolean renderPreview(
            BlockOutlineRenderState renderState,
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            boolean translucent,
            LevelRenderState levelRenderState,
            BlockPos targetPos, AABB bounds) {
        if (renderState.isTranslucent() != translucent) {
            return false;
        }

        Vec3 camPos = levelRenderState.cameraRenderState.pos;
        double ox = targetPos.getX() - camPos.x;
        double oy = targetPos.getY() - camPos.y;
        double oz = targetPos.getZ() - camPos.z;

        emitPreviewWireframe(bufferSource, poseStack, bounds, ox, oy, oz);
        return false;
    }

    /**
     * Draws the translucent green wireframe box at the camera-relative offset.
     *
     * @param bufferSource the buffer source for line rendering
     * @param poseStack    the pose stack for rendering
     * @param bounds       the slot bounding box in block-local coords
     * @param ox           the camera-relative X offset of the target block
     * @param oy           the camera-relative Y offset of the target block
     * @param oz           the camera-relative Z offset of the target block
     */
    private static void emitPreviewWireframe(MultiBufferSource.BufferSource bufferSource,
                                             PoseStack poseStack, AABB bounds, double ox, double oy, double oz) {
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(RenderTypes.lines()));
        ctx.emitWireframe(new CuboidBounds(
                        (float) (bounds.minX + ox), (float) (bounds.maxX + ox),
                        (float) (bounds.minZ + oz), (float) (bounds.maxZ + oz),
                        (float) (bounds.minY + oy), (float) (bounds.maxY + oy)),
                PREVIEW_COLOR, lineWidth);
        bufferSource.endLastBatch();
    }

    /**
     * A placement target: block position and slot index.
     *
     * @param pos  the target block position
     * @param slot the target slot index
     */
    public record PlacementTarget(BlockPos pos, int slot) {
    }
}
