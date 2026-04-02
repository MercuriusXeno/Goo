package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import com.mercuriusxeno.goo.block.HubBlock;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.ISlottedGooContainer;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.TapBlockEntity;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.world.item.ItemStack;
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
 * Renders an in-world HUD panel when the player's crosshair targets a canister
 * or hub canister. Shows multi-type goo contents (icon + amount per type)
 * and optional label for the targeted slot.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public class CanisterHudRenderer {

    /** Label color (gold). */
    private static final int LABEL_COLOR = 0xFFFFD700;

    /** Upgrade level color (aqua). */
    private static final int UPGRADE_COLOR = 0xFF55FFFF;

    /** Exponential smoothing time constant. */
    private static final float SMOOTH_TAU = 0.1f;

    /** Pitch threshold for retract completion. */
    private static final float RETRACT_THRESHOLD = 0.01f;

    /** Canister body height in blocks (12/16). */
    private static final double BODY_HEIGHT = 12.0 / 16.0;

    /** Mid-body Y for side-face anchoring (6/16). */
    private static final double MID_BODY = 6.0 / 16.0;

    /** Y position for the HUD below the canister block bottom. */
    private static final double BLOCK_BOTTOM = 0.0;

    /** Hub canister top in blocks (14/16 + 1/16 gap = 15/16). */
    private static final double HUB_CANISTER_TOP = 15.0 / 16.0;

    /** Mid-height for hub canister side faces (8/16). */
    private static final double HUB_MID = 8.0 / 16.0;

    /** Offset to push the panel to the face surface (half a block). */
    private static final double FACE_OFFSET = 0.5;

    /** Canister top Y on the tap (16/16 = full block height). */
    private static final double TAP_CANISTER_TOP = 1.0;

    /** Tap canister slot center offsets by facing (cx, cz in block coords). */
    private static final double[][] TAP_SLOT_CENTERS = {
        /* SOUTH */ { 8.0 / 16.0, 3.0 / 16.0 },
        /* NORTH */ { 8.0 / 16.0, 13.0 / 16.0 },
        /* EAST  */ { 13.0 / 16.0, 8.0 / 16.0 },
        /* WEST  */ { 3.0 / 16.0, 8.0 / 16.0 },
    };

    /** Sentinel slot value indicating this target is a tap canister (not a grid slot). */
    private static final int TAP_SLOT = -2;

    // --- Animation state ---
    private static @Nullable BlockPos trackedPos = null;
    private static int trackedSlot = -1;
    private static double trackedCx = 0.5;
    private static double trackedCz = 0.5;
    private static double trackedLift = BODY_HEIGHT;
    private static Direction trackedFace = Direction.UP;
    private static boolean trackedBlockAbove = false;
    private static float currentPitch = 0f;
    private static boolean retracting = false;
    private static final long[] lastFrameNanos = {0};

    /** Renders the canister HUD after entities. */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Target target = getTarget();
        float dt = InWorldHud.computeDeltaTime(lastFrameNanos);

        updateState(target, dt);

        if (trackedPos == null) return;

        SlotData data = lookupSlotData(trackedPos, trackedSlot);
        if (data == null || (data.contents.isEmpty() && data.compression <= 0)) {
            clearState();
            return;
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        renderPanel(event.getPoseStack(), camera, data, trackedPos, trackedSlot);
    }

    /** State machine: manages emerge and retract transitions. */
    private static void updateState(@Nullable Target target, float dt) {
        boolean hasTarget = target != null;
        boolean sameTarget = hasTarget && target.pos.equals(trackedPos) && target.slot == trackedSlot;

        if (hasTarget && (trackedPos == null || !sameTarget)) {
            trackedPos = target.pos;
            trackedSlot = target.slot;
            trackedCx = target.cx;
            trackedCz = target.cz;
            trackedLift = target.lift;
            trackedFace = target.hitFace;
            trackedBlockAbove = target.hasBlockAbove;
            currentPitch = 0f;
            retracting = false;
        } else if (hasTarget && sameTarget) {
            // Same pos/slot - keep animation state but update face and offsets
            // so the HUD follows the player's gaze as they move around the block.
            trackedCx = target.cx;
            trackedCz = target.cz;
            trackedLift = target.lift;
            trackedFace = target.hitFace;
            trackedBlockAbove = target.hasBlockAbove;
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
        trackedSlot = -1;
        currentPitch = 0f;
        retracting = false;
    }

    /** Returns the targeted canister slot, or null if not looking at a canister. */
    private static @Nullable Target getTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null) return null;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be instanceof CanisterBlockEntity) {
            int slot = CanisterBlock.hitSlot(hit, pos);
            if (slot >= 0) {
                boolean blockAbove = hasFullBlockAbove(mc.level, pos);
                Direction playerFacing = mc.player != null
                        ? mc.player.getDirection() : Direction.NORTH;
                return canisterTarget(pos, slot, face, blockAbove, playerFacing);
            }
        }
        if (be instanceof HubBlockEntity) {
            int slot = HubBlock.hitSlot(hit, pos);
            if (slot >= 0) {
                Vec3 look = mc.player != null ? mc.player.getLookAngle() : Vec3.ZERO;
                return hubTarget(pos, slot, face, look);
            }
            return hubFrameTarget(pos, face);
        }
        if (be instanceof TapBlockEntity tap && !tap.getCanister().isEmpty()) {
            Direction facing = mc.level.getBlockState(pos).getValue(TapBlock.FACING);
            return tapTarget(pos, facing);
        }
        return null;
    }

    /** Returns true if the block above has a full collision shape. */
    private static boolean hasFullBlockAbove(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        return level.getBlockState(above)
            .isCollisionShapeFullBlock(level, above);
    }

    /**
     * Builds a target for a canister block slot, using the 3x3 grid centers.
     * Positioning depends on whether a full block is above and which face was hit.
     */
    private static Target canisterTarget(BlockPos pos, int slot,
            Direction face, boolean blockAbove, Direction playerFacing) {
        float[] center = CanisterSlotLayout.SLOT_CENTERS[slot];
        double cx = center[0] / 16.0;
        double cz = center[1] / 16.0;

        if (face == Direction.DOWN) {
            return new Target(pos, slot, cx, cz, BLOCK_BOTTOM, Direction.DOWN, false);
        }
        if (!blockAbove) {
            return new Target(pos, slot, cx, cz, BODY_HEIGHT, Direction.UP, false);
        }
        if (face == Direction.UP) {
            // Block above would clip - redirect to the player-facing side
            return canisterSideTarget(pos, slot, playerFacing, cx, cz);
        }
        return canisterSideTarget(pos, slot, face, cx, cz);
    }

    /** Builds a side-face target for a canister slot: offset to the hit face, mid-body height. */
    private static Target canisterSideTarget(BlockPos pos, int slot,
            Direction face, double cx, double cz) {
        double offCx = cx + face.getStepX() * FACE_OFFSET;
        double offCz = cz + face.getStepZ() * FACE_OFFSET;
        return new Target(pos, slot, offCx, offCz, MID_BODY, face, false);
    }

    /**
     * Builds a target for a hub canister slot.
     * Side face: anchored on the face most perpendicular to the player's look vector,
     * so the HUD always appears on the side most visible to the player.
     * UP/DOWN hit: billboard above canister top.
     */
    private static Target hubTarget(BlockPos pos, int slot, Direction face, Vec3 look) {
        double[] center = HubBlock.SLOT_CENTERS[slot];
        double cx = center[0] / 16.0;
        double cz = center[1] / 16.0;

        if (face != Direction.UP && face != Direction.DOWN) {
            Direction bestFace = InWorldHud.bestPerpendicularFace(look);
            double offCx = cx + bestFace.getStepX() * FACE_OFFSET;
            double offCz = cz + bestFace.getStepZ() * FACE_OFFSET;
            return new Target(pos, slot, offCx, offCz, HUB_MID, bestFace, false);
        }
        return new Target(pos, slot, cx, cz, HUB_CANISTER_TOP, Direction.UP, false);
    }

    /** Builds a target for the hub frame (intake/spindle). Centered, HUD above intake. */
    private static @Nullable Target hubFrameTarget(BlockPos pos, Direction face) {
        return new Target(pos, -1, 0.5, 0.5, HUB_CANISTER_TOP, Direction.UP, false);
    }

    /** Builds a target for the tap's canister slot, anchored above the canister. */
    private static Target tapTarget(BlockPos pos, Direction facing) {
        int idx = switch (facing) {
            case SOUTH -> 0;
            case NORTH -> 1;
            case EAST  -> 2;
            case WEST  -> 3;
            default    -> 0;
        };
        double cx = TAP_SLOT_CENTERS[idx][0];
        double cz = TAP_SLOT_CENTERS[idx][1];
        return new Target(pos, TAP_SLOT, cx, cz, TAP_CANISTER_TOP, Direction.UP, false);
    }

    /** Looks up the goo contents, label, and compression at the given position and slot. */
    private static @Nullable SlotData lookupSlotData(BlockPos pos, int slot) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TapBlockEntity tap && slot == TAP_SLOT) {
            ItemStack canister = tap.getCanister();
            if (canister.isEmpty()) return null;
            int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(canister);
            GooContents contents = tap.getGooContents();
            return new SlotData(contents, null, compression);
        }
        if (be instanceof ISlottedGooContainer holder) {
            if (slot < 0) return null;
            CanisterMetadata meta = holder.getSlotMetadata(slot);
            ItemStack canister = holder.getCanister(slot);
            int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(canister);
            return new SlotData(holder.getSlotGooContents(slot),
                meta.label(), compression);
        }
        return null;
    }

    /** Renders the HUD panel at the tracked position, using face-aware rotation. */
    private static void renderPanel(PoseStack poseStack, Camera camera,
            SlotData data, BlockPos pos, int slot) {
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

        renderContent(poseStack, data);
        poseStack.popPose();
    }

    /**
     * Applies the appropriate rotation based on tracked face and block-above state.
     * Side faces use face rotation; UP with block above uses flat rotation;
     * UP without block above uses billboard rotation.
     */
    private static void applyRotation(PoseStack poseStack, Camera camera) {
        if (trackedFace != Direction.UP && trackedFace != Direction.DOWN) {
            InWorldHud.applyFaceRotation(poseStack, trackedFace);
            return;
        }
        if (trackedBlockAbove) {
            InWorldHud.applyFlatRotation(poseStack, camera);
            return;
        }
        InWorldHud.applyBillboardRotation(poseStack, camera, currentPitch);
    }

    /** Renders the panel content: label, upgrade level, and goo type/amount rows. */
    private static void renderContent(PoseStack poseStack, SlotData data) {
        Font font = Minecraft.getInstance().font;
        String label = data.label;
        boolean hasLabel = label != null && !label.isEmpty();
        boolean hasUpgrade = data.compression > 0;
        String upgradeText = hasUpgrade ? "Lv " + data.compression : "";

        float maxRowWidth = InWorldHud.computeMaxRowWidth(font, data.contents);
        float labelWidth = hasLabel ? font.width(label) : 0;
        float upgradeWidth = hasUpgrade ? font.width(upgradeText) : 0;
        float contentWidth = Math.max(maxRowWidth,
            Math.max(labelWidth, upgradeWidth));
        int gooRows = data.contents.typeCount();
        int headerRows = (hasLabel ? 1 : 0) + (hasUpgrade ? 1 : 0);
        int rowCount = headerRows + gooRows;
        float panelWidth = contentWidth + InWorldHud.BORDER * 2;
        float panelHeight = InWorldHud.BORDER * 2 + rowCount * InWorldHud.ROW_HEIGHT;

        // For the DOWN face, shift the panel downward so it hangs below the
        // block instead of extending upward into it.  The Y-axis is flipped by
        // the -PIXEL_SCALE scale, so a positive pixel-space translate moves the
        // drawn content to the opposite (downward) side of the anchor.
        if (trackedFace == Direction.DOWN) {
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
            InWorldHud.drawText(font, buffers, poseStack, label, contentX, textY, LABEL_COLOR);
            row++;
        }

        if (hasUpgrade) {
            float textY = baseY + row * InWorldHud.ROW_HEIGHT + (InWorldHud.ROW_HEIGHT - font.lineHeight) / 2f;
            InWorldHud.drawText(font, buffers, poseStack, upgradeText, contentX, textY, UPGRADE_COLOR);
            row++;
        }

        for (Map.Entry<GooType, Long> entry : data.contents.getAll().entrySet()) {
            float rowY = baseY + row * InWorldHud.ROW_HEIGHT;
            String amountText = GooTooltipHandler.formatFluidDisplayCompact(entry.getValue());
            InWorldHud.renderGooRow(poseStack, font, buffers, entry.getKey(), amountText, contentX, rowY);
            row++;
        }

        buffers.endBatch();
    }

    /** Targeted canister slot with XZ center offset, Y lift, hit face, and block-above state. */
    private record Target(BlockPos pos, int slot, double cx, double cz,
            double lift, Direction hitFace, boolean hasBlockAbove) {
    }

    /** Goo contents, label, and compression level for a targeted slot. */
    private record SlotData(GooContents contents, @Nullable String label, int compression) {
    }
}
