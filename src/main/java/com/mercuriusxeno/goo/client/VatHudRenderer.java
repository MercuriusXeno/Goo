package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.VatBlockEntity;
import com.mercuriusxeno.goo.client.VatStackAggregator.VatStackData;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
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
import java.util.Map;

/**
 * Renders an in-world HUD panel when the player's crosshair targets a vat.
 * Shows aggregated stack contents, upgrade level, gasket info, and label.
 * Panel is placed on the face the player is looking at.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class VatHudRenderer {
    /** Upgrade level color (aqua). */
    private static final int UPGRADE_COLOR = 0xFF55FFFF;
    /** Name label color (gold). */
    private static final int LABEL_COLOR = 0xFFFFAA00;
    /** Stack size color (gray). */
    private static final int STACK_COLOR = 0xFFAAAAAA;
    /** Exponential smoothing time constant. */
    private static final float SMOOTH_TAU = 0.1f;
    /** Pitch threshold for retract completion. */
    private static final float RETRACT_THRESHOLD = 0.01f;
    /** Y position for the HUD above the vat block top (full-block height). */
    private static final double BLOCK_TOP = 1.0;
    /** Y position for the HUD below the vat block bottom. */
    private static final double BLOCK_BOTTOM = 0.0;
    /** Mid-block Y for side-face anchoring. */
    private static final double MID_BLOCK = 0.5;
    /** Offset to push the panel to the face surface (half a block). */
    private static final double FACE_OFFSET = 0.5;

    /** Block center offset for centering calculations. */
    private static final double BLOCK_CENTER = 0.5;

    /** Z-nudge for panels on upward/downward faces to prevent z-fighting. */
    private static final float Z_NUDGE_POS = 0.01f;

    /** Z-nudge for panels on side faces. */
    private static final float Z_NUDGE_NEG = -0.01f;

    /** Upgrade level display prefix. */
    private static final String UPGRADE_PREFIX = "Lv ";

    /** Stack size display prefix. */
    private static final String STACK_PREFIX = "Stack: ";

    /** Empty string for absent text fields. */
    private static final String EMPTY_TEXT = "";

    /** Divisor for centering calculations. */
    private static final int HALF = 2;

    /** Half-width divisor for panel centering. */
    private static final float HALF_F = 2f;

    // --- Animation state ---
    private static @Nullable BlockPos trackedPos;
    private static Direction trackedFace = Direction.UP;
    private static double trackedCx = 0.5;
    private static double trackedCz = 0.5;
    private static double trackedLift = BLOCK_TOP;
    private static float currentPitch;
    private static boolean retracting;
    private static final long[] lastFrameNanos = {0};

    private VatHudRenderer() {}


    /**
     * Renders the vat HUD after entities.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        VatTarget target = getTarget();
        float dt = InWorldHud.computeDeltaTime(lastFrameNanos);
        updateState(target, dt);
        if (trackedPos == null) { return; }

        VatStackData data = lookupVatData(trackedPos);
        if (data == null || (data.contents().isEmpty()
                && data.compression() <= 0 && !data.hasLabel())) {
            clearState();
            return;
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        renderPanel(event.getPoseStack(), camera, data, trackedPos);
    }

    /**
     * State machine: manages emerge and retract transitions.
     *
     * @param target the current aim target
     * @param dt the delta time in seconds
     */
    private static void updateState(@Nullable VatTarget target, float dt) {
        boolean hasTarget = target != null;
        boolean sameTarget = hasTarget && target.pos.equals(trackedPos);

        if (hasTarget && !sameTarget) {
            adoptTarget(target);
        } else if (hasTarget) {
            refreshTarget(target);
        } else if (trackedPos != null && !retracting) {
            retracting = true;
        }

        advancePitch(dt);
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
        if (retracting) { retracting = false; }
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
        if (trackedPos == null) { return; }

        float targetPitch = retracting ? 0f : 1f;
        currentPitch = InWorldHud.smoothToward(currentPitch, targetPitch, dt, SMOOTH_TAU);

        if (retracting && currentPitch < RETRACT_THRESHOLD) {
            clearState();
        }
    }

    /** Resets all state. */
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
        if (mc.level == null || mc.hitResult == null) { return null; }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) { return null; }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be instanceof VatBlockEntity) {
            Direction face = hit.getDirection();
            if (face == Direction.UP) {
                return new VatTarget(pos, Direction.UP, BLOCK_CENTER, BLOCK_CENTER, BLOCK_TOP);
            }
            if (face == Direction.DOWN) {
                return new VatTarget(pos, Direction.DOWN, BLOCK_CENTER, BLOCK_CENTER, BLOCK_BOTTOM);
            }
            // Side face: pick the face most visible to the player.
            Vec3 look = mc.player != null ? mc.player.getLookAngle() : Vec3.ZERO;
            Direction bestFace = InWorldHud.bestPerpendicularFace(look);
            double cx = BLOCK_CENTER + bestFace.getStepX() * FACE_OFFSET;
            double cz = BLOCK_CENTER + bestFace.getStepZ() * FACE_OFFSET;
            return new VatTarget(pos, bestFace, cx, cz, MID_BLOCK);
        }
        return null;
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
        if (level == null) { return null; }
        return VatStackAggregator.aggregate(level, pos);
    }

    /**
     * Renders the HUD panel on the tracked face of the vat block.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param data the extracted render data
     * @param pos the block position
     */
    private static void renderPanel(PoseStack poseStack, Camera camera,
            VatStackData data, BlockPos pos) {
        Vec3 cam = camera.position();
        double rx = pos.getX() + trackedCx - cam.x;
        double ry = pos.getY() + trackedLift - cam.y;
        double rz = pos.getZ() + trackedCz - cam.z;

        poseStack.pushPose();
        poseStack.translate(rx, ry, rz);
        applyRotation(poseStack, camera);
        float zNudge = (trackedFace == Direction.UP || trackedFace == Direction.DOWN)
                ? Z_NUDGE_POS : Z_NUDGE_NEG;
        poseStack.translate(0, 0, zNudge);
        poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);

        renderContent(poseStack, data, trackedFace);
        poseStack.popPose();
    }

    /**
     * Applies face-aware rotation: side faces use face rotation to lie flat
     * against the block surface; UP/DOWN use billboard rotation.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     */
    private static void applyRotation(PoseStack poseStack, Camera camera) {
        if (trackedFace != Direction.UP && trackedFace != Direction.DOWN) {
            InWorldHud.applyFaceRotation(poseStack, trackedFace);
            return;
        }
        InWorldHud.applyBillboardRotation(poseStack, camera, currentPitch);
    }

    /**
     * Renders the panel content: label, upgrade level, stack size, goo rows.
     *
     * @param poseStack the pose stack for rendering
     * @param data the extracted render data
     * @param face the block face direction
     */
    private static void renderContent(PoseStack poseStack,
            VatStackData data, Direction face) {
        Font font = Minecraft.getInstance().font;
        boolean hasUpgrade = data.compression() > 0;
        boolean hasLabel = data.hasLabel();
        boolean showStack = data.stackSize() > 1;
        String upgradeText = hasUpgrade ? UPGRADE_PREFIX + data.compression() : EMPTY_TEXT;
        String stackText = showStack ? STACK_PREFIX + data.stackSize() : EMPTY_TEXT;

        float maxRowWidth = InWorldHud.computeMaxRowWidth(font, data.contents());
        float upgradeWidth = hasUpgrade ? font.width(upgradeText) : 0;
        float stackWidth = showStack ? font.width(stackText) : 0;
        float labelWidth = hasLabel ? font.width(data.label()) : 0;
        float contentWidth = Math.max(maxRowWidth,
            Math.max(upgradeWidth, Math.max(labelWidth, stackWidth)));
        int gooRows = data.contents().typeCount();
        int headerRows = (hasLabel ? 1 : 0) + (showStack ? 1 : 0)
            + (hasUpgrade ? 1 : 0);
        int rowCount = headerRows + gooRows;
        float panelWidth = contentWidth + InWorldHud.BORDER * HALF;
        float panelHeight = InWorldHud.BORDER * HALF + rowCount * InWorldHud.ROW_HEIGHT;

        if (face == Direction.DOWN) {
            poseStack.translate(0, panelHeight, 0);
        }

        MultiBufferSource.BufferSource buffers =
            Minecraft.getInstance().renderBuffers().bufferSource();

        float halfW = panelWidth / HALF_F;
        InWorldHud.renderBackground(poseStack, buffers,
            -halfW, -panelHeight, panelWidth, panelHeight);

        float contentX = -halfW + InWorldHud.BORDER;
        float baseY = -panelHeight + InWorldHud.BORDER;
        int row = 0;

        if (hasLabel) {
            row += renderHeaderRow(font, buffers, poseStack, data.label(), contentX, baseY, row, LABEL_COLOR);
        }
        if (showStack) {
            row += renderHeaderRow(font, buffers, poseStack, stackText, contentX, baseY, row, STACK_COLOR);
        }
        if (hasUpgrade) {
            row += renderHeaderRow(font, buffers, poseStack, upgradeText, contentX, baseY, row, UPGRADE_COLOR);
        }

        renderGooRows(poseStack, font, buffers, data.contents(), contentX, baseY, row);
        buffers.endBatch();
    }

    /**
     * Renders a single header text row, vertically centered within its row slot.
     *
     * @param font the font renderer
     * @param buffers the buffer source
     * @param poseStack the pose stack
     * @param text the header text
     * @param x the left X
     * @param baseY the panel content top Y
     * @param row the current row index
     * @param color the text color
     * @return 1, for row-counter advancement
     */
    private static int renderHeaderRow(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float baseY, int row, int color) {
        float textY = baseY + row * InWorldHud.ROW_HEIGHT
            + (InWorldHud.ROW_HEIGHT - font.lineHeight) / HALF_F;
        InWorldHud.drawText(font, buffers, poseStack, text, x, textY, color);
        return 1;
    }

    /**
     * Renders all goo type rows starting at the given row offset.
     *
     * @param poseStack the pose stack
     * @param font the font renderer
     * @param buffers the buffer source
     * @param contents the goo contents to render
     * @param x the left X
     * @param baseY the panel content top Y
     * @param startRow the first row index for goo rows
     */
    private static void renderGooRows(PoseStack poseStack, Font font,
            MultiBufferSource buffers, GooContents contents,
            float x, float baseY, int startRow) {
        int row = startRow;
        for (Map.Entry<GooType, Long> entry : contents.getAll().entrySet()) {
            float rowY = baseY + row * InWorldHud.ROW_HEIGHT;
            String amountText = GooTooltipHandler.formatFluidDisplayCompact(entry.getValue());
            InWorldHud.renderGooRow(poseStack, font, buffers, entry.getKey(), amountText, x, rowY);
            row++;
        }
    }

    /** Target: vat position, face, XZ center offset, and Y lift. */
    private record VatTarget(BlockPos pos, Direction face, double cx, double cz, double lift) {
    }
}
