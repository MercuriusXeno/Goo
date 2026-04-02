package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CrucibleBlock;
import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.HubBlock;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.IGasketHolder;
import com.mercuriusxeno.goo.block.VatBlock;
import com.mercuriusxeno.goo.block.VatBlockEntity;
import com.mercuriusxeno.goo.item.ChoralTunerItem;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
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

    private GasketOverlayRenderer() {}

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

    /** Number of stripe bands across a face. */
    private static final int STRIPE_COUNT = 5;

    /** Fraction of each stripe band that is filled (rest is gap). */
    private static final double STRIPE_FILL = 0.4;

    /** Base color for the connection glow line (soft cyan-white). */
    private static final int GLOW_CORE_COLOR = ARGB.color(180, 200, 230, 255);

    /** Number of bloom passes around the core line for the glow effect. */
    private static final int GLOW_PASSES = 3;

    /** Alpha multiplier per bloom pass (each layer is fainter). */
    private static final float GLOW_ALPHA_DECAY = 0.4f;

    /** Width multiplier per bloom pass (each layer is wider). */
    private static final float GLOW_WIDTH_STEP = 1.5f;

    /** Sin wave frequency for glow pulsing (~3-second period). */
    private static final float GLOW_PULSE_FREQ = 2.1f;

    /** Sin wave amplitude for glow pulsing (±15% brightness). */
    private static final float GLOW_PULSE_AMP = 0.15f;

    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof ChoralTunerItem)) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof IGasketHolder holder)) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        double ox = pos.getX() - camera.position().x;
        double oy = pos.getY() - camera.position().y;
        double oz = pos.getZ() - camera.position().z;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Resolve the single region the player is looking at
        GasketRole role = holder.resolveRole(hit);
        int slot = holder.resolveSlot(hit);

        // Check if the machine supports this role
        if (!holder.supportsRole(role)) return;

        // Get the overlay bounds for this specific region, inflated slightly
        // to sit above block geometry and avoid z-fighting
        AABB bounds = resolveGasketBounds(be, role, slot);
        if (bounds == null) return;
        bounds = bounds.inflate(OVERLAY_EPSILON);

        // Check if this gasket is already connected
        boolean connected = holder.getPartner(role, slot) != null;
        int fillColor = role == GasketRole.RECEIVER ? RECEIVER_COLOR : TRANSMITTER_COLOR;
        int stripeColor = role == GasketRole.RECEIVER ? RECEIVER_STRIPE : TRANSMITTER_STRIPE;

        // Draw translucent filled quad
        VertexConsumer quadConsumer = bufferSource.getBuffer(RenderTypes.debugQuads());
        renderFilledBox(poseStack, quadConsumer, bounds, ox, oy, oz, fillColor);

        if (connected) {
            renderDiagonalStripes(poseStack, quadConsumer, bounds, ox, oy, oz, stripeColor);
        }

        bufferSource.endLastBatch();

        // Draw wireframe on top
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.lines());
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        int wireColor = role == GasketRole.RECEIVER
                ? ARGB.color(200, 80, 140, 255)
                : ARGB.color(200, 255, 160, 40);
        SlotOutlineRenderer.renderWireframeCuboid(poseStack, lineConsumer,
                bounds.minX + ox, bounds.minY + oy, bounds.minZ + oz,
                bounds.maxX + ox, bounds.maxY + oy, bounds.maxZ + oz,
                wireColor, lineWidth);

        bufferSource.endLastBatch();

        // Draw glowing connection line to partner if linked
        if (connected) {
            renderConnectionLine(mc, poseStack, bufferSource, camera,
                    pos, bounds, role, slot, holder);
        }
    }

    /**
     * Renders a multi-pass glowing line from the highlighted gasket face to its
     * connected partner. Each pass is wider and fainter, producing a soft bloom.
     * Skips rendering if the partner block is unloaded or in a different dimension.
     */
    private static void renderConnectionLine(
            Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos localPos, AABB localBounds, GasketRole localRole,
            int localSlot, IGasketHolder localHolder) {
        GasketPartner partner = localHolder.getPartner(localRole, localSlot);
        if (partner == null || partner.isEntityTarget()) return;

        BlockPos partnerPos = partner.pos();
        if (mc.level == null || !mc.level.isLoaded(partnerPos)) return;

        Vec3 from = gasketFaceCenter(localPos, localBounds, localRole);
        Vec3 to = resolvePartnerEndpoint(mc, partnerPos, partner.slot(), localRole.opposite());
        if (to == null) return;

        renderGlowLine(poseStack, bufferSource, camera, from, to, mc);
    }

    /**
     * Returns the center of the gasket face on the highlighted block.
     * RECEIVER faces emit from the top of their region; TRANSMITTER from the bottom.
     */
    private static Vec3 gasketFaceCenter(BlockPos pos, AABB bounds, GasketRole role) {
        double cx = pos.getX() + (bounds.minX + bounds.maxX) * 0.5;
        double cz = pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5;
        double cy = role == GasketRole.RECEIVER
                ? pos.getY() + bounds.maxY
                : pos.getY() + bounds.minY;
        return new Vec3(cx, cy, cz);
    }

    /**
     * Computes the partner's gasket face center by looking up the block entity
     * and resolving its slot/machine geometry. Returns null if the partner
     * block entity can't be resolved.
     */
    private static Vec3 resolvePartnerEndpoint(
            Minecraft mc, BlockPos partnerPos, int partnerSlot, GasketRole partnerRole) {
        BlockEntity partnerBe = mc.level.getBlockEntity(partnerPos);
        if (partnerBe == null) return null;

        AABB partnerBounds = resolveGasketBounds(partnerBe, partnerRole, partnerSlot);
        if (partnerBounds == null) {
            // Fallback: center of the block at top or bottom
            double y = partnerRole == GasketRole.RECEIVER ? partnerPos.getY() + 1.0 : partnerPos.getY();
            return new Vec3(partnerPos.getX() + 0.5, y, partnerPos.getZ() + 0.5);
        }
        return gasketFaceCenter(partnerPos, partnerBounds, partnerRole);
    }

    /**
     * Renders a multi-pass glow line between two world-space points.
     * Core pass is bright and thin; bloom passes are progressively wider and fainter.
     * Alpha pulses gently over time for a living feel.
     */
    private static void renderGlowLine(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3 from, Vec3 to, Minecraft mc) {
        float gameTime = mc.level.getGameTime() * 0.05f;
        float pulse = 1.0f + GLOW_PULSE_AMP * Mth.sin(gameTime * GLOW_PULSE_FREQ);
        float baseWidth = mc.getWindow().getAppropriateLineWidth();

        double ax = from.x - camera.position().x;
        double ay = from.y - camera.position().y;
        double az = from.z - camera.position().z;
        double bx = to.x - camera.position().x;
        double by = to.y - camera.position().y;
        double bz = to.z - camera.position().z;

        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.lines());

        // Bloom passes (outermost first so core draws on top)
        for (int i = GLOW_PASSES - 1; i >= 0; i--) {
            float alphaScale = (float) Math.pow(GLOW_ALPHA_DECAY, i) * pulse;
            int alpha = Mth.clamp((int) (ARGB.alpha(GLOW_CORE_COLOR) * alphaScale), 0, 255);
            int color = ARGB.color(alpha,
                    ARGB.red(GLOW_CORE_COLOR), ARGB.green(GLOW_CORE_COLOR), ARGB.blue(GLOW_CORE_COLOR));
            float width = baseWidth * (1.0f + i * GLOW_WIDTH_STEP);
            SlotOutlineRenderer.emitEdge(poseStack, lineConsumer,
                    ax, ay, az, bx, by, bz, color, width);
        }

        bufferSource.endLastBatch();
    }

    /**
     * Resolves the AABB for a gasket region based on machine type, role, and slot.
     * Used for both the local overlay and partner endpoint computation.
     *
     * @return the gasket bounds in block-local coordinates (0-1), or null if invalid
     */
    private static AABB resolveGasketBounds(BlockEntity be, GasketRole role, int slot) {
        if (be instanceof CanisterBlockEntity cbe) {
            return resolveCanisterBounds(cbe, role, slot);
        } else if (be instanceof HubBlockEntity hbe) {
            return resolveHubBounds(hbe, role, slot);
        } else if (be instanceof VatBlockEntity) {
            return resolveVatBounds(be, role);
        } else if (be instanceof CrucibleBlockEntity) {
            return resolveCrucibleBounds(be);
        }
        return null;
    }

    /**
     * Canister: split the full slot body at the vertical midpoint into upper/lower halves.
     * Uses slotShape (the full 4x12x4 body), NOT the 1px gasket caps.
     */
    private static AABB resolveCanisterBounds(CanisterBlockEntity cbe, GasketRole role, int slot) {
        if (slot < 0 || slot >= CanisterBlock.SLOT_COUNT) return null;
        if (cbe.getCanister(slot).isEmpty()) return null;

        AABB slotBounds = CanisterBlock.slotShape(slot).bounds();
        double midY = (slotBounds.minY + slotBounds.maxY) / 2.0;
        if (role == GasketRole.RECEIVER) {
            return new AABB(slotBounds.minX, midY, slotBounds.minZ,
                    slotBounds.maxX, slotBounds.maxY, slotBounds.maxZ);
        } else {
            return new AABB(slotBounds.minX, slotBounds.minY, slotBounds.minZ,
                    slotBounds.maxX, midY, slotBounds.maxZ);
        }
    }

    /**
     * Hub: split the full slot body at the vertical midpoint into upper/lower halves.
     */
    private static AABB resolveHubBounds(HubBlockEntity hbe, GasketRole role, int slot) {
        if (slot < 0 || slot >= HubBlock.SLOT_COUNT) return null;
        if (hbe.getCanister(slot).isEmpty()) return null;

        AABB slotBounds = HubBlock.slotShape(slot).bounds();
        double midY = (slotBounds.minY + slotBounds.maxY) / 2.0;
        if (role == GasketRole.RECEIVER) {
            return new AABB(slotBounds.minX, midY, slotBounds.minZ,
                    slotBounds.maxX, slotBounds.maxY, slotBounds.maxZ);
        } else {
            return new AABB(slotBounds.minX, slotBounds.minY, slotBounds.minZ,
                    slotBounds.maxX, midY, slotBounds.maxZ);
        }
    }

    /** Vat: upper or lower half of the full block, depending on role. */
    private static AABB resolveVatBounds(BlockEntity be, GasketRole role) {
        var state = be.getBlockState();
        if (role == GasketRole.RECEIVER && state.getValue(VatBlock.GASKET_CAP)) {
            return new AABB(0, 0.5, 0, 1, 1, 1);
        } else if (role == GasketRole.TRANSMITTER && state.getValue(VatBlock.GASKET_BASE)) {
            return new AABB(0, 0, 0, 1, 0.5, 1);
        }
        return null;
    }

    /** Crucible: basin region (upper portion, Y 9/16 to 16/16). Always transmitter. */
    private static AABB resolveCrucibleBounds(BlockEntity be) {
        if (!be.getBlockState().getValue(CrucibleBlock.HAS_GASKET)) return null;
        return new AABB(0, 9.0 / 16.0, 0, 1, 1, 1);
    }

    /**
     * Renders six filled faces of an AABB as translucent quads.
     * Coordinates are block-local; ox/oy/oz apply camera offset.
     */
    private static void renderFilledBox(PoseStack poseStack, VertexConsumer consumer,
            AABB bounds, double ox, double oy, double oz, int color) {
        float x0 = (float) (bounds.minX + ox);
        float y0 = (float) (bounds.minY + oy);
        float z0 = (float) (bounds.minZ + oz);
        float x1 = (float) (bounds.maxX + ox);
        float y1 = (float) (bounds.maxY + oy);
        float z1 = (float) (bounds.maxZ + oz);
        PoseStack.Pose pose = poseStack.last();

        // Bottom face (Y-)
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y0, z1).setColor(color);
        consumer.addVertex(pose, x0, y0, z1).setColor(color);

        // Top face (Y+)
        consumer.addVertex(pose, x0, y1, z1).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x1, y1, z0).setColor(color);
        consumer.addVertex(pose, x0, y1, z0).setColor(color);

        // North face (Z-)
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
        consumer.addVertex(pose, x0, y1, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z0).setColor(color);
        consumer.addVertex(pose, x1, y0, z0).setColor(color);

        // South face (Z+)
        consumer.addVertex(pose, x1, y0, z1).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x0, y1, z1).setColor(color);
        consumer.addVertex(pose, x0, y0, z1).setColor(color);

        // West face (X-)
        consumer.addVertex(pose, x0, y0, z1).setColor(color);
        consumer.addVertex(pose, x0, y1, z1).setColor(color);
        consumer.addVertex(pose, x0, y1, z0).setColor(color);
        consumer.addVertex(pose, x0, y0, z0).setColor(color);

        // East face (X+)
        consumer.addVertex(pose, x1, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x1, y0, z1).setColor(color);
    }

    /**
     * Renders diagonal warning stripes across all six faces of the AABB to
     * indicate the gasket is already connected. Stripes run diagonally across
     * each face as thin bands.
     */
    private static void renderDiagonalStripes(PoseStack poseStack, VertexConsumer consumer,
            AABB bounds, double ox, double oy, double oz, int color) {
        // Inset slightly to avoid z-fighting with the filled box
        double inset = 0.001;
        double x0 = bounds.minX + ox - inset;
        double y0 = bounds.minY + oy - inset;
        double z0 = bounds.minZ + oz - inset;
        double x1 = bounds.maxX + ox + inset;
        double y1 = bounds.maxY + oy + inset;
        double z1 = bounds.maxZ + oz + inset;

        PoseStack.Pose pose = poseStack.last();

        // Draw stripes on Y+ and Y- faces (horizontal faces, stripes along XZ diagonal)
        renderFaceStripes(pose, consumer, color,
                x0, y1, z0, x1, y1, z1, true, true);   // Top
        renderFaceStripes(pose, consumer, color,
                x0, y0, z0, x1, y0, z1, true, false);   // Bottom

        // Draw stripes on Z- and Z+ faces (stripes along XY diagonal)
        renderFaceStripes(pose, consumer, color,
                x0, y0, z0, x1, y1, z0, false, false);  // North (Z-)
        renderFaceStripes(pose, consumer, color,
                x0, y0, z1, x1, y1, z1, false, true);   // South (Z+)

        // Draw stripes on X- and X+ faces (stripes along ZY diagonal)
        renderVerticalFaceStripes(pose, consumer, color,
                x0, y0, z0, y1, z1, false);  // West (X-)
        renderVerticalFaceStripes(pose, consumer, color,
                x1, y0, z0, y1, z1, true);   // East (X+)
    }

    /**
     * Renders diagonal stripe bands on a horizontal or vertical face.
     * For horizontal faces (isHorizontal=true): face spans (x0,fixedY,z0)-(x1,fixedY,z1).
     * For vertical faces along Z axis (isHorizontal=false): face spans (x0,y0,fixedZ)-(x1,y1,fixedZ).
     */
    private static void renderFaceStripes(PoseStack.Pose pose, VertexConsumer consumer,
            int color, double ax0, double ay0, double az0, double ax1, double ay1, double az1,
            boolean isHorizontal, boolean flipWinding) {
        double bandWidth = 1.0 / STRIPE_COUNT;
        double stripeWidth = bandWidth * STRIPE_FILL;

        for (int i = 0; i < STRIPE_COUNT; i++) {
            double t0 = i * bandWidth;
            double t1 = t0 + stripeWidth;

            if (isHorizontal) {
                // Horizontal face at fixed Y. Stripes run diagonally in XZ.
                double fixedY = (float) ay0;
                double xLen = ax1 - ax0;

                // Stripe band from t0 to t1 along the diagonal
                double sx0 = ax0 + xLen * t0;
                double sz0 = az0;
                double sx1 = ax0 + xLen * t1;
                double sz1 = az0;
                double sx2 = ax0 + xLen * Math.min(t1 + 0.3, 1.0);
                double sz2 = az1;
                double sx3 = ax0 + xLen * Math.min(t0 + 0.3, 1.0);
                double sz3 = az1;

                if (flipWinding) {
                    consumer.addVertex(pose, (float) sx3, (float) fixedY, (float) sz3).setColor(color);
                    consumer.addVertex(pose, (float) sx2, (float) fixedY, (float) sz2).setColor(color);
                    consumer.addVertex(pose, (float) sx1, (float) fixedY, (float) sz1).setColor(color);
                    consumer.addVertex(pose, (float) sx0, (float) fixedY, (float) sz0).setColor(color);
                } else {
                    consumer.addVertex(pose, (float) sx0, (float) fixedY, (float) sz0).setColor(color);
                    consumer.addVertex(pose, (float) sx1, (float) fixedY, (float) sz1).setColor(color);
                    consumer.addVertex(pose, (float) sx2, (float) fixedY, (float) sz2).setColor(color);
                    consumer.addVertex(pose, (float) sx3, (float) fixedY, (float) sz3).setColor(color);
                }
            } else {
                // Vertical face at fixed Z. Stripes run diagonally in XY.
                double fixedZ = (float) az0;
                double xLen = ax1 - ax0;

                double sx0 = ax0 + xLen * t0;
                double sy0 = ay0;
                double sx1 = ax0 + xLen * t1;
                double sy1 = ay0;
                double sx2 = ax0 + xLen * Math.min(t1 + 0.3, 1.0);
                double sy2 = ay1;
                double sx3 = ax0 + xLen * Math.min(t0 + 0.3, 1.0);
                double sy3 = ay1;

                if (flipWinding) {
                    consumer.addVertex(pose, (float) sx3, (float) sy3, (float) fixedZ).setColor(color);
                    consumer.addVertex(pose, (float) sx2, (float) sy2, (float) fixedZ).setColor(color);
                    consumer.addVertex(pose, (float) sx1, (float) sy1, (float) fixedZ).setColor(color);
                    consumer.addVertex(pose, (float) sx0, (float) sy0, (float) fixedZ).setColor(color);
                } else {
                    consumer.addVertex(pose, (float) sx0, (float) sy0, (float) fixedZ).setColor(color);
                    consumer.addVertex(pose, (float) sx1, (float) sy1, (float) fixedZ).setColor(color);
                    consumer.addVertex(pose, (float) sx2, (float) sy2, (float) fixedZ).setColor(color);
                    consumer.addVertex(pose, (float) sx3, (float) sy3, (float) fixedZ).setColor(color);
                }
            }
        }
    }

    /**
     * Renders diagonal stripe bands on X-facing vertical faces (stripes in ZY plane).
     */
    private static void renderVerticalFaceStripes(PoseStack.Pose pose, VertexConsumer consumer,
            int color, double fixedX, double y0, double z0, double y1, double z1,
            boolean flipWinding) {
        double bandWidth = 1.0 / STRIPE_COUNT;
        double stripeWidth = bandWidth * STRIPE_FILL;
        double zLen = z1 - z0;

        for (int i = 0; i < STRIPE_COUNT; i++) {
            double t0 = i * bandWidth;
            double t1 = t0 + stripeWidth;

            double sz0 = z0 + zLen * t0;
            double sy0 = y0;
            double sz1 = z0 + zLen * t1;
            double sy1 = y0;
            double sz2 = z0 + zLen * Math.min(t1 + 0.3, 1.0);
            double sy2 = y1;
            double sz3 = z0 + zLen * Math.min(t0 + 0.3, 1.0);
            double sy3 = y1;

            if (flipWinding) {
                consumer.addVertex(pose, (float) fixedX, (float) sy3, (float) sz3).setColor(color);
                consumer.addVertex(pose, (float) fixedX, (float) sy2, (float) sz2).setColor(color);
                consumer.addVertex(pose, (float) fixedX, (float) sy1, (float) sz1).setColor(color);
                consumer.addVertex(pose, (float) fixedX, (float) sy0, (float) sz0).setColor(color);
            } else {
                consumer.addVertex(pose, (float) fixedX, (float) sy0, (float) sz0).setColor(color);
                consumer.addVertex(pose, (float) fixedX, (float) sy1, (float) sz1).setColor(color);
                consumer.addVertex(pose, (float) fixedX, (float) sy2, (float) sz2).setColor(color);
                consumer.addVertex(pose, (float) fixedX, (float) sy3, (float) sz3).setColor(color);
            }
        }
    }
}
