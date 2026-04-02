package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import com.mercuriusxeno.goo.block.HubBlock;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

    private CanisterPlacementOverlay() {}

    /** A placement target: block position and slot index. */
    public record PlacementTarget(BlockPos pos, int slot) {}

    /** Translucent green color for the placement preview. */
    private static final int PREVIEW_COLOR = ARGB.color(180, 100, 255, 100);

    /** Cached new-block placement target, updated each tick. */
    private static @Nullable PlacementTarget cachedPlacement;

    /** Returns the current new-block placement target, or null. */
    public static @Nullable PlacementTarget getCachedPlacement() {
        return cachedPlacement;
    }

    /** Recomputes the placement preview target each client tick. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        cachedPlacement = computePlacement();
    }

    /**
     * Computes placement target. When the player aims at a surface and the
     * placement position already has a canister block, shows the insertion
     * preview on it. Otherwise shows new-block placement preview.
     */
    private static @Nullable PlacementTarget computePlacement() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;
        if (!(player.getMainHandItem().getItem() instanceof CanisterItem)) return null;
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return null;
        if (bhr.getType() == HitResult.Type.MISS) return null;

        Block clickedBlock = mc.level.getBlockState(bhr.getBlockPos()).getBlock();
        if (clickedBlock instanceof CanisterBlock || clickedBlock instanceof HubBlock
                || clickedBlock instanceof TapBlock) return null;

        BlockPos placePos = bhr.getBlockPos().relative(bhr.getDirection());

        if (mc.level.getBlockState(placePos).getBlock() instanceof CanisterBlock) {
            return computeInsertionTarget(mc, bhr, placePos);
        }

        if (!mc.level.getBlockState(placePos).canBeReplaced()) return null;
        int slot = slotFromHitLocation(bhr, placePos);
        return new PlacementTarget(placePos, slot);
    }

    /**
     * Computes which empty slot to preview when the placement position
     * already has a canister block. Uses nearest-slot from hit coordinates.
     */
    private static @Nullable PlacementTarget computeInsertionTarget(
            Minecraft mc, BlockHitResult bhr, BlockPos canisterPos) {
        if (!(mc.level.getBlockEntity(canisterPos) instanceof CanisterBlockEntity be)) return null;
        Vec3 loc = bhr.getLocation();
        float px = (float) ((loc.x - canisterPos.getX()) * 16.0);
        float pz = (float) ((loc.z - canisterPos.getZ()) * 16.0);
        int slot = CanisterSlotLayout.nearestSlot(px, pz);
        if (slot < 0 || !be.getCanister(slot).isEmpty()) return null;
        return new PlacementTarget(canisterPos, slot);
    }

    /**
     * Computes the canister grid cell from the BlockHitResult location.
     * The hit location is on the solid block's face, which shares a plane
     * with the new block's entry face.
     */
    private static int slotFromHitLocation(BlockHitResult bhr, BlockPos placePos) {
        Vec3 loc = bhr.getLocation();
        float px = (float) ((loc.x - placePos.getX()) * 16.0);
        float pz = (float) ((loc.z - placePos.getZ()) * 16.0);
        Direction entryFace = bhr.getDirection().getOpposite();
        return CanisterSlotLayout.placementSlot(entryFace, px, pz);
    }

    /**
     * Hooks into the block outline event to render the new-block placement preview.
     * Adds a non-suppressing renderer so the vanilla outline still draws.
     */
    @SubscribeEvent
    public static void onExtractOutline(ExtractBlockOutlineRenderStateEvent event) {
        Block block = event.getBlockState().getBlock();
        if (block instanceof CanisterBlock || block instanceof HubBlock
                || block instanceof TapBlock) return;

        if (cachedPlacement != null) {
            AABB bounds = CanisterBlock.slotShape(cachedPlacement.slot()).bounds();
            event.addCustomRenderer(previewRendererAt(cachedPlacement.pos(), bounds));
        }
    }

    /** Creates a renderer that draws a wireframe at the given position and bounds. */
    private static net.neoforged.neoforge.client.CustomBlockOutlineRenderer previewRendererAt(
            BlockPos pos, AABB bounds) {
        return (renderState, bufferSource, poseStack, translucent, levelRenderState) ->
            renderPreview(renderState, bufferSource, poseStack, translucent,
                    levelRenderState, pos, bounds);
    }

    /** Renders the wireframe preview at the target block position. */
    private static boolean renderPreview(
            BlockOutlineRenderState renderState,
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            boolean translucent,
            LevelRenderState levelRenderState,
            BlockPos targetPos, AABB bounds) {
        if (renderState.isTranslucent() != translucent) return false;

        Vec3 camPos = levelRenderState.cameraRenderState.pos;
        double ox = targetPos.getX() - camPos.x;
        double oy = targetPos.getY() - camPos.y;
        double oz = targetPos.getZ() - camPos.z;

        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lines());
        SlotOutlineRenderer.renderWireframeCuboid(poseStack, consumer,
                bounds.minX + ox, bounds.minY + oy, bounds.minZ + oz,
                bounds.maxX + ox, bounds.maxY + oy, bounds.maxZ + oz,
                PREVIEW_COLOR, lineWidth);
        bufferSource.endLastBatch();
        return false;
    }
}
