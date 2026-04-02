package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.VatBlockEntity;
import com.mercuriusxeno.goo.client.VatStackAggregator.VatStackData;
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
public class VatHudRenderer {

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

    // --- Animation state ---
    private static @Nullable BlockPos trackedPos = null;
    private static Direction trackedFace = Direction.UP;
    private static double trackedCx = 0.5;
    private static double trackedCz = 0.5;
    private static double trackedLift = BLOCK_TOP;
    private static float currentPitch = 0f;
    private static boolean retracting = false;
    private static final long[] lastFrameNanos = {0};

    /** Renders the vat HUD after entities. */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        VatTarget target = getTarget();
        float dt = InWorldHud.computeDeltaTime(lastFrameNanos);
        updateState(target, dt);
        if (trackedPos == null) return;

        VatStackData data = lookupVatData(trackedPos);
        if (data == null || (data.contents().isEmpty()
                && data.compression() <= 0 && !data.hasLabel())) {
            clearState();
            return;
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        renderPanel(event.getPoseStack(), camera, data, trackedPos);
    }

    /** State machine: manages emerge and retract transitions. */
    private static void updateState(@Nullable VatTarget target, float dt) {
        boolean hasTarget = target != null;
        boolean sameTarget = hasTarget && target.pos.equals(trackedPos);

        if (hasTarget && (trackedPos == null || !sameTarget)) {
            trackedPos = target.pos;
            trackedFace = target.face;
            trackedCx = target.cx;
            trackedCz = target.cz;
            trackedLift = target.lift;
            currentPitch = 0f;
            retracting = false;
        } else if (hasTarget && sameTarget) {
            trackedFace = target.face;
            trackedCx = target.cx;
            trackedCz = target.cz;
            trackedLift = target.lift;
            if (retracting) retracting = false;
        } else if (!hasTarget && trackedPos != null && !retracting) {
            retracting = true;
        }

        if (trackedPos == null) return;

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
        trackedCx = 0.5;
        trackedCz = 0.5;
        trackedLift = BLOCK_TOP;
        currentPitch = 0f;
        retracting = false;
    }

    /** Returns the targeted vat with hit face, or null if not looking at a vat. */
    private static @Nullable VatTarget getTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null) return null;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be instanceof VatBlockEntity) {
            Direction face = hit.getDirection();
            if (face == Direction.UP) {
                return new VatTarget(pos, Direction.UP, 0.5, 0.5, BLOCK_TOP);
            }
            if (face == Direction.DOWN) {
                return new VatTarget(pos, Direction.DOWN, 0.5, 0.5, BLOCK_BOTTOM);
            }
            // Side face: pick the face most visible to the player.
            Vec3 look = mc.player != null ? mc.player.getLookAngle() : Vec3.ZERO;
            Direction bestFace = InWorldHud.bestPerpendicularFace(look);
            double cx = 0.5 + bestFace.getStepX() * FACE_OFFSET;
            double cz = 0.5 + bestFace.getStepZ() * FACE_OFFSET;
            return new VatTarget(pos, bestFace, cx, cz, MID_BLOCK);
        }
        return null;
    }

    /** Looks up aggregated vat stack data at the given position. */
    @Nullable
    private static VatStackData lookupVatData(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        return VatStackAggregator.aggregate(level, pos);
    }

    /** Renders the HUD panel on the tracked face of the vat block. */
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
                ? 0.01f : -0.01f;
        poseStack.translate(0, 0, zNudge);
        poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);

        renderContent(poseStack, data, trackedFace);
        poseStack.popPose();
    }

    /**
     * Applies face-aware rotation: side faces use face rotation to lie flat
     * against the block surface; UP/DOWN use billboard rotation.
     */
    private static void applyRotation(PoseStack poseStack, Camera camera) {
        if (trackedFace != Direction.UP && trackedFace != Direction.DOWN) {
            InWorldHud.applyFaceRotation(poseStack, trackedFace);
            return;
        }
        InWorldHud.applyBillboardRotation(poseStack, camera, currentPitch);
    }

    /** Renders the panel content: label, upgrade level, stack size, goo rows. */
    private static void renderContent(PoseStack poseStack,
            VatStackData data, Direction face) {
        Font font = Minecraft.getInstance().font;
        boolean hasUpgrade = data.compression() > 0;
        boolean hasLabel = data.hasLabel();
        boolean showStack = data.stackSize() > 1;
        String upgradeText = hasUpgrade ? "Lv " + data.compression() : "";
        String stackText = showStack ? "Stack: " + data.stackSize() : "";

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
        float panelWidth = contentWidth + InWorldHud.BORDER * 2;
        float panelHeight = InWorldHud.BORDER * 2 + rowCount * InWorldHud.ROW_HEIGHT;

        // For the DOWN face, shift the panel downward so it hangs below the
        // block instead of extending upward into it.  The Y-axis is flipped by
        // the -PIXEL_SCALE scale, so a positive pixel-space translate moves the
        // drawn content to the opposite (downward) side of the anchor.
        if (face == Direction.DOWN) {
            poseStack.translate(0, panelHeight, 0);
        }

        MultiBufferSource.BufferSource buffers =
            Minecraft.getInstance().renderBuffers().bufferSource();

        float halfW = panelWidth / 2f;
        InWorldHud.renderBackground(poseStack, buffers,
            -halfW, -panelHeight, panelWidth, panelHeight);

        float contentX = -halfW + InWorldHud.BORDER;
        float baseY = -panelHeight + InWorldHud.BORDER;
        int row = 0;

        if (hasLabel) {
            float textY = baseY + (InWorldHud.ROW_HEIGHT - font.lineHeight) / 2f;
            InWorldHud.drawText(font, buffers, poseStack, data.label(),
                contentX, textY, LABEL_COLOR);
            row++;
        }

        if (showStack) {
            float textY = baseY + row * InWorldHud.ROW_HEIGHT + (InWorldHud.ROW_HEIGHT - font.lineHeight) / 2f;
            InWorldHud.drawText(font, buffers, poseStack, stackText,
                contentX, textY, STACK_COLOR);
            row++;
        }

        if (hasUpgrade) {
            float textY = baseY + row * InWorldHud.ROW_HEIGHT + (InWorldHud.ROW_HEIGHT - font.lineHeight) / 2f;
            InWorldHud.drawText(font, buffers, poseStack, upgradeText,
                contentX, textY, UPGRADE_COLOR);
            row++;
        }

        for (Map.Entry<GooType, Long> entry : data.contents().getAll().entrySet()) {
            float rowY = baseY + row * InWorldHud.ROW_HEIGHT;
            String amountText = GooTooltipHandler.formatFluidDisplayCompact(entry.getValue());
            InWorldHud.renderGooRow(poseStack, font, buffers, entry.getKey(), amountText, contentX, rowY);
            row++;
        }

        buffers.endBatch();
    }

    /** Target: vat position, face, XZ center offset, and Y lift. */
    private record VatTarget(BlockPos pos, Direction face, double cx, double cz, double lift) {
    }
}
