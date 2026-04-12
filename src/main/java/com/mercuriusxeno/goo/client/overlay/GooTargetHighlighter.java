package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-side aim highlighting for goo blob throwing. When the player holds
 * a goo glove with a selected type, this renders:
 * - Vanilla spectral/glowing outline on the targeted mob (goo-colored)
 * - A goo-colored translucent face highlight on the targeted block face
 * - Nothing when aiming at air or beyond range
 *
 * The entity outline uses NeoForge's render state modifier system to inject
 * the outline color into the entity render pipeline, producing the same
 * visual effect as spectral arrows or the Glowing potion.
 *
 * Also exposes {@link #resolveTarget} for the throw system to reuse.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTargetHighlighter {
    /** Maximum range for blob throwing in blocks. */
    public static final double MAX_RANGE = 64.0;

    /**
     * Granny-arc threshold: when a side-face hit lands in the upper 15% of
     * the shape's height, redirect targeting to the UP face so the blob
     * arcs onto the top of the block instead of hitting the side.
     */
    private static final double GRANNY_ARC_THRESHOLD = 0.85;

    /** Half block offset for face center calculations. */
    private static final double FACE_CENTER_OFFSET = 0.5;

    // --- Aim hit state ---

    /**
     * The aim hit currently resolved by the glove, or null. Updated each
     * tick. Used as the sticky-retention seed for the next frame and as
     * the source of truth for both the entity outline modifier and the
     * ChainMarker BER highlighting accessor.
     */
    private static AimAssistResolver.@Nullable AimHit lastAimHit;

    /** Opaque ARGB outline color for the targeted entity, or 0 if none. */
    private static int targetOutlineColor;

    // --- Frame-scoped arc deferral ---

    /** Target cached by the opaque-stage handler for the translucent
     * arc-render stage to consume. Null when no valid target was
     * resolved this frame or the arc has already been consumed. */
    private static @Nullable TargetResult cachedArcTarget;
    /** Goo type for the cached arc target. */
    private static @Nullable GooType cachedArcType;
    /** Partial tick captured at the opaque-stage handler. */
    private static float cachedArcPartialTick;

    private GooTargetHighlighter() {}

    /**
     * Client tick: resolves aim target and stores entity + goo color for the
     * render state modifier to pick up during entity rendering.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clearTarget();
            return;
        }
        tickGloveTarget(mc);
    }

    /**
     * Resolves goo type selection and updates aim target for the local player.
     *
     * @param mc the Minecraft client instance
     */
    private static void tickGloveTarget(Minecraft mc) {
        GooType selectedType = findSelectedGooType(mc.player);
        if (selectedType == null) {
            clearTarget();
            return;
        }
        updateTarget(mc.player, selectedType);
    }

    /** Resets aim hit and outline color when no valid aim exists. */
    private static void clearTarget() {
        lastAimHit = null;
        targetOutlineColor = 0;
    }

    /**
     * Resolves aim and updates the tracked aim hit and outline color.
     *
     * @param player       the local player
     * @param selectedType the currently selected goo type
     */
    private static void updateTarget(Player player, GooType selectedType) {
        TargetResult target = resolveTarget(player, 1.0f);
        boolean hasEntity = target instanceof TargetResult.EntityTarget;
        targetOutlineColor = hasEntity ? ARGB.opaque(selectedType.getColor()) : 0;
    }

    /**
     * Render state modifier callback: sets outlineColor on entities targeted
     * by the glove so vanilla renders the spectral glow outline in the goo color.
     * Registered via RegisterRenderStateModifiersEvent in GooClientSetup.
     *
     * @param entity the target entity
     * @param state the block state
     */
    public static void modifyEntityRenderState(Entity entity, EntityRenderState state) {
        if (targetOutlineColor == 0) { return; }
        if (lastAimHit instanceof AimAssistResolver.AimHit.EntityHit eh
                && eh.entity() == entity) {
            state.outlineColor = targetOutlineColor;
        }
    }

    /**
     * Returns true if the chain-marker block at the given position is the
     * current cone-assisted aim target. Consumed by {@code ChainMarkerBER}
     * to persist the "targeted" visual across the freeze window and through
     * the eager cone scan.
     *
     * @param pos the chain marker block position
     * @return true if the aim assist is currently locked onto this marker
     */
    public static boolean isChainMarkerTargeted(BlockPos pos) {
        return lastAimHit instanceof AimAssistResolver.AimHit.ChainMarkerHit cmh
                && cmh.pos().equals(pos);
    }

    // --- Target resolution (public API for throw system) ---

    /**
     * Resolves what the player is aiming at within throw range.
     * Entity hits take priority over block hits unless sneaking,
     * which forces block-only targeting with no aim assist.
     *
     * @param player the local player
     * @param partialTick interpolation factor for smooth rendering
     * @return entity target, block face target, or NONE
     */
    public static TargetResult resolveTarget(Player player, float partialTick) {
        // Sneak always cancels the post-throw freeze, matching the existing
        // "shift bypasses aim assist" rule at line 169 below.
        if (player.isShiftKeyDown()) {
            ThrowFreezeState.clear();
        } else {
            TargetResult frozen = ThrowFreezeState.getFrozenTarget();
            if (frozen != null) { return frozen; }
        }
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 reach = eyePos.add(player.getViewVector(partialTick).scale(MAX_RANGE));
        TargetResult entityResult = resolveEntityTarget(player, eyePos, reach);
        if (entityResult != null) { return entityResult; }
        return resolveBlockTarget(player, eyePos, reach);
    }

    /**
     * Attempts aim-assisted targeting (entities and chain markers) unless
     * the player is sneaking. Updates the sticky seed {@code lastAimHit}
     * as a side effect so the next frame can honor retention.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the maximum reach endpoint
     * @return an entity or chain marker target result, or null if none / sneaking
     */
    private static @Nullable TargetResult resolveEntityTarget(
            Player player, Vec3 eyePos, Vec3 reach) {
        if (player.isShiftKeyDown()) {
            lastAimHit = null;
            return null;
        }
        AimAssistResolver.AimHit hit = AimAssistResolver.findClosestAimHit(
                player, eyePos, reach, lastAimHit);
        lastAimHit = hit;
        if (hit instanceof AimAssistResolver.AimHit.EntityHit eh) {
            return TargetResult.entity(eh.entity());
        }
        if (hit instanceof AimAssistResolver.AimHit.ChainMarkerHit cmh) {
            return TargetResult.chainMarker(cmh.pos());
        }
        return null;
    }

    /**
     * Clips against blocks and returns a block or granny-arc target.
     * On a miss, projects to max range along the look vector so the
     * arc always renders toward the aimed direction.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the maximum reach endpoint
     * @return the resolved block target, or max-range projection on miss
     */
    private static TargetResult resolveBlockTarget(Player player, Vec3 eyePos, Vec3 reach) {
        BlockHitResult hit = player.level().clip(new ClipContext(
                eyePos, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return projectToGround(player.level(), reach);
        }
        return classifyBlockHit(player.level(), hit);
    }

    /** Raycasts down from the max-range endpoint to find the ground.
     * If the endpoint is above max build height (looking upward), starts
     * the downward cast from build height at the same XZ.
     *
     * @param level the current level
     * @param reach the max-range endpoint along the look vector
     * @return a block target on the ground, or NONE if no ground found
     */
    private static TargetResult projectToGround(Level level, Vec3 reach) {
        double topY = Math.min(reach.y, level.getMaxY());
        Vec3 top = new Vec3(reach.x, topY, reach.z);
        Vec3 bottom = new Vec3(reach.x, level.getMinY(), reach.z);
        BlockHitResult ground = level.clip(new ClipContext(
                top, bottom, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, CollisionContext.empty()));
        if (ground.getType() != HitResult.Type.BLOCK) {
            return TargetResult.NONE;
        }
        return TargetResult.block(ground.getBlockPos(), Direction.UP);
    }

    /**
     * Classifies a confirmed block hit as a granny-arc or normal face target.
     * A granny arc is only offered when the block directly above the hit is
     * air - otherwise the arc would collide with that block and the shot
     * makes no sense, so it falls back to a normal side-face target.
     *
     * @param level the current level
     * @param hit   the confirmed block hit
     * @return granny-arc or block-face target result
     */
    private static TargetResult classifyBlockHit(Level level, BlockHitResult hit) {
        Direction face = hit.getDirection();
        if (face.getAxis() != Direction.Axis.Y
                && isUpperEdge(level, hit)
                && level.getBlockState(hit.getBlockPos().above()).isAir()) {
            return TargetResult.grannyArc(hit.getBlockPos());
        }
        return TargetResult.block(hit.getBlockPos(), face);
    }

    /**
     * Returns true if the hit landed in the upper portion of the block's
     * voxel shape, measured against the shape's actual Y extent so slabs,
     * stairs, etc. use their real geometry, not a full cube.
     *
     * @param level the current level
     * @param hit the block hit result
     * @return true if upperEdge
     */
    private static boolean isUpperEdge(Level level, BlockHitResult hit) {
        var pos = hit.getBlockPos();
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) { return false; }
        AABB bounds = shape.bounds();
        double range = bounds.maxY - bounds.minY;
        if (range <= 0) { return false; }
        double hitY = hit.getLocation().y - pos.getY();
        double relative = (hitY - bounds.minY) / range;
        return relative >= GRANNY_ARC_THRESHOLD;
    }

    // --- Event handlers ---

    /**
     * Renders goo-colored target visuals: dashed arc to entity targets,
     * translucent face highlight for block targets. Entity spectral outlines
     * are handled separately by the render state modifier.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        clearCachedArc();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { return; }
        if (!mc.options.getCameraType().isFirstPerson()) { return; }
        GooType selectedType = findSelectedGooType(mc.player);
        if (selectedType == null) { return; }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TargetResult target = resolveTarget(mc.player, partialTick);
        cacheArc(target, selectedType, partialTick);
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (target instanceof TargetResult.BlockTarget bt) {
            VoxelHighlightRenderer.renderBlockFace(ps, buf, camera,
                    bt.pos(), bt.face(), selectedType);
        } else if (target instanceof TargetResult.ChainMarkerTarget cmt) {
            VoxelHighlightRenderer.renderBlockShape(ps, buf, camera,
                    cmt.pos(), selectedType);
        }
    }

    /** Renders the deferred throw-arc line after translucent blocks so
     * the depth buffer contains both opaque and water depth for
     * correct sorting.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        TargetResult target = cachedArcTarget;
        GooType type = cachedArcType;
        float partialTick = cachedArcPartialTick;
        clearCachedArc();
        if (target == null || type == null) { return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { return; }
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        dispatchArcForTarget(ps, buf, camera, mc.player, target, type, partialTick);
    }

    private static void cacheArc(TargetResult target, GooType type, float partialTick) {
        cachedArcTarget = target;
        cachedArcType = type;
        cachedArcPartialTick = partialTick;
    }

    private static void clearCachedArc() {
        cachedArcTarget = null;
        cachedArcType = null;
        cachedArcPartialTick = 0f;
    }

    /** Dispatches arc rendering only (face already drawn at opaque stage).
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the active camera
     * @param player       the local player
     * @param target       the cached target
     * @param selectedType the cached goo type
     * @param partialTick  the cached partial tick
     */
    private static void dispatchArcForTarget(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, TargetResult target,
            GooType selectedType, float partialTick) {
        if (target instanceof TargetResult.EntityTarget et) {
            renderEntityArc(poseStack, bufferSource, camera, player, et, selectedType, partialTick);
        } else if (target instanceof TargetResult.ChainMarkerTarget cmt) {
            renderChainMarkerArc(poseStack, bufferSource, camera, player, cmt, selectedType, partialTick);
        } else if (target instanceof TargetResult.BlockTarget bt) {
            renderBlockArc(poseStack, bufferSource, camera, player, bt, selectedType, partialTick);
        }
    }

    /**
     * Renders the dashed arc toward a chain marker target. Chain markers
     * behave like entities for targeting so they get an entity-style arc
     * (no face voxel overlay). The ChainMarker BER handles the highlight
     * visual on the block itself.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param player       the local player
     * @param cmt          the chain marker target
     * @param gooType      the goo type for coloring
     * @param partialTick  the partial tick for animation
     */
    private static void renderChainMarkerArc(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, TargetResult.ChainMarkerTarget cmt,
            GooType gooType, float partialTick) {
        Vec3 end = Vec3.atCenterOf(cmt.pos());
        ArcRenderer.renderTargetArc(poseStack, bufferSource, camera,
                player, end, gooType.getColor(), partialTick, false);
    }

    /**
     * Renders the dashed arc toward an entity target.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param player       the local player
     * @param et           the entity target
     * @param gooType      the goo type for coloring
     * @param partialTick  the partial tick for animation
     */
    private static void renderEntityArc(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, TargetResult.EntityTarget et,
            GooType gooType, float partialTick) {
        Vec3 end = et.entity().getBoundingBox().getCenter();
        ArcRenderer.renderTargetArc(poseStack, bufferSource, camera,
                player, end, gooType.getColor(), partialTick, false);
    }

    /** Renders only the throw-arc line for a block target. The face
     * highlight is drawn separately in the opaque stage.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param player       the local player
     * @param bt           the block target
     * @param gooType      the goo type for coloring
     * @param partialTick  the partial tick for animation
     */
    private static void renderBlockArc(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, TargetResult.BlockTarget bt,
            GooType gooType, float partialTick) {
        Vec3 end = Vec3.atCenterOf(bt.pos())
                .add(bt.face().getUnitVec3().scale(FACE_CENTER_OFFSET));
        ArcRenderer.renderTargetArc(poseStack, bufferSource, camera,
                player, end, gooType.getColor(), partialTick, bt.grannyArc());
    }

    // --- Hand position ---

    /**
     * Returns the world-space arc origin from the blob center captured
     * during item rendering. Uses the last capture unconditionally -
     * no age check, no fallback formula. The capture updates every
     * frame the glove renders. If no capture exists yet (first frame
     * of world load, before the item renderer has ever fired), returns
     * the camera position as a degenerate origin until the first
     * capture arrives next frame.
     *
     * @param player the interacting player
     * @param camera the render camera
     * @return the world-space hand position
     */
    public static Vec3 getGloveHandPosition(Player player, Camera camera) {
        Vec3 captured = GloveSpecialRenderer.getLastBlobCenterCamRel();
        if (captured == null) { return camera.position(); }
        return camera.position().add(captured);
    }

    // --- Glove detection ---

    /**
     * Checks both hands for a goo glove with a selected type. Main hand priority.
     * Returns null if the player has no goo of that type (suppresses visuals).
     *
     * @param player the interacting player
     * @return the matching result, or null if not found
     */
    private static @Nullable GooType findSelectedGooType(Player player) {
        GooType type = tryGloveInHand(player.getMainHandItem());
        if (type != null) { return GloveUseTracker.isSelectedTypeAvailable() ? type : null; }
        type = tryGloveInHand(player.getOffhandItem());
        if (type != null) { return GloveUseTracker.isSelectedTypeAvailable() ? type : null; }
        return null;
    }

    /**
     * Returns the selected type if the stack is a glove with a selection.
     *
     * @param stack the item stack
     * @return the result
     */
    private static @Nullable GooType tryGloveInHand(ItemStack stack) {
        if (stack.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(stack);
        }
        return null;
    }
}
