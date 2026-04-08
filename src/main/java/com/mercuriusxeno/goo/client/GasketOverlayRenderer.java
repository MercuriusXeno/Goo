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

    /** Width of each stripe band as a fraction of the face (1/STRIPE_COUNT). */
    private static final double BAND_WIDTH = 1.0 / STRIPE_COUNT;

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

    /** Wireframe color for receiver outlines (bright blue). */
    private static final int RECEIVER_WIRE = ARGB.color(200, 80, 140, 255);

    /** Wireframe color for transmitter outlines (bright orange). */
    private static final int TRANSMITTER_WIRE = ARGB.color(200, 255, 160, 40);

    /** Midpoint offset for face center calculations. */
    private static final double FACE_MIDPOINT = 0.5;

    /** Game time to radians conversion factor for glow animation. */
    private static final float GLOW_TIME_SCALE = 0.05f;

    /** Vertical midpoint divisor for splitting slot bounds. */
    private static final double SLOT_MID_DIVISOR = 2.0;

    /** Vat receiver upper half Y start. */
    private static final double VAT_UPPER_START = 0.5;

    /** Vat transmitter lower half Y end. */
    private static final double VAT_LOWER_END = 0.5;

    /** Crucible basin top boundary in block-local coords (9/16). */
    private static final double CRUCIBLE_BASIN_Y = 9.0 / 16.0;

    /** Inset for stripe z-fighting prevention. */
    private static final double STRIPE_INSET = 0.001;

    /** Diagonal stripe shift for warning pattern. */
    private static final double STRIPE_DIAGONAL_SHIFT = 0.3;

    /** Maximum channel value for alpha clamping. */
    private static final int MAX_CHANNEL = 255;


    /** Stripe corner array index: near-side start of band. */
    private static final int CORNER_NEAR_START = 0;

    /** Stripe corner array index: near-side end of band. */
    private static final int CORNER_NEAR_END = 1;

    /** Stripe corner array index: far-side end (diagonally shifted). */
    private static final int CORNER_FAR_END = 2;

    /** Stripe corner array index: far-side start (diagonally shifted). */
    private static final int CORNER_FAR_START = 3;

    /** Vertex float offset for Y component. */
    private static final int VERT_Y = 1;

    /** Vertex float offset for Z component. */
    private static final int VERT_Z = 2;


    /** Index into box float array for max X. */
    private static final int BOX_X1 = 3;

    /** Index into box float array for max Y. */
    private static final int BOX_Y1 = 4;

    /** Index into box float array for max Z. */
    private static final int BOX_Z1 = 5;

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

        BlockHitResult hit = resolveBlockHit(mc);
        if (hit == null) { return; }

        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof IGasketHolder holder)) { return; }

        renderGasketOverlay(mc, event, hit, pos, be, holder);
    }

    /**
     * Resolves the block hit result, returning null if the targeted block
     * entity is not a gasket holder.
     *
     * @param mc the Minecraft instance
     * @return the block hit result, or null if not targeting a block
     */
    private static BlockHitResult resolveBlockHit(Minecraft mc) {
        return (BlockHitResult) mc.hitResult;
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
    private static AABB resolveOverlayBounds(BlockEntity be, IGasketHolder holder, BlockHitResult hit) {
        GasketRole role = holder.resolveRole(hit);
        int slot = holder.resolveSlot(hit);
        if (!holder.supportsRole(role)) { return null; }

        AABB bounds = resolveGasketBounds(be, role, slot);
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

        renderFilledBox(event.getPoseStack(), quad, bounds, ofs, roleColor(role));
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
        renderDiagonalStripes(poseStack, quad, bounds, ofs, stripeColor);
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
        SlotOutlineRenderer.renderWireframeCuboid(poseStack, line,
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

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        renderConnectionLine(mc, event.getPoseStack(), buf,
                mc.gameRenderer.getMainCamera(), pos, bounds, role, slot, holder);
    }

    /**
     * Renders a multi-pass glowing line from the highlighted gasket face to its
     * connected partner. Skips rendering if the partner is invalid or unloaded.
     *
     * @param mc the Minecraft instance
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera the render camera
     * @param localPos the local block position
     * @param localBounds the local gasket bounds
     * @param localRole the local gasket role
     * @param localSlot the local slot index
     * @param localHolder the local gasket holder
     */
    private static void renderConnectionLine(
            Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos localPos, AABB localBounds, GasketRole localRole,
            int localSlot, IGasketHolder localHolder) {
        Vec3 to = resolveConnectionEndpoint(mc, localRole, localSlot, localHolder);
        if (to == null) { return; }

        Vec3 from = gasketFaceCenter(localPos, localBounds, localRole);
        renderGlowLine(poseStack, bufferSource, camera, from, to, mc);
    }

    /**
     * Resolves the partner endpoint for a connection line, or null if the
     * partner is invalid (entity target, unloaded, or unresolvable).
     *
     * @param mc the Minecraft instance
     * @param localRole the local gasket role
     * @param localSlot the local slot index
     * @param localHolder the local gasket holder
     * @return the partner endpoint, or null
     */
    private static Vec3 resolveConnectionEndpoint(
            Minecraft mc, GasketRole localRole, int localSlot, IGasketHolder localHolder) {
        GasketPartner partner = localHolder.getPartner(localRole, localSlot);
        if (partner == null || partner.isEntityTarget()) { return null; }

        BlockPos partnerPos = partner.pos();
        if (mc.level == null || !mc.level.isLoaded(partnerPos)) { return null; }
        return resolvePartnerEndpoint(mc, partnerPos, partner.slot(), localRole.opposite());
    }

    /**
     * Returns the center of the gasket face on the highlighted block.
     * RECEIVER faces emit from the top of their region; TRANSMITTER from the bottom.
     *
     * @param pos the block position
     * @param bounds the axis-aligned bounding box
     * @param role the gasket role
     * @return the face center in world coordinates
     */
    private static Vec3 gasketFaceCenter(BlockPos pos, AABB bounds, GasketRole role) {
        double cx = pos.getX() + (bounds.minX + bounds.maxX) * FACE_MIDPOINT;
        double cz = pos.getZ() + (bounds.minZ + bounds.maxZ) * FACE_MIDPOINT;
        double cy = role == GasketRole.RECEIVER
                ? pos.getY() + bounds.maxY
                : pos.getY() + bounds.minY;
        return new Vec3(cx, cy, cz);
    }

    /**
     * Computes the partner's gasket face center by looking up the block entity
     * and resolving its slot/machine geometry. Returns null if unresolvable.
     *
     * @param mc the Minecraft instance
     * @param partnerPos the partner block position
     * @param partnerSlot the partner slot index
     * @param partnerRole the partner gasket role
     * @return the resolved endpoint, or null if unresolvable
     */
    private static Vec3 resolvePartnerEndpoint(
            Minecraft mc, BlockPos partnerPos, int partnerSlot, GasketRole partnerRole) {
        BlockEntity partnerBe = mc.level.getBlockEntity(partnerPos);
        if (partnerBe == null) { return null; }

        AABB partnerBounds = resolveGasketBounds(partnerBe, partnerRole, partnerSlot);
        if (partnerBounds == null) {
            return partnerFallbackCenter(partnerPos, partnerRole);
        }
        return gasketFaceCenter(partnerPos, partnerBounds, partnerRole);
    }

    /**
     * Returns a fallback center point when partner bounds can't be resolved.
     *
     * @param pos the partner block position
     * @param role the partner gasket role
     * @return the center of the block top (receiver) or bottom (transmitter)
     */
    private static Vec3 partnerFallbackCenter(BlockPos pos, GasketRole role) {
        double y = role == GasketRole.RECEIVER ? pos.getY() + 1.0 : pos.getY();
        return new Vec3(pos.getX() + FACE_MIDPOINT, y, pos.getZ() + FACE_MIDPOINT);
    }

    /**
     * Renders a multi-pass glow line between two world-space points.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera the render camera
     * @param from the start world position
     * @param to the end world position
     * @param mc the Minecraft instance
     */
    private static void renderGlowLine(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3 from, Vec3 to, Minecraft mc) {
        float pulse = computeGlowPulse(mc);
        float baseWidth = mc.getWindow().getAppropriateLineWidth();
        Vec3 camPos = camera.position();

        VertexConsumer line = bufferSource.getBuffer(RenderTypes.lines());
        emitGlowPasses(poseStack, line, from.subtract(camPos), to.subtract(camPos), pulse, baseWidth);
        bufferSource.endLastBatch();
    }

    /**
     * Computes the pulse multiplier for glow line brightness oscillation.
     *
     * @param mc the Minecraft instance (provides game time)
     * @return the pulse multiplier, centered on 1.0
     */
    private static float computeGlowPulse(Minecraft mc) {
        float gameTime = mc.level.getGameTime() * GLOW_TIME_SCALE;
        return 1.0f + GLOW_PULSE_AMP * Mth.sin(gameTime * GLOW_PULSE_FREQ);
    }

    /**
     * Emits bloom passes from outermost to core so the core draws on top.
     *
     * @param poseStack the pose stack for rendering
     * @param lineConsumer the vertex consumer for lines
     * @param a the camera-relative start position
     * @param b the camera-relative end position
     * @param pulse the brightness pulse multiplier
     * @param baseWidth the base line width in pixels
     */
    private static void emitGlowPasses(
            PoseStack poseStack, VertexConsumer lineConsumer,
            Vec3 a, Vec3 b, float pulse, float baseWidth) {
        for (int i = GLOW_PASSES - 1; i >= 0; i--) {
            int color = computeGlowPassColor(i, pulse);
            float width = baseWidth * (1.0f + i * GLOW_WIDTH_STEP);
            SlotOutlineRenderer.emitEdge(poseStack, lineConsumer,
                    a.x, a.y, a.z, b.x, b.y, b.z, color, width);
        }
    }

    /**
     * Computes the ARGB color for a single glow bloom pass, applying
     * exponential alpha decay and the pulse multiplier.
     *
     * @param passIndex the bloom pass index (0 = core, higher = outer)
     * @param pulse the brightness pulse multiplier
     * @return the ARGB color for this pass
     */
    private static int computeGlowPassColor(int passIndex, float pulse) {
        float alphaScale = (float) Math.pow(GLOW_ALPHA_DECAY, passIndex) * pulse;
        int alpha = Mth.clamp((int) (ARGB.alpha(GLOW_CORE_COLOR) * alphaScale), 0, MAX_CHANNEL);
        return ARGB.color(alpha,
                ARGB.red(GLOW_CORE_COLOR), ARGB.green(GLOW_CORE_COLOR), ARGB.blue(GLOW_CORE_COLOR));
    }

    /**
     * Resolves the AABB for a gasket region based on machine type, role, and slot.
     *
     * @param be the block entity instance
     * @param role the gasket role
     * @param slot the slot index
     * @return the gasket bounds in block-local coordinates (0-1), or null if invalid
     */
    private static AABB resolveGasketBounds(BlockEntity be, GasketRole role, int slot) {
        if (be instanceof CanisterBlockEntity cbe) { return resolveCanisterBounds(cbe, role, slot); }
        if (be instanceof HubBlockEntity hbe) { return resolveHubBounds(hbe, role, slot); }
        if (be instanceof VatBlockEntity) { return resolveVatBounds(be, role); }
        if (be instanceof CrucibleBlockEntity) { return resolveCrucibleBounds(be); }
        return null;
    }

    /**
     * Canister: split the full slot body at the vertical midpoint into upper/lower halves.
     *
     * @param cbe the canister block entity
     * @param role the gasket role
     * @param slot the slot index
     * @return the upper or lower half bounds, or null if the slot is invalid/empty
     */
    private static AABB resolveCanisterBounds(CanisterBlockEntity cbe, GasketRole role, int slot) {
        if (slot < 0 || slot >= CanisterBlock.SLOT_COUNT) { return null; }
        if (cbe.getCanister(slot).isEmpty()) { return null; }
        return splitBoundsAtMidY(CanisterBlock.slotShape(slot).bounds(), role);
    }

    /**
     * Hub: split the full slot body at the vertical midpoint into upper/lower halves.
     *
     * @param hbe the hub block entity
     * @param role the gasket role
     * @param slot the slot index
     * @return the upper or lower half bounds, or null if the slot is invalid/empty
     */
    private static AABB resolveHubBounds(HubBlockEntity hbe, GasketRole role, int slot) {
        if (slot < 0 || slot >= HubBlock.SLOT_COUNT) { return null; }
        if (hbe.getCanister(slot).isEmpty()) { return null; }
        return splitBoundsAtMidY(HubBlock.slotShape(slot).bounds(), role);
    }

    /**
     * Splits an AABB at its vertical midpoint. RECEIVER gets the upper half,
     * TRANSMITTER gets the lower half.
     *
     * @param bounds the full slot bounds
     * @param role the gasket role determining which half to return
     * @return the upper or lower half of the bounds
     */
    private static AABB splitBoundsAtMidY(AABB bounds, GasketRole role) {
        double midY = (bounds.minY + bounds.maxY) / SLOT_MID_DIVISOR;
        if (role == GasketRole.RECEIVER) {
            return new AABB(bounds.minX, midY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ);
        }
        return new AABB(bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, midY, bounds.maxZ);
    }

    /**
     * Vat: upper or lower half of the full block, depending on role.
     *
     * @param be the block entity instance
     * @param role the gasket role
     * @return the resolved bounds, or null if the role's gasket is absent
     */
    private static AABB resolveVatBounds(BlockEntity be, GasketRole role) {
        var state = be.getBlockState();
        if (role == GasketRole.RECEIVER && state.getValue(VatBlock.GASKET_CAP)) {
            return new AABB(0, VAT_UPPER_START, 0, 1, 1, 1);
        } else if (role == GasketRole.TRANSMITTER && state.getValue(VatBlock.GASKET_BASE)) {
            return new AABB(0, 0, 0, 1, VAT_LOWER_END, 1);
        }
        return null;
    }

    /**
     * Crucible: basin region (upper portion, Y 9/16 to 16/16). Always transmitter.
     *
     * @param be the block entity instance
     * @return the basin bounds, or null if no gasket is installed
     */
    private static AABB resolveCrucibleBounds(BlockEntity be) {
        if (!be.getBlockState().getValue(CrucibleBlock.HAS_GASKET)) { return null; }
        return new AABB(0, CRUCIBLE_BASIN_Y, 0, 1, 1, 1);
    }

    /**
     * Converts AABB min/max to camera-offset float array [x0,y0,z0,x1,y1,z1].
     *
     * @param bounds the bounding box
     * @param ofs camera offset
     * @return six floats: min xyz then max xyz
     */
    private static float[] boundsToFloats(AABB bounds, Vec3 ofs) {
        return new float[] {
                (float) (bounds.minX + ofs.x), (float) (bounds.minY + ofs.y),
                (float) (bounds.minZ + ofs.z), (float) (bounds.maxX + ofs.x),
                (float) (bounds.maxY + ofs.y), (float) (bounds.maxZ + ofs.z)
        };
    }

    /**
     * Renders six filled faces of an AABB as translucent quads.
     *
     * @param poseStack the pose stack for rendering
     * @param consumer the vertex consumer
     * @param bounds the axis-aligned bounding box
     * @param ofs camera offset
     * @param color the ARGB color value
     */
    private static void renderFilledBox(PoseStack poseStack, VertexConsumer consumer,
            AABB bounds, Vec3 ofs, int color) {
        float[] f = boundsToFloats(bounds, ofs);
        PoseStack.Pose pose = poseStack.last();
        emitYFaces(pose, consumer, f[0], f[VERT_Y], f[VERT_Z], f[BOX_X1], f[BOX_Y1], f[BOX_Z1], color);
        emitZFaces(pose, consumer, f[0], f[VERT_Y], f[VERT_Z], f[BOX_X1], f[BOX_Y1], f[BOX_Z1], color);
        emitXFaces(pose, consumer, f[0], f[VERT_Y], f[VERT_Z], f[BOX_X1], f[BOX_Y1], f[BOX_Z1], color);
    }

    /**
     * Emits the bottom (Y-) and top (Y+) face quads.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param x0 minimum X
     * @param y0 minimum Y
     * @param z0 minimum Z
     * @param x1 maximum X
     * @param y1 maximum Y
     * @param z1 maximum Z
     * @param color the ARGB color value
     */
    private static void emitYFaces(PoseStack.Pose pose, VertexConsumer consumer,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
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
    }

    /**
     * Emits the north (Z-) and south (Z+) face quads.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param x0 minimum X
     * @param y0 minimum Y
     * @param z0 minimum Z
     * @param x1 maximum X
     * @param y1 maximum Y
     * @param z1 maximum Z
     * @param color the ARGB color value
     */
    private static void emitZFaces(PoseStack.Pose pose, VertexConsumer consumer,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
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
    }

    /**
     * Emits the west (X-) and east (X+) face quads.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param x0 minimum X
     * @param y0 minimum Y
     * @param z0 minimum Z
     * @param x1 maximum X
     * @param y1 maximum Y
     * @param z1 maximum Z
     * @param color the ARGB color value
     */
    private static void emitXFaces(PoseStack.Pose pose, VertexConsumer consumer,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
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
     * indicate the gasket is already connected.
     *
     * @param poseStack the pose stack for rendering
     * @param consumer the vertex consumer
     * @param bounds the axis-aligned bounding box
     * @param ofs camera offset
     * @param color the ARGB color value
     */
    private static void renderDiagonalStripes(PoseStack poseStack, VertexConsumer consumer,
            AABB bounds, Vec3 ofs, int color) {
        AABB inset = computeStripeInsetBounds(bounds, ofs);
        PoseStack.Pose pose = poseStack.last();
        renderHorizontalFaceStripes(pose, consumer, color, inset);
        renderVerticalZFaceStripes(pose, consumer, color, inset);
        renderVerticalXFaceStripes(pose, consumer, color, inset);
    }

    /**
     * Computes the inset bounds for stripe rendering, applying camera offset
     * and a small inset to prevent z-fighting with the filled box.
     *
     * @param bounds the gasket bounds
     * @param ofs the camera offset
     * @return the inset bounds in camera-relative coordinates
     */
    private static AABB computeStripeInsetBounds(AABB bounds, Vec3 ofs) {
        return new AABB(
                bounds.minX + ofs.x - STRIPE_INSET,
                bounds.minY + ofs.y - STRIPE_INSET,
                bounds.minZ + ofs.z - STRIPE_INSET,
                bounds.maxX + ofs.x + STRIPE_INSET,
                bounds.maxY + ofs.y + STRIPE_INSET,
                bounds.maxZ + ofs.z + STRIPE_INSET);
    }

    /**
     * Renders diagonal stripes on the top (Y+) and bottom (Y-) horizontal faces.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param b the inset bounds
     */
    private static void renderHorizontalFaceStripes(PoseStack.Pose pose, VertexConsumer consumer,
            int color, AABB b) {
        renderHorizontalStripe(pose, consumer, color, b.minX, b.maxY, b.minZ, b.maxX, b.maxZ, true);
        renderHorizontalStripe(pose, consumer, color, b.minX, b.minY, b.minZ, b.maxX, b.maxZ, false);
    }

    /**
     * Renders diagonal stripes on the north (Z-) and south (Z+) faces.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param b the inset bounds
     */
    private static void renderVerticalZFaceStripes(PoseStack.Pose pose, VertexConsumer consumer,
            int color, AABB b) {
        renderVerticalZStripe(pose, consumer, color, b.minX, b.minY, b.minZ, b.maxX, b.maxY, false);
        renderVerticalZStripe(pose, consumer, color, b.minX, b.minY, b.maxZ, b.maxX, b.maxY, true);
    }

    /**
     * Renders diagonal stripes on the west (X-) and east (X+) faces.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param b the inset bounds
     */
    private static void renderVerticalXFaceStripes(PoseStack.Pose pose, VertexConsumer consumer,
            int color, AABB b) {
        renderVerticalXStripe(pose, consumer, color, b.minX, b.minY, b.minZ, b.maxY, b.maxZ, false);
        renderVerticalXStripe(pose, consumer, color, b.maxX, b.minY, b.minZ, b.maxY, b.maxZ, true);
    }

    /**
     * Renders diagonal stripe bands on a horizontal face at a fixed Y coordinate.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param x0 minimum X
     * @param fixedY the Y coordinate of the face
     * @param z0 minimum Z
     * @param x1 maximum X
     * @param z1 maximum Z
     * @param flip whether to reverse winding order
     */
    private static void renderHorizontalStripe(PoseStack.Pose pose, VertexConsumer consumer,
            int color, double x0, double fixedY, double z0, double x1, double z1, boolean flip) {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            float[] c = computeStripeCorners(i, x0, x1 - x0);
            emitHorizontalStripeQuad(pose, consumer, color, flip, c, (float) fixedY, (float) z0, (float) z1);
        }
    }

    /**
     * Emits a single horizontal-face stripe quad from computed corners.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param flip whether to reverse winding order
     * @param c the four stripe corner X positions
     * @param y the fixed Y coordinate
     * @param z0 the near Z
     * @param z1 the far Z
     */
    private static void emitHorizontalStripeQuad(PoseStack.Pose pose, VertexConsumer consumer,
            int color, boolean flip, float[] c, float y, float z0, float z1) {
        emitQuad(pose, consumer, color, flip,
                c[CORNER_NEAR_START], y, z0, c[CORNER_NEAR_END], y, z0,
                c[CORNER_FAR_END], y, z1, c[CORNER_FAR_START], y, z1);
    }

    /**
     * Renders diagonal stripe bands on a vertical face at a fixed Z coordinate.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param x0 minimum X
     * @param y0 minimum Y
     * @param fixedZ the Z coordinate of the face
     * @param x1 maximum X
     * @param y1 maximum Y
     * @param flip whether to reverse winding order
     */
    private static void renderVerticalZStripe(PoseStack.Pose pose, VertexConsumer consumer,
            int color, double x0, double y0, double fixedZ, double x1, double y1, boolean flip) {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            float[] c = computeStripeCorners(i, x0, x1 - x0);
            emitVerticalZStripeQuad(pose, consumer, color, flip, c, (float) y0, (float) y1, (float) fixedZ);
        }
    }

    /**
     * Emits a single Z-face stripe quad from computed corners.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param flip whether to reverse winding order
     * @param c the four stripe corner X positions
     * @param y0 the bottom Y
     * @param y1 the top Y
     * @param z the fixed Z coordinate
     */
    private static void emitVerticalZStripeQuad(PoseStack.Pose pose, VertexConsumer consumer,
            int color, boolean flip, float[] c, float y0, float y1, float z) {
        emitQuad(pose, consumer, color, flip,
                c[CORNER_NEAR_START], y0, z, c[CORNER_NEAR_END], y0, z,
                c[CORNER_FAR_END], y1, z, c[CORNER_FAR_START], y1, z);
    }

    /**
     * Renders diagonal stripe bands on X-facing vertical faces (stripes in ZY plane).
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param fixedX the fixed X coordinate
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     * @param flip whether to reverse winding order
     */
    private static void renderVerticalXStripe(PoseStack.Pose pose, VertexConsumer consumer,
            int color, double fixedX, double y0, double z0, double y1, double z1, boolean flip) {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            float[] c = computeStripeCorners(i, z0, z1 - z0);
            emitVerticalXStripeQuad(pose, consumer, color, flip, c, (float) fixedX, (float) y0, (float) y1);
        }
    }

    /**
     * Emits a single X-face stripe quad from computed corners.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param flip whether to reverse winding order
     * @param c the four stripe corner Z positions
     * @param x the fixed X coordinate
     * @param y0 the bottom Y
     * @param y1 the top Y
     */
    private static void emitVerticalXStripeQuad(PoseStack.Pose pose, VertexConsumer consumer,
            int color, boolean flip, float[] c, float x, float y0, float y1) {
        emitQuad(pose, consumer, color, flip,
                x, y0, c[CORNER_NEAR_START], x, y0, c[CORNER_NEAR_END],
                x, y1, c[CORNER_FAR_END], x, y1, c[CORNER_FAR_START]);
    }

    /**
     * Computes the four diagonal stripe corner positions along the primary axis.
     * Returns [nearStart, nearEnd, farEnd, farStart] where near is the unshifted
     * edge and far is the diagonally shifted edge.
     *
     * @param stripeIndex the stripe band index
     * @param origin the axis origin coordinate
     * @param axisLen the axis length
     * @return four corner positions along the primary axis
     */
    private static float[] computeStripeCorners(int stripeIndex, double origin, double axisLen) {
        double t0 = stripeIndex * BAND_WIDTH;
        double t1 = t0 + BAND_WIDTH * STRIPE_FILL;
        return new float[] {
                (float) (origin + axisLen * t0),
                (float) (origin + axisLen * t1),
                (float) (origin + axisLen * Math.min(t1 + STRIPE_DIAGONAL_SHIFT, 1.0)),
                (float) (origin + axisLen * Math.min(t0 + STRIPE_DIAGONAL_SHIFT, 1.0))
        };
    }

    /**
     * Emits a single quad with four vertices. When flip is true, vertices are
     * emitted in reverse order (3,2,1,0) for correct face winding.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param flip whether to reverse winding order
     * @param x0 vertex 0 X
     * @param y0 vertex 0 Y
     * @param z0 vertex 0 Z
     * @param x1 vertex 1 X
     * @param y1 vertex 1 Y
     * @param z1 vertex 1 Z
     * @param x2 vertex 2 X
     * @param y2 vertex 2 Y
     * @param z2 vertex 2 Z
     * @param x3 vertex 3 X
     * @param y3 vertex 3 Y
     * @param z3 vertex 3 Z
     */
    private static void emitQuad(PoseStack.Pose pose, VertexConsumer consumer, int color,
            boolean flip, float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        if (flip) {
            emitQuadVertices(pose, consumer, color, x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0);
        } else {
            emitQuadVertices(pose, consumer, color, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
        }
    }

    /**
     * Emits four vertices in the given order (no winding logic).
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param x0 vertex 0 X
     * @param y0 vertex 0 Y
     * @param z0 vertex 0 Z
     * @param x1 vertex 1 X
     * @param y1 vertex 1 Y
     * @param z1 vertex 1 Z
     * @param x2 vertex 2 X
     * @param y2 vertex 2 Y
     * @param z2 vertex 2 Z
     * @param x3 vertex 3 X
     * @param y3 vertex 3 Y
     * @param z3 vertex 3 Z
     */
    private static void emitQuadVertices(PoseStack.Pose pose, VertexConsumer consumer, int color,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x2, y2, z2).setColor(color);
        consumer.addVertex(pose, x3, y3, z3).setColor(color);
    }
}
