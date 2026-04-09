package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3fc;
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
    public static final double MAX_RANGE = 32.0;

    /**
     * Granny-arc threshold: when a side-face hit lands in the upper 15% of
     * the shape's height, redirect targeting to the UP face so the blob
     * arcs onto the top of the block instead of hitting the side.
     */
    private static final double GRANNY_ARC_THRESHOLD = 0.85;

    /** Half block offset for face center calculations. */
    private static final double FACE_CENTER_OFFSET = 0.5;

    /** Sentinel for no valid hand position frame. */
    private static final long NO_FRAME = -1;

    // --- Entity outline state ---

    /** The entity currently targeted by the glove, or null. Updated each tick. */
    private static @Nullable Entity targetedEntity;

    /** Opaque ARGB outline color for the targeted entity, or 0 if none. */
    private static int targetOutlineColor;

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

    /** Resets entity target and outline color when no valid aim exists. */
    private static void clearTarget() {
        targetedEntity = null;
        targetOutlineColor = 0;
    }

    /**
     * Resolves aim and updates the targeted entity and outline color.
     *
     * @param player       the local player
     * @param selectedType the currently selected goo type
     */
    private static void updateTarget(Player player, GooType selectedType) {
        TargetResult target = resolveTarget(player, 1.0f);
        targetedEntity = (target instanceof TargetResult.EntityTarget et)
                ? et.entity() : null;
        targetOutlineColor = (targetedEntity != null)
                ? ARGB.opaque(selectedType.getColor()) : 0;
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
        if (entity == targetedEntity && targetOutlineColor != 0) {
            state.outlineColor = targetOutlineColor;
        }
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
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 reach = eyePos.add(player.getViewVector(partialTick).scale(MAX_RANGE));
        TargetResult entityResult = resolveEntityTarget(player, eyePos, reach);
        if (entityResult != null) { return entityResult; }
        return resolveBlockTarget(player, eyePos, reach);
    }

    /**
     * Attempts entity targeting unless the player is sneaking.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the maximum reach endpoint
     * @return an entity target result, or null if none found or sneaking
     */
    private static @Nullable TargetResult resolveEntityTarget(
            Player player, Vec3 eyePos, Vec3 reach) {
        if (player.isShiftKeyDown()) { return null; }
        Entity entityHit = AimAssistResolver.findClosestEntity(player, eyePos, reach, targetedEntity);
        if (entityHit != null) { return TargetResult.entity(entityHit); }
        return null;
    }

    /**
     * Clips against blocks and returns a block or granny-arc target, or NONE.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the maximum reach endpoint
     * @return the resolved block target or NONE
     */
    private static TargetResult resolveBlockTarget(Player player, Vec3 eyePos, Vec3 reach) {
        BlockHitResult hit = player.level().clip(new ClipContext(
                eyePos, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return TargetResult.NONE;
        }
        return classifyBlockHit(player.level(), hit);
    }

    /**
     * Classifies a confirmed block hit as a granny-arc or normal face target.
     *
     * @param level the current level
     * @param hit   the confirmed block hit
     * @return granny-arc or block-face target result
     */
    private static TargetResult classifyBlockHit(Level level, BlockHitResult hit) {
        Direction face = hit.getDirection();
        if (face.getAxis() != Direction.Axis.Y && isUpperEdge(level, hit)) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { return; }
        if (!mc.options.getCameraType().isFirstPerson()) { return; }
        GooType selectedType = findSelectedGooType(mc.player);
        if (selectedType == null) { return; }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TargetResult target = resolveTarget(mc.player, partialTick);
        dispatchTargetRendering(event, mc, target, selectedType, partialTick);
    }

    /**
     * Dispatches arc and face rendering based on target type.
     *
     * @param event        the render event for pose stack access
     * @param mc           the Minecraft instance
     * @param target       the resolved aim target
     * @param selectedType the selected goo type
     * @param partialTick  the partial tick for animation
     */
    private static void dispatchTargetRendering(
            RenderLevelStageEvent.AfterOpaqueFeatures event, Minecraft mc,
            TargetResult target, GooType selectedType, float partialTick) {
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        if (target instanceof TargetResult.EntityTarget et) {
            renderEntityArc(ps, buf, camera, mc.player, et, selectedType, partialTick);
        } else if (target instanceof TargetResult.BlockTarget bt) {
            renderBlockArcAndFace(ps, buf, camera, mc.player, bt, selectedType, partialTick);
        }
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

    /**
     * Renders the dashed arc and face highlight for a block target.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param player       the local player
     * @param bt           the block target
     * @param gooType      the goo type for coloring
     * @param partialTick  the partial tick for animation
     */
    private static void renderBlockArcAndFace(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, TargetResult.BlockTarget bt,
            GooType gooType, float partialTick) {
        Vec3 end = Vec3.atCenterOf(bt.pos())
                .add(bt.face().getUnitVec3().scale(FACE_CENTER_OFFSET));
        ArcRenderer.renderTargetArc(poseStack, bufferSource, camera,
                player, end, gooType.getColor(), partialTick, bt.grannyArc());
        VoxelHighlightRenderer.renderBlockFace(poseStack, bufferSource, camera,
                bt.pos(), bt.face(), gooType);
    }

    // --- Hand position ---

    /**
     * Returns the world-space arc origin. Prefers the exact blob center
     * captured during item rendering (pixel-accurate). Falls back to a
     * camera-basis approximation when the blob wasn't rendered this frame.
     *
     * @param player the interacting player
     * @param camera the render camera
     * @return the gloveHandPosition
     */
    public static Vec3 getGloveHandPosition(Player player, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        long frame = mc.level != null ? mc.level.getGameTime() : NO_FRAME;
        Vec3 captured = GloveSpecialRenderer.getBlobCenterCamRel(frame);
        if (captured != null) {
            return camera.position().add(captured);
        }
        return computeCameraFallback(player, camera);
    }

    /**
     * Computes a camera-basis hand position when the blob wasn't rendered this frame.
     *
     * @param player the local player
     * @param camera the render camera
     * @return the fallback hand position in world space
     */
    private static Vec3 computeCameraFallback(Player player, Camera camera) {
        float side = ThrowArc.gloveSide(player.getMainHandItem(), player.getMainArm());
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        Vec3 offset = ThrowArc.handOffset(
                new Vec3(-left.x(), -left.y(), -left.z()),
                new Vec3(up.x(), up.y(), up.z()),
                side, player.getScale());
        return camera.position().add(offset);
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
