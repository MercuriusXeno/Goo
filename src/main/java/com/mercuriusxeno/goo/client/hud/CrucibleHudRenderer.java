package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.CrucibleBlock;
import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Renders a compact in-world HUD panel when the player's crosshair targets
 * a crucible's basin. The panel sits on the basin rim at the point farthest
 * from the player and billboards to face the camera. Each goo type shows as
 * an icon with "reservoir / total" volumes.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrucibleHudRenderer {
    /** Exponential smoothing time constant in seconds. Lower = snappier. */
    private static final float SMOOTH_TAU = 0.1f;

    /** Pitch threshold (radians) below which the retracting panel is considered flush. */
    private static final float RETRACT_THRESHOLD = 0.01f;

    /** Y threshold in block-local coords: below this is the fuel rod area, not the basin. */
    private static final double BASIN_MIN_Y = 10.0 / 16.0;

    /** Z-nudge for panel to prevent z-fighting on the basin rim. */
    private static final double RIM_Z_NUDGE = -0.01;

    // --- Animation state (static, persists across frames) ---

    /** Block position currently showing the HUD, or null if idle. */
    private static @Nullable BlockPos trackedPos;

    /** Smoothed pitch angle in radians (0 = flush with rim, positive = tilted toward camera). */
    private static float currentPitch;

    /** True when crosshair has left and the panel is animating back to flush. */
    private static boolean retracting;

    /** System.nanoTime() of the last frame, for delta-time calculation. */
    private static final long[] LAST_FRAME_NANOS = {0};

    private CrucibleHudRenderer() {}

    /**
     * Renders the crucible HUD after entities are drawn.
     * Drives the state machine and dispatches rendering when active.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float dt = InWorldHud.computeDeltaTime(LAST_FRAME_NANOS);
        updateState(getTargetPos(), dt);
        if (trackedPos == null) { return; }
        dispatchRender(event.getPoseStack(), camera);
    }

    /**
     * Validates the tracked crucible still exists, then renders the rim panel.
     * Clears state if the block entity is gone.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     */
    private static void dispatchRender(PoseStack poseStack, Camera camera) {
        CrucibleBlockEntity be = lookupCrucible(trackedPos);
        if (be == null) {
            clearState();
            return;
        }
        renderRimPanel(poseStack, be, camera);
    }

    /**
     * State machine: manages transitions between idle, emerging, and retracting.
     * Updates currentPitch each frame via exponential smoothing.
     *
     * @param target the current aim target
     * @param dt the delta time in seconds
     */
    private static void updateState(@Nullable BlockPos target, float dt) {
        applyTransition(target);
        advancePitch(dt);
    }

    /**
     * Applies the idle/emerge/retract state transition for a single frame.
     * New target starts emerge; same target cancels retract; lost target begins retract.
     *
     * @param target the current aim target, or null if not aiming at a crucible
     */
    private static void applyTransition(@Nullable BlockPos target) {
        if (isNewTarget(target)) {
            beginEmerge(target);
        } else if (target != null && retracting) {
            retracting = false;
        } else if (shouldBeginRetract(target)) {
            retracting = true;
        }
    }

    /**
     * Returns true when the crosshair has left and retract should begin.
     *
     * @param target the current aim target, or null if not aiming at a crucible
     * @return true if retract animation should start
     */
    private static boolean shouldBeginRetract(@Nullable BlockPos target) {
        return target == null && trackedPos != null && !retracting;
    }

    /**
     * Returns true when the target is a new (different) crucible position.
     *
     * @param target the current aim target, or null
     * @return true if a new target should trigger an emerge
     */
    private static boolean isNewTarget(@Nullable BlockPos target) {
        return target != null && !target.equals(trackedPos);
    }

    /**
     * Advances the pitch toward the target value and completes retract if flush.
     *
     * @param dt the delta time in seconds
     */
    private static void advancePitch(float dt) {
        if (trackedPos == null) { return; }

        float targetPitch = retracting ? 0f : 1f;
        currentPitch = InWorldHud.smoothToward(currentPitch, targetPitch, dt, SMOOTH_TAU);

        if (retracting && currentPitch < RETRACT_THRESHOLD) {
            clearState();
        }
    }

    /**
     * Initializes state for a new emerge animation on the given block.
     *
     * @param pos the block position
     */
    private static void beginEmerge(BlockPos pos) {
        trackedPos = pos;
        currentPitch = 0f;
        retracting = false;
    }

    /** Resets all state to idle. */
    private static void clearState() {
        trackedPos = null;
        currentPitch = 0f;
        retracting = false;
    }

    /**
     * Returns the block position of the targeted crucible basin, or null.
     * Only returns a target when the crosshair hits the basin portion (Y >= 10/16),
     * not the fuel rod area below.
     *
     * @return the targetPos
     */
    private static @Nullable BlockPos getTargetPos() {
        Minecraft mc = Minecraft.getInstance();
        BlockHitResult hit = getBlockHitResult(mc);
        if (hit == null) { return null; }
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof CrucibleBlock)) { return null; }
        if (hitsBelowBasin(hit, pos)) { return null; }
        return pos;
    }

    /**
     * Returns the current block hit result, or null if the crosshair is not targeting a block.
     *
     * @param mc the Minecraft client instance
     * @return the block hit result, or null
     */
    private static @Nullable BlockHitResult getBlockHitResult(Minecraft mc) {
        if (mc.level == null || mc.hitResult == null) { return null; }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) { return null; }
        return (BlockHitResult) mc.hitResult;
    }

    /**
     * Returns true if the hit location is below the basin floor (fuel rod area).
     *
     * @param hit the block hit result
     * @param pos the block position
     * @return true if the condition is met
     */
    private static boolean hitsBelowBasin(BlockHitResult hit, BlockPos pos) {
        double localY = hit.getLocation().y - pos.getY();
        return localY < BASIN_MIN_Y;
    }

    /**
     * Looks up the CrucibleBlockEntity at the given position, or null.
     *
     * @param pos the block position
     * @return the crucible, or null if not found
     */
    private static @Nullable CrucibleBlockEntity lookupCrucible(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) { return null; }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof CrucibleBlockEntity cbe ? cbe : null;
    }

    /**
     * Renders the HUD panel on the basin rim at the farthest point from the camera.
     * Billboards to face the player with smoothed emerge animation.
     *
     * @param poseStack the pose stack for rendering
     * @param be the block entity instance
     * @param camera the render camera
     */
    private static void renderRimPanel(PoseStack poseStack, CrucibleBlockEntity be,
            Camera camera) {
        poseStack.pushPose();
        positionOnRim(poseStack, be.getBlockPos(), camera);
        CruciblePanelPainter.renderPanel(poseStack, be);
        poseStack.popPose();
    }

    /**
     * Transforms the pose stack to the best rim anchor: translates, billboards,
     * z-nudges, and scales to pixel units.
     *
     * @param poseStack the pose stack for rendering
     * @param pos the block position of the crucible
     * @param camera the render camera
     */
    private static void positionOnRim(PoseStack poseStack, BlockPos pos, Camera camera) {
        CrucibleRimMath.translateToRimPoint(poseStack, pos, camera.position(), camera);
        InWorldHud.applyBillboardRotation(poseStack, camera, currentPitch);
        poseStack.translate(0, 0, RIM_Z_NUDGE);
        poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);
    }
}
