package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.gasket.ChoralTunerItem;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Renders translucent overlay quads on gasket face regions when the player
 * holds a choral tuner and looks at a gasket-capable block. Blue = RECEIVER,
 * orange = TRANSMITTER. Only the single region under the crosshair is shown.
 * Connected gaskets display diagonal warning stripes in the fog.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GasketOverlayRenderer {
    /** Translucent blue for receiver (input/cap) regions. */
    private static final int RECEIVER_COLOR = ARGB.color(100, 80, 140, 255);

    /** Translucent orange for transmitter (output/base) regions. */
    private static final int TRANSMITTER_COLOR = ARGB.color(100, 255, 160, 40);

    /** Stripe color for connected gaskets: darker variant with higher alpha. */
    private static final int RECEIVER_STRIPE = ARGB.color(160, 40, 80, 200);
    private static final int TRANSMITTER_STRIPE = ARGB.color(160, 200, 100, 10);

    /**
     * Small expansion applied to overlay AABBs so faces sit above the block
     * geometry instead of coplanar with it, preventing z-fighting flicker.
     */
    private static final double OVERLAY_EPSILON = 0.002;

    /** Wireframe color for receiver outlines (bright blue). */
    private static final int RECEIVER_WIRE = ARGB.color(200, 80, 140, 255);

    /** Wireframe color for transmitter outlines (bright orange). */
    private static final int TRANSMITTER_WIRE = ARGB.color(200, 255, 160, 40);

    private GasketOverlayRenderer() {}

    /**
     * Main event handler: renders gasket overlays when the player holds a
     * choral tuner and looks at a gasket-capable block.
     *
     * @param event the render event fired after opaque features
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Minecraft mc = Minecraft.getInstance();
        if (!isGasketOverlayActive(mc)) { return; }

        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof IGasketHolder holder)) { return; }

        renderGasketOverlay(mc, event, hit, pos, be, holder);
    }

    /**
     * Renders fill, wireframe, and connection overlays for a valid gasket target.
     *
     * @param mc the Minecraft instance
     * @param event the render event
     * @param hit the block hit result
     * @param pos the block position
     * @param be the block entity
     * @param holder the gasket holder
     */
    private static void renderGasketOverlay(
            Minecraft mc, RenderLevelStageEvent.AfterOpaqueFeatures event,
            BlockHitResult hit, BlockPos pos, BlockEntity be, IGasketHolder holder) {
        AABB bounds = resolveOverlayBounds(be, holder, hit);
        if (bounds == null) { return; }

        GasketRole role = holder.resolveRole(hit);
        int slot = holder.resolveSlot(hit);
        boolean connected = holder.getPartner(role, slot) != null;

        renderFillOverlay(mc, event, pos, bounds, role, connected);
        renderWireframeOverlay(mc, event, pos, bounds, role);
        renderConnectionIfLinked(mc, event, pos, bounds, role, slot, holder, connected);
    }

    /**
     * Checks whether the gasket overlay should render: player exists, holds a
     * choral tuner, and is looking at a block.
     *
     * @param mc the Minecraft instance
     * @return true if overlay rendering should proceed
     */
    private static boolean isGasketOverlayActive(Minecraft mc) {
        if (mc.player == null || mc.level == null) { return false; }
        if (!(mc.player.getMainHandItem().getItem() instanceof ChoralTunerItem)) { return false; }
        return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    /**
     * Resolves the inflated overlay bounds for the targeted gasket region,
     * or null if the target is invalid (unsupported role, empty slot, etc.).
     *
     * @param be the block entity at the hit position
     * @param holder the gasket holder interface
     * @param hit the block hit result
     * @return the inflated bounds, or null if invalid
     */
    private static AABB resolveOverlayBounds(BlockEntity be, IGasketHolder holder,
            BlockHitResult hit) {
        GasketRole role = holder.resolveRole(hit);
        int slot = holder.resolveSlot(hit);
        if (!holder.supportsRole(role)) { return null; }

        AABB bounds = GasketBoundsResolver.resolveGasketBounds(be, role, slot);
        if (bounds == null) { return null; }
        return bounds.inflate(OVERLAY_EPSILON);
    }

    /**
     * Computes the camera-relative offset for a block position.
     *
     * @param pos the block position
     * @param camera the render camera
     * @return the camera-relative offset as a Vec3
     */
    private static Vec3 cameraOffset(BlockPos pos, Camera camera) {
        return new Vec3(
                pos.getX() - camera.position().x,
                pos.getY() - camera.position().y,
                pos.getZ() - camera.position().z);
    }

    /**
     * Renders the translucent filled quad and optional diagonal stripes.
     *
     * @param mc the Minecraft instance
     * @param event the render event for pose/buffer access
     * @param pos the block position
     * @param bounds the inflated gasket bounds
     * @param role the gasket role
     * @param connected whether this gasket has a partner
     */
    private static void renderFillOverlay(
            Minecraft mc, RenderLevelStageEvent.AfterOpaqueFeatures event,
            BlockPos pos, AABB bounds, GasketRole role, boolean connected) {
        Vec3 ofs = cameraOffset(pos, mc.gameRenderer.getMainCamera());
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer quad = buf.getBuffer(RenderTypes.debugQuads());

        GasketMeshEmitter.renderFilledBox(event.getPoseStack(), quad, bounds, ofs, roleColor(role));
        renderStripesIfConnected(event.getPoseStack(), quad, bounds, ofs, role, connected);
        buf.endLastBatch();
    }

    /**
     * Renders diagonal stripes over the filled box if the gasket is connected.
     *
     * @param poseStack the pose stack
     * @param quad the vertex consumer
     * @param bounds the gasket bounds
     * @param ofs the camera offset
     * @param role the gasket role
     * @param connected whether this gasket has a partner
     */
    private static void renderStripesIfConnected(PoseStack poseStack, VertexConsumer quad,
            AABB bounds, Vec3 ofs, GasketRole role, boolean connected) {
        if (!connected) { return; }
        int stripeColor = role == GasketRole.RECEIVER ? RECEIVER_STRIPE : TRANSMITTER_STRIPE;
        GasketMeshEmitter.renderDiagonalStripes(poseStack, quad, bounds, ofs, stripeColor);
    }

    /**
     * Returns the fill color for a gasket role.
     *
     * @param role the gasket role
     * @return the ARGB fill color
     */
    private static int roleColor(GasketRole role) {
        return role == GasketRole.RECEIVER ? RECEIVER_COLOR : TRANSMITTER_COLOR;
    }

    /**
     * Renders the wireframe outline around the gasket region.
     *
     * @param mc the Minecraft instance
     * @param event the render event for pose/buffer access
     * @param pos the block position
     * @param bounds the inflated gasket bounds
     * @param role the gasket role
     */
    private static void renderWireframeOverlay(
            Minecraft mc, RenderLevelStageEvent.AfterOpaqueFeatures event,
            BlockPos pos, AABB bounds, GasketRole role) {
        Vec3 ofs = cameraOffset(pos, mc.gameRenderer.getMainCamera());
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer line = buf.getBuffer(RenderTypes.lines());

        emitWireframeCuboid(event.getPoseStack(), line, bounds, ofs, role, mc);
        buf.endLastBatch();
    }

    /**
     * Emits the wireframe cuboid edges for a gasket region.
     *
     * @param poseStack the pose stack
     * @param line the line vertex consumer
     * @param bounds the gasket bounds
     * @param ofs the camera offset
     * @param role the gasket role
     * @param mc the Minecraft instance
     */
    private static void emitWireframeCuboid(PoseStack poseStack, VertexConsumer line,
            AABB bounds, Vec3 ofs, GasketRole role, Minecraft mc) {
        int wireColor = role == GasketRole.RECEIVER ? RECEIVER_WIRE : TRANSMITTER_WIRE;
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        WireframeRenderer.renderWireframeCuboid(poseStack, line,
                bounds.minX + ofs.x, bounds.minY + ofs.y, bounds.minZ + ofs.z,
                bounds.maxX + ofs.x, bounds.maxY + ofs.y, bounds.maxZ + ofs.z,
                wireColor, lineWidth);
    }

    /**
     * Renders the glowing connection line to the partner block if linked.
     *
     * @param mc the Minecraft instance
     * @param event the render event for pose/buffer access
     * @param pos the block position
     * @param bounds the inflated gasket bounds
     * @param role the gasket role
     * @param slot the slot index
     * @param holder the gasket holder
     * @param connected whether this gasket has a partner
     */
    private static void renderConnectionIfLinked(
            Minecraft mc, RenderLevelStageEvent.AfterOpaqueFeatures event,
            BlockPos pos, AABB bounds, GasketRole role, int slot,
            IGasketHolder holder, boolean connected) {
        if (!connected) { return; }
        GasketConnectionRenderer.renderConnectionLine(mc, event.getPoseStack(),
                mc.gameRenderer.getMainCamera(), pos, bounds, role, slot, holder);
    }
}
