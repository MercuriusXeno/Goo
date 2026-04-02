package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.HubBlock;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.TapBlockEntity;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
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

    private SlotOutlineRenderer() {}

    /** Translucent green color for placement preview wireframe. */
    private static final int PREVIEW_COLOR = ARGB.color(180, 100, 255, 100);

    /**
     * Intercepts outline extraction for hub and canister blocks,
     * adding a custom renderer that highlights only the targeted slot.
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
        }
    }

    // --- Hub ---

    /** Adds a custom renderer for the hub: frame + targeted occupied slot + preview. */
    private static void addHubRenderer(ExtractBlockOutlineRenderStateEvent event) {
        BlockHitResult hit = event.getHitResult();
        BlockPos pos = event.getBlockPos();
        VoxelShape outlineShape = computeHubOutline(hit, pos, event);
        AABB preview = computeHubPreview(hit, pos, event);
        event.addCustomRenderer(slotRenderer(outlineShape, preview));
    }

    /** Computes the hub outline: frame + the single occupied slot the player is aiming at. */
    private static VoxelShape computeHubOutline(
            BlockHitResult hit, BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        int slot = HubBlock.hitSlot(hit, pos);
        if (slot < 0) {
            return HubBlock.frameShape();
        }
        if (event.getLevel().getBlockEntity(pos) instanceof HubBlockEntity be
                && !be.getCanister(slot).isEmpty()) {
            return Shapes.or(HubBlock.frameShape(), HubBlock.slotShape(slot));
        }
        return HubBlock.frameShape();
    }

    /**
     * Computes the placement preview bounds for a hub slot by projecting
     * the player's view ray onto the hit face plane of the block.
     */
    private static @Nullable AABB computeHubPreview(
            BlockHitResult hit, BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        if (!isPlayerHoldingCanister()) return null;
        if (!(event.getLevel().getBlockEntity(pos) instanceof HubBlockEntity be)) return null;
        int slot = projectToHubSlot(hit, pos);
        if (slot < 0 || !be.getCanister(slot).isEmpty()) return null;
        return HubBlock.slotShape(slot).bounds();
    }

    /** Finds the nearest hub slot from the hit result's contact point. */
    private static int projectToHubSlot(BlockHitResult hit, BlockPos pos) {
        Vec3 loc = hit.getLocation();
        double px = (loc.x - pos.getX()) * 16.0;
        double pz = (loc.z - pos.getZ()) * 16.0;
        return HubBlock.nearestSlot(px, pz);
    }

    // --- Canister ---

    /** Adds a custom renderer for the canister block: targeted slot + preview + punch progress. */
    private static void addCanisterRenderer(ExtractBlockOutlineRenderStateEvent event) {
        BlockHitResult hit = event.getHitResult();
        BlockPos pos = event.getBlockPos();
        VoxelShape outlineShape = computeCanisterOutline(hit, pos, event);
        AABB preview = computeCanisterPreview(hit, pos, event);
        AABB punchBounds = computePunchProgressBounds(pos);
        float punchProgress = punchBounds != null ? CanisterPunchListener.getProgress() : 0f;
        event.addCustomRenderer(slotRendererWithPunch(
                outlineShape, preview, punchBounds, punchProgress));
    }

    /** Computes the canister outline: just the single slot the player is aiming at. */
    private static VoxelShape computeCanisterOutline(
            BlockHitResult hit, BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        int slot = CanisterBlock.hitSlot(hit, pos);
        if (slot < 0) {
            return CanisterBlock.slotShape(4);
        }
        return CanisterBlock.slotShape(slot);
    }

    /**
     * Computes the placement preview bounds when directly hitting an occupied
     * canister slot. Uses face-offset resolution to find the adjacent empty slot.
     * Pass-through previews (ray through empty space) are handled by
     * {@link CanisterPlacementOverlay}.
     */
    private static @Nullable AABB computeCanisterPreview(
            BlockHitResult hit, BlockPos pos, ExtractBlockOutlineRenderStateEvent event) {
        if (!isPlayerHoldingCanister()) return null;
        if (!(event.getLevel().getBlockEntity(pos) instanceof CanisterBlockEntity be)) return null;

        int slot = CanisterItem.resolveInsertionSlot(
                hit.getLocation(), pos, hit.getDirection(), be);
        if (slot < 0) return null;
        return CanisterBlock.slotShape(slot).bounds();
    }

    /**
     * Returns the AABB of the slot being punch-held, or null if no punch is active on this block.
     */
    private static @Nullable AABB computePunchProgressBounds(BlockPos pos) {
        if (!CanisterPunchListener.isActive()) return null;
        if (!pos.equals(CanisterPunchListener.getActivePos())) return null;
        return CanisterBlock.slotShape(CanisterPunchListener.getActiveSlot()).bounds();
    }

    // --- Tap ---

    /** Adds a custom renderer for the tap: standard outline + canister slot wireframe preview. */
    private static void addTapRenderer(ExtractBlockOutlineRenderStateEvent event) {
        BlockPos pos = event.getBlockPos();
        VoxelShape outlineShape = event.getBlockState().getShape(event.getLevel(), pos);
        AABB preview = computeTapPreview(pos, event.getBlockState(), event);
        event.addCustomRenderer(slotRenderer(outlineShape, preview));
    }

    /**
     * Returns canister slot bounds as a placement preview when the player holds
     * a canister and the tap's canister slot is empty.
     */
    private static @Nullable AABB computeTapPreview(
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            ExtractBlockOutlineRenderStateEvent event) {
        if (!isPlayerHoldingCanister()) return null;
        if (!(event.getLevel().getBlockEntity(pos) instanceof TapBlockEntity tap)) return null;
        if (!tap.getCanister().isEmpty()) return null;
        net.minecraft.core.Direction facing = state.getValue(TapBlock.FACING);
        return TapBlock.canisterSlotShape(facing).bounds();
    }

    // --- Rendering ---

    /** Returns true if the local player is holding a canister item in their main hand. */
    private static boolean isPlayerHoldingCanister() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem().getItem() instanceof CanisterItem;
    }

    /**
     * Creates a custom outline renderer that draws the given shape
     * with standard block outline styling, plus an optional placement preview.
     */
    private static CustomBlockOutlineRenderer slotRenderer(
            VoxelShape shape, @Nullable AABB preview) {
        return (renderState, bufferSource, poseStack, translucent, levelRenderState) ->
                renderOutline(renderState, bufferSource, poseStack, translucent,
                        levelRenderState, shape, preview);
    }

    /**
     * Creates a custom outline renderer with slot outline, placement preview,
     * and an optional red wireframe for punch-hold progress.
     */
    private static CustomBlockOutlineRenderer slotRendererWithPunch(
            VoxelShape shape, @Nullable AABB preview,
            @Nullable AABB punchBounds, float punchProgress) {
        return (renderState, bufferSource, poseStack, translucent, levelRenderState) -> {
            renderOutline(renderState, bufferSource, poseStack, translucent,
                    levelRenderState, shape, preview);
            if (punchBounds != null && punchProgress > 0f) {
                Vec3 camPos = levelRenderState.cameraRenderState.pos;
                renderPunchProgress(poseStack, bufferSource,
                        punchBounds, renderState.pos(), camPos, punchProgress);
                bufferSource.endLastBatch();
            }
            return true;
        };
    }

    /**
     * Renders a custom outline shape using the same style as vanilla block outlines.
     * If a preview AABB is provided, also draws a wireframe cuboid for placement preview.
     *
     * @return true to suppress vanilla outline rendering
     */
    private static boolean renderOutline(
            BlockOutlineRenderState renderState,
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            boolean translucent,
            LevelRenderState levelRenderState,
            VoxelShape shape,
            @Nullable AABB preview) {
        if (renderState.isTranslucent() != translucent) {
            return true;
        }
        Vec3 camPos = levelRenderState.cameraRenderState.pos;
        BlockPos pos = renderState.pos();

        renderSelectionOutline(renderState, bufferSource, poseStack, shape, pos, camPos);

        if (preview != null) {
            renderPreviewWireframe(poseStack, bufferSource, preview, pos, camPos);
        }

        bufferSource.endLastBatch();
        return true;
    }

    /** Renders the standard selection outline (with high-contrast support). */
    private static void renderSelectionOutline(
            BlockOutlineRenderState renderState,
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            VoxelShape shape, BlockPos pos, Vec3 camPos) {
        if (renderState.highContrast()) {
            renderShapeOutline(poseStack, bufferSource, RenderTypes.secondaryBlockOutline(),
                    shape, pos, camPos, -16777216, 7.0F);
        }

        int color = renderState.highContrast() ? -11010079 : ARGB.black(102);
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        renderShapeOutline(poseStack, bufferSource, RenderTypes.lines(),
                shape, pos, camPos, color, lineWidth);
    }

    /**
     * Renders a red wireframe on the targeted slot that intensifies with punch progress.
     * Alpha transitions from faint (80) to vivid (255) as progress goes from 0 to 1.
     */
    private static void renderPunchProgress(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            AABB bounds, BlockPos pos, Vec3 camPos, float progress) {
        int alpha = (int) (80 + 175 * progress);
        int color = ARGB.color(alpha, 255, 50, 50);
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lines());
        double ox = pos.getX() - camPos.x;
        double oy = pos.getY() - camPos.y;
        double oz = pos.getZ() - camPos.z;
        renderWireframeCuboid(poseStack, consumer,
                bounds.minX + ox, bounds.minY + oy, bounds.minZ + oz,
                bounds.maxX + ox, bounds.maxY + oy, bounds.maxZ + oz,
                color, lineWidth);
    }

    /** Renders a wireframe placement preview for an empty slot. */
    private static void renderPreviewWireframe(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            AABB bounds, BlockPos pos, Vec3 camPos) {
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lines());
        double ox = pos.getX() - camPos.x;
        double oy = pos.getY() - camPos.y;
        double oz = pos.getZ() - camPos.z;
        renderWireframeCuboid(poseStack, consumer,
                bounds.minX + ox, bounds.minY + oy, bounds.minZ + oz,
                bounds.maxX + ox, bounds.maxY + oy, bounds.maxZ + oz,
                PREVIEW_COLOR, lineWidth);
    }

    /**
     * Draws 12 edges of an axis-aligned cuboid using manual line rendering.
     * No VoxelShape involved: coordinates are camera-relative world space.
     */
    static void renderWireframeCuboid(
            PoseStack poseStack, VertexConsumer consumer,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            int color, float lineWidth) {
        // Bottom face
        emitEdge(poseStack, consumer, x0, y0, z0, x1, y0, z0, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y0, z0, x1, y0, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y0, z1, x0, y0, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x0, y0, z1, x0, y0, z0, color, lineWidth);
        // Top face
        emitEdge(poseStack, consumer, x0, y1, z0, x1, y1, z0, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y1, z0, x1, y1, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y1, z1, x0, y1, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x0, y1, z1, x0, y1, z0, color, lineWidth);
        // Vertical pillars
        emitEdge(poseStack, consumer, x0, y0, z0, x0, y1, z0, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y0, z0, x1, y1, z0, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y0, z1, x1, y1, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x0, y0, z1, x0, y1, z1, color, lineWidth);
    }

    /** Emits a single line segment (two vertices with direction-based normal and line width). */
    static void emitEdge(
            PoseStack poseStack, VertexConsumer consumer,
            double ax, double ay, double az,
            double bx, double by, double bz,
            int color, float lineWidth) {
        float dx = (float) (bx - ax);
        float dy = (float) (by - ay);
        float dz = (float) (bz - az);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0) { dx /= len; dy /= len; dz /= len; }

        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, (float) ax, (float) ay, (float) az)
                .setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        consumer.addVertex(pose, (float) bx, (float) by, (float) bz)
                .setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
    }

    /** Delegates to ShapeRenderer with camera-relative coordinates. */
    private static void renderShapeOutline(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            RenderType renderType,
            VoxelShape shape,
            BlockPos pos,
            Vec3 camPos,
            int color,
            float lineWidth) {
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        ShapeRenderer.renderShape(
                poseStack, consumer, shape,
                pos.getX() - camPos.x,
                pos.getY() - camPos.y,
                pos.getZ() - camPos.z,
                color, lineWidth);
    }
}
