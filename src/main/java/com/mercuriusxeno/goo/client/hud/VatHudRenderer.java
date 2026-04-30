package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.client.machine.VatStackAggregator;
import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Renders an in-world HUD panel when the player's crosshair targets a vat.
 * Shows aggregated stack contents, upgrade level, gasket info, and label.
 * Panel is placed on the face the player is looking at.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class VatHudRenderer {
    /**
     * Exponential smoothing time constant.
     */
    private static final float SMOOTH_TAU = 0.1f;
    /**
     * Pitch threshold for retract completion.
     */
    private static final float RETRACT_THRESHOLD = 0.01f;
    /**
     * Y position for the HUD above the vat block top (full-block height).
     */
    private static final double BLOCK_TOP = 1.0;
    /**
     * Y position for the HUD below the vat block bottom.
     */
    private static final double BLOCK_BOTTOM = 0.0;
    /**
     * Mid-block Y for side-face anchoring.
     */
    private static final double MID_BLOCK = 0.5;
    /**
     * Offset to push the panel to the face surface (half a block).
     */
    private static final double FACE_OFFSET = 0.5;

    /**
     * Block center offset for centering calculations.
     */
    private static final double BLOCK_CENTER = 0.5;

    /**
     * Z-nudge for panels on upward/downward faces to prevent z-fighting.
     */
    private static final float Z_NUDGE_POS = 0.01f;

    /**
     * Z-nudge for panels on side faces.
     */
    private static final float Z_NUDGE_NEG = -0.01f;
    private static final long[] LAST_FRAME_NANOS = {0};
    // --- Animation state ---
    private static @Nullable BlockPos trackedPos;
    private static Direction trackedFace = Direction.UP;
    private static double trackedCx = 0.5;
    private static double trackedCz = 0.5;
    private static double trackedLift = BLOCK_TOP;
    private static float currentPitch;
    private static boolean retracting;

    private VatHudRenderer() {
    }


    /**
     * Renders the vat HUD after entities.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        updateState(getTarget(), InWorldHud.computeDeltaTime(LAST_FRAME_NANOS));
        if (trackedPos == null) {
            return;
        }
        renderIfNonEmpty(event.getPoseStack(), trackedPos);
    }

    /**
     * Looks up vat data and renders the HUD if the vat has displayable content.
     *
     * @param poseStack the pose stack
     * @param pos       the tracked vat position
     */
    private static void renderIfNonEmpty(PoseStack poseStack, BlockPos pos) {
        VatStackData data = lookupVatData(pos);
        if (isEmptyVat(data)) {
            clearState();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        renderPanel(poseStack, camera, data, pos);
    }

    /**
     * Returns true if the vat data is absent or has no displayable content.
     *
     * @param data the vat stack data, or null
     * @return true if there is nothing to render
     */
    private static boolean isEmptyVat(@Nullable VatStackData data) {
        return data == null || (data.contents().isEmpty()
                && data.compression() <= 0 && !data.hasLabel());
    }

    /**
     * State machine: manages emerge and retract transitions.
     *
     * @param target the current aim target
     * @param dt     the delta time in seconds
     */
    private static void updateState(@Nullable VatTarget target, float dt) {
        applyTargetTransition(target);
        advancePitch(dt);
    }

    /**
     * Transitions the tracked target based on whether the aim changed, persists, or vanished.
     *
     * @param target the current aim target, or null if none
     */
    private static void applyTargetTransition(@Nullable VatTarget target) {
        if (target != null) {
            applyAimTarget(target);
        } else if (trackedPos != null && !retracting) {
            retracting = true;
        }
    }

    /**
     * Adopts or refreshes the aim target depending on whether the position changed.
     *
     * @param target the non-null aim target
     */
    private static void applyAimTarget(VatTarget target) {
        if (target.pos.equals(trackedPos)) {
            refreshTarget(target);
        } else {
            adoptTarget(target);
        }
    }

    /**
     * Adopts a new target, resetting animation state for a fresh emerge.
     *
     * @param target the new target to track
     */
    private static void adoptTarget(VatTarget target) {
        trackedPos = target.pos;
        applyTargetOffsets(target);
        currentPitch = 0f;
        retracting = false;
    }

    /**
     * Refreshes offsets from the same target so the HUD follows gaze changes.
     *
     * @param target the current target with updated offsets
     */
    private static void refreshTarget(VatTarget target) {
        applyTargetOffsets(target);
        if (retracting) {
            retracting = false;
        }
    }

    /**
     * Copies spatial offsets from a target into the tracked state fields.
     *
     * @param target the target to copy from
     */
    private static void applyTargetOffsets(VatTarget target) {
        trackedFace = target.face;
        trackedCx = target.cx;
        trackedCz = target.cz;
        trackedLift = target.lift;
    }

    /**
     * Advances the pitch toward the target value and completes retract if flush.
     *
     * @param dt the delta time in seconds
     */
    private static void advancePitch(float dt) {
        if (trackedPos == null) {
            return;
        }

        float targetPitch = retracting ? 0f : 1f;
        currentPitch = InWorldHud.smoothToward(currentPitch, targetPitch, dt, SMOOTH_TAU);

        if (retracting && currentPitch < RETRACT_THRESHOLD) {
            clearState();
        }
    }

    /**
     * Resets all state.
     */
    private static void clearState() {
        trackedPos = null;
        trackedFace = Direction.UP;
        trackedCx = BLOCK_CENTER;
        trackedCz = BLOCK_CENTER;
        trackedLift = BLOCK_TOP;
        currentPitch = 0f;
        retracting = false;
    }

    /**
     * Returns the targeted vat with hit face, or null if not looking at a vat.
     *
     * @return the target
     */
    private static @Nullable VatTarget getTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null) {
            return null;
        }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof VatBlockEntity)) {
            return null;
        }
        return resolveVatFace(mc, pos, hit.getDirection());
    }

    /**
     * Resolves a vat target from the hit face direction.
     *
     * @param mc   the Minecraft client instance
     * @param pos  the vat block position
     * @param face the face the player is looking at
     * @return the resolved target
     */
    private static VatTarget resolveVatFace(Minecraft mc, BlockPos pos, Direction face) {
        if (face == Direction.UP) {
            return new VatTarget(pos, Direction.UP, BLOCK_CENTER, BLOCK_CENTER, BLOCK_TOP);
        }
        if (face == Direction.DOWN) {
            return new VatTarget(pos, Direction.DOWN, BLOCK_CENTER, BLOCK_CENTER, BLOCK_BOTTOM);
        }
        Vec3 look = mc.player != null ? mc.player.getLookAngle() : Vec3.ZERO;
        Direction bestFace = InWorldHud.bestPerpendicularFace(look);
        double cx = BLOCK_CENTER + bestFace.getStepX() * FACE_OFFSET;
        double cz = BLOCK_CENTER + bestFace.getStepZ() * FACE_OFFSET;
        return new VatTarget(pos, bestFace, cx, cz, MID_BLOCK);
    }

    /**
     * Looks up aggregated vat stack data at the given position.
     *
     * @param pos the block position
     * @return the vatData, or null if not found
     */
    @Nullable
    private static VatStackData lookupVatData(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return VatStackAggregator.aggregate(level, pos);
    }

    /**
     * Renders the HUD panel on the tracked face of the vat block.
     *
     * @param poseStack the pose stack for rendering
     * @param camera    the render camera
     * @param data      the extracted render data
     * @param pos       the block position
     */
    private static void renderPanel(PoseStack poseStack, Camera camera, VatStackData data, BlockPos pos) {
        Vec3 cam = camera.position();
        poseStack.pushPose();
        poseStack.translate(pos.getX() + trackedCx - cam.x,
                pos.getY() + trackedLift - cam.y, pos.getZ() + trackedCz - cam.z);
        applyRotation(poseStack, camera);
        VatHudPanelPainter.renderContent(poseStack, data, trackedFace);
        poseStack.popPose();
    }

    /**
     * Applies face-aware rotation, z-fighting nudge, and pixel scale.
     * Side faces lie flat against the block surface; UP/DOWN billboard toward the camera.
     *
     * @param poseStack the pose stack for rendering
     * @param camera    the render camera
     */
    private static void applyRotation(PoseStack poseStack, Camera camera) {
        boolean vertical = trackedFace == Direction.UP || trackedFace == Direction.DOWN;
        if (vertical) {
            InWorldHud.applyBillboardRotation(poseStack, camera, currentPitch);
        } else {
            InWorldHud.applyFaceRotation(poseStack, trackedFace);
        }
        poseStack.translate(0, 0, vertical ? Z_NUDGE_POS : Z_NUDGE_NEG);
        poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);
    }

    /**
     * Target: vat position, face, XZ center offset, and Y lift.
     */
    private record VatTarget(BlockPos pos, Direction face, double cx, double cz, double lift) {
    }
}
