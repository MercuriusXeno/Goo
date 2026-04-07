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
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
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
public final class CanisterHudRenderer {
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

    /** Sentinel value for no tracked slot. */
    private static final int NO_SLOT = -1;

    /** Block-local coordinate to pixel conversion factor. */
    private static final double BLOCK_PIXELS = 16.0;

    /** Z-nudge for panels on upward/downward faces to prevent z-fighting. */
    private static final float Z_NUDGE_POS = 0.01f;

    /** Z-nudge for panels on side faces. */
    private static final float Z_NUDGE_NEG = -0.01f;

    /** Divisor for centering calculations. */
    private static final int HALF = 2;

    /** Index for SOUTH facing in TAP_SLOT_CENTERS. */
    private static final int TAP_SOUTH = 0;

    /** Index for NORTH facing in TAP_SLOT_CENTERS. */
    private static final int TAP_NORTH = 1;

    /** Index for EAST facing in TAP_SLOT_CENTERS. */
    private static final int TAP_EAST = 2;

    /** Index for WEST facing in TAP_SLOT_CENTERS. */
    private static final int TAP_WEST = 3;

    /** Upgrade level display prefix. */
    private static final String UPGRADE_PREFIX = "Lv ";

    /** Half-width divisor for panel centering. */
    private static final float HALF_F = 2f;

    /** Block center offset for hub frame targets. */
    private static final double BLOCK_CENTER = 0.5;

    /** Empty string for absent upgrade text. */
    private static final String EMPTY_UPGRADE = "";

    // --- Animation state ---
    private static @Nullable BlockPos trackedPos;
    private static int trackedSlot = NO_SLOT;
    private static double trackedCx = 0.5;
    private static double trackedCz = 0.5;
    private static double trackedLift = BODY_HEIGHT;
    private static Direction trackedFace = Direction.UP;
    private static boolean trackedBlockAbove;
    private static float currentPitch;
    private static boolean retracting;
    private static final long[] lastFrameNanos = {0};

    private CanisterHudRenderer() {}

    /**
     * Renders the canister HUD after entities.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Target target = getTarget();
        float dt = InWorldHud.computeDeltaTime(lastFrameNanos);
        updateState(target, dt);

        if (trackedPos == null) { return; }

        renderIfValid(event.getPoseStack());
    }

    /**
     * Looks up the tracked slot data and renders the panel if goo is present.
     * Clears state if the slot is empty or missing.
     *
     * @param poseStack the pose stack for rendering
     */
    private static void renderIfValid(PoseStack poseStack) {
        SlotData data = lookupSlotData(trackedPos, trackedSlot);
        if (data == null || (data.contents.isEmpty() && data.compression <= 0)) {
            clearState();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        renderPanel(poseStack, camera, data, trackedPos, trackedSlot);
    }

    /**
     * State machine: manages emerge and retract transitions.
     *
     * @param target the current aim target
     * @param dt the delta time in seconds
     */
    private static void updateState(@Nullable Target target, float dt) {
        applyTargetTransition(target);
        advancePitch(dt);
    }

    /**
     * Handles target arrival, departure, and same-target refresh transitions.
     *
     * @param target the current aim target, or null if not looking at one
     */
    private static void applyTargetTransition(@Nullable Target target) {
        if (target != null && !isSameTarget(target)) {
            adoptTarget(target);
        } else if (target != null) {
            refreshTarget(target);
        } else {
            beginRetractIfTracking();
        }
    }

    /**
     * Starts the retract animation if a target is still tracked.
     */
    private static void beginRetractIfTracking() {
        if (trackedPos != null && !retracting) { retracting = true; }
    }

    /**
     * Returns true if the given target matches the currently tracked position and slot.
     *
     * @param target the target to compare
     * @return true if position and slot both match
     */
    private static boolean isSameTarget(Target target) {
        return target.pos.equals(trackedPos) && target.slot == trackedSlot;
    }

    /**
     * Adopts a new target, resetting animation state for a fresh emerge.
     *
     * @param target the new target to track
     */
    private static void adoptTarget(Target target) {
        trackedPos = target.pos;
        trackedSlot = target.slot;
        applyTargetOffsets(target);
        currentPitch = 0f;
        retracting = false;
    }

    /**
     * Refreshes offsets from the same target so the HUD follows gaze changes.
     *
     * @param target the current target with updated offsets
     */
    private static void refreshTarget(Target target) {
        applyTargetOffsets(target);
        if (retracting) { retracting = false; }
    }

    /**
     * Copies spatial offsets from a target into the tracked state fields.
     *
     * @param target the target to copy from
     */
    private static void applyTargetOffsets(Target target) {
        trackedCx = target.cx;
        trackedCz = target.cz;
        trackedLift = target.lift;
        trackedFace = target.hitFace;
        trackedBlockAbove = target.hasBlockAbove;
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
        trackedSlot = NO_SLOT;
        currentPitch = 0f;
        retracting = false;
    }

    /**
     * Returns the targeted canister slot, or null if not looking at a canister.
     *
     * @return the target
     */
    private static @Nullable Target getTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null) { return null; }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) { return null; }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        return resolveBlockTarget(mc, hit, pos, be);
    }

    /**
     * Dispatches to the per-block-type target builder.
     *
     * @param mc the Minecraft instance
     * @param hit the block hit result
     * @param pos the block position
     * @param be the block entity at the hit position
     * @return the target, or null if not a supported block
     */
    private static @Nullable Target resolveBlockTarget(
            Minecraft mc, BlockHitResult hit, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof CanisterBlockEntity) { return getCanisterTarget(mc, hit, pos); }
        if (be instanceof HubBlockEntity) { return getHubTarget(mc, hit, pos); }
        if (be instanceof TapBlockEntity tap) { return getTapTarget(mc, pos, tap); }
        return null;
    }

    /**
     * Resolves a canister block hit into a slot target with face and block-above awareness.
     *
     * @param mc the Minecraft instance
     * @param hit the block hit result
     * @param pos the block position
     * @return the target, or null if the hit missed all slots
     */
    private static @Nullable Target getCanisterTarget(Minecraft mc,
            BlockHitResult hit, BlockPos pos) {
        int slot = CanisterBlock.hitSlot(hit, pos);
        if (slot < 0) { return null; }
        boolean blockAbove = hasFullBlockAbove(mc.level, pos);
        Direction playerFacing = mc.player != null
                ? mc.player.getDirection() : Direction.NORTH;
        return canisterTarget(pos, slot, hit.getDirection(), blockAbove, playerFacing);
    }

    /**
     * Resolves a hub block hit into either a slot target or a frame target.
     *
     * @param mc the Minecraft instance
     * @param hit the block hit result
     * @param pos the block position
     * @return the target (frame fallback if no slot was hit)
     */
    private static @Nullable Target getHubTarget(Minecraft mc,
            BlockHitResult hit, BlockPos pos) {
        int slot = HubBlock.hitSlot(hit, pos);
        if (slot >= 0) {
            Vec3 look = mc.player != null ? mc.player.getLookAngle() : Vec3.ZERO;
            return hubTarget(pos, slot, hit.getDirection(), look);
        }
        return hubFrameTarget(pos, hit.getDirection());
    }

    /**
     * Resolves a tap block hit into a canister target if the tap holds a canister.
     *
     * @param mc the Minecraft instance
     * @param pos the block position
     * @param tap the tap block entity
     * @return the target, or null if the tap has no canister
     */
    private static @Nullable Target getTapTarget(Minecraft mc,
            BlockPos pos, TapBlockEntity tap) {
        if (tap.getCanister().isEmpty()) { return null; }
        Direction facing = mc.level.getBlockState(pos).getValue(TapBlock.FACING);
        return tapTarget(pos, facing);
    }

    /**
     * Returns true if the block above has a full collision shape.
     *
     * @param level the current level
     * @param pos the block position
     * @return true if fullBlockAbove is present
     */
    private static boolean hasFullBlockAbove(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        return level.getBlockState(above)
            .isCollisionShapeFullBlock(level, above);
    }

    /**
     * Builds a target for a canister block slot, using the 3x3 grid centers.
     * Positioning depends on whether a full block is above and which face was hit.
     *
     * @param pos the block position
     * @param slot the slot index
     * @param face the block face direction
     * @param blockAbove the blockAbove
     * @param playerFacing the playerFacing
     * @return the result
     */
    private static Target canisterTarget(BlockPos pos, int slot,
            Direction face, boolean blockAbove, Direction playerFacing) {
        double cx = slotCenterX(slot);
        double cz = slotCenterZ(slot);
        if (face == Direction.DOWN) { return canisterDownTarget(pos, slot, cx, cz); }
        if (!blockAbove) { return canisterTopTarget(pos, slot, cx, cz); }
        Direction side = (face == Direction.UP) ? playerFacing : face;
        return canisterSideTarget(pos, slot, side, cx, cz);
    }

    /**
     * Builds a target anchored below a canister (viewed from underneath).
     *
     * @param pos the block position
     * @param slot the slot index
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @return the downward-facing target
     */
    private static Target canisterDownTarget(BlockPos pos, int slot, double cx, double cz) {
        return new Target(pos, slot, cx, cz, BLOCK_BOTTOM, Direction.DOWN, false);
    }

    /**
     * Builds a target anchored above the canister body top.
     *
     * @param pos the block position
     * @param slot the slot index
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @return the upward-facing target
     */
    private static Target canisterTopTarget(BlockPos pos, int slot, double cx, double cz) {
        return new Target(pos, slot, cx, cz, BODY_HEIGHT, Direction.UP, false);
    }

    /**
     * Returns the block-local X center for a canister slot (0..1 range).
     *
     * @param slot the slot index
     * @return the X coordinate in block units
     */
    private static double slotCenterX(int slot) {
        return CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS;
    }

    /**
     * Returns the block-local Z center for a canister slot (0..1 range).
     *
     * @param slot the slot index
     * @return the Z coordinate in block units
     */
    private static double slotCenterZ(int slot) {
        return CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS;
    }

    /**
     * Builds a side-face target for a canister slot: offset to the hit face, mid-body height.
     *
     * @param pos the block position
     * @param slot the slot index
     * @param face the block face direction
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @return the result
     */
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
     *
     * @param pos the block position
     * @param slot the slot index
     * @param face the block face direction
     * @param look the player look direction vector
     * @return the result
     */
    private static Target hubTarget(BlockPos pos, int slot, Direction face, Vec3 look) {
        double[] center = HubBlock.SLOT_CENTERS[slot];
        double cx = center[0] / BLOCK_PIXELS;
        double cz = center[1] / BLOCK_PIXELS;
        if (face != Direction.UP && face != Direction.DOWN) {
            return hubSideTarget(pos, slot, cx, cz, look);
        }
        return new Target(pos, slot, cx, cz, HUB_CANISTER_TOP, Direction.UP, false);
    }

    /**
     * Builds a side-face target for a hub slot, anchored on the face most
     * perpendicular to the player's look vector.
     *
     * @param pos the block position
     * @param slot the slot index
     * @param cx the center X in block coords
     * @param cz the center Z in block coords
     * @param look the player look direction vector
     * @return the side-anchored target
     */
    private static Target hubSideTarget(BlockPos pos, int slot,
            double cx, double cz, Vec3 look) {
        Direction bestFace = InWorldHud.bestPerpendicularFace(look);
        double offCx = cx + bestFace.getStepX() * FACE_OFFSET;
        double offCz = cz + bestFace.getStepZ() * FACE_OFFSET;
        return new Target(pos, slot, offCx, offCz, HUB_MID, bestFace, false);
    }

    /**
     * Builds a target for the hub frame (intake/spindle). Centered, HUD above intake.
     *
     * @param pos the block position
     * @param face the block face direction
     * @return the result
     */
    private static @Nullable Target hubFrameTarget(BlockPos pos, Direction face) {
        return new Target(pos, NO_SLOT, BLOCK_CENTER, BLOCK_CENTER, HUB_CANISTER_TOP, Direction.UP, false);
    }

    /**
     * Builds a target for the tap's canister slot, anchored above the canister.
     *
     * @param pos the block position
     * @param facing the block facing direction
     * @return the result
     */
    private static Target tapTarget(BlockPos pos, Direction facing) {
        int idx = tapSlotIndex(facing);
        double cx = TAP_SLOT_CENTERS[idx][0];
        double cz = TAP_SLOT_CENTERS[idx][1];
        return new Target(pos, TAP_SLOT, cx, cz, TAP_CANISTER_TOP, Direction.UP, false);
    }

    /**
     * Maps a horizontal facing direction to its TAP_SLOT_CENTERS array index.
     *
     * @param facing the horizontal direction
     * @return the array index
     */
    private static int tapSlotIndex(Direction facing) {
        return switch (facing) {
            case SOUTH -> TAP_SOUTH;
            case NORTH -> TAP_NORTH;
            case EAST  -> TAP_EAST;
            case WEST  -> TAP_WEST;
            default    -> TAP_SOUTH;
        };
    }

    /**
     * Looks up the goo contents, label, and compression at the given position and slot.
     *
     * @param pos the block position
     * @param slot the slot index
     * @return the slotData, or null if not found
     */
    private static @Nullable SlotData lookupSlotData(BlockPos pos, int slot) {
        Level level = Minecraft.getInstance().level;
        if (level == null) { return null; }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TapBlockEntity tap && slot == TAP_SLOT) { return lookupTapSlotData(tap); }
        if (be instanceof ISlottedGooContainer holder) { return lookupContainerSlotData(holder, slot); }
        return null;
    }

    /**
     * Extracts slot data from a tap block entity's held canister.
     *
     * @param tap the tap block entity
     * @return the slot data, or null if the tap holds no canister
     */
    private static @Nullable SlotData lookupTapSlotData(TapBlockEntity tap) {
        ItemStack canister = tap.getCanister();
        if (canister.isEmpty()) { return null; }
        int compression = GooEnchantments.getCompressionLevel(canister);
        return new SlotData(tap.getGooContents(), null, compression);
    }

    /**
     * Extracts slot data from a slotted goo container at a specific slot index.
     *
     * @param holder the slotted goo container
     * @param slot the slot index
     * @return the slot data, or null if the slot index is invalid
     */
    private static @Nullable SlotData lookupContainerSlotData(
            ISlottedGooContainer holder, int slot) {
        if (slot < 0) { return null; }
        CanisterMetadata meta = holder.getSlotMetadata(slot);
        ItemStack canister = holder.getCanister(slot);
        int compression = GooEnchantments.getCompressionLevel(canister);
        return new SlotData(holder.getSlotGooContents(slot), meta.label(), compression);
    }

    /**
     * Renders the HUD panel at the tracked position, using face-aware rotation.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param data the extracted render data
     * @param pos the block position
     * @param slot the slot index
     */
    private static void renderPanel(PoseStack poseStack, Camera camera,
            SlotData data, BlockPos pos, int slot) {
        poseStack.pushPose();
        applyPanelTransform(poseStack, camera, pos);
        renderContent(poseStack, data);
        poseStack.popPose();
    }

    /**
     * Positions, rotates, and scales the pose stack for panel rendering.
     * Translates to the camera-relative anchor, applies face/billboard rotation,
     * nudges to prevent z-fighting, and scales to pixel units.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param pos the block position
     */
    private static void applyPanelTransform(PoseStack poseStack,
            Camera camera, BlockPos pos) {
        translateToAnchor(poseStack, camera, pos);
        applyRotation(poseStack, camera);
        float zNudge = isVerticalFace() ? Z_NUDGE_POS : Z_NUDGE_NEG;
        poseStack.translate(0, 0, zNudge);
        poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);
    }

    /**
     * Translates the pose stack to the camera-relative anchor position.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param pos the block position
     */
    private static void translateToAnchor(PoseStack poseStack, Camera camera, BlockPos pos) {
        Vec3 cam = camera.position();
        poseStack.translate(
            pos.getX() + trackedCx - cam.x,
            pos.getY() + trackedLift - cam.y,
            pos.getZ() + trackedCz - cam.z);
    }

    /**
     * Returns true if the tracked face is vertical (UP or DOWN).
     *
     * @return true for vertical faces
     */
    private static boolean isVerticalFace() {
        return trackedFace == Direction.UP || trackedFace == Direction.DOWN;
    }

    /**
     * Applies the appropriate rotation based on tracked face and block-above state.
     * Side faces use face rotation; UP with block above uses flat rotation;
     * UP without block above uses billboard rotation.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     */
    private static void applyRotation(PoseStack poseStack, Camera camera) {
        if (!isVerticalFace()) {
            InWorldHud.applyFaceRotation(poseStack, trackedFace);
        } else if (trackedBlockAbove) {
            InWorldHud.applyFlatRotation(poseStack, camera);
        } else {
            InWorldHud.applyBillboardRotation(poseStack, camera, currentPitch);
        }
    }

    /**
     * Renders the panel content: label, upgrade level, and goo type/amount rows.
     *
     * @param poseStack the pose stack for rendering
     * @param data the extracted render data
     */
    private static void renderContent(PoseStack poseStack, SlotData data) {
        Font font = Minecraft.getInstance().font;
        PanelMetrics metrics = measurePanel(font, data);
        if (trackedFace == Direction.DOWN) {
            poseStack.translate(0, metrics.height, 0);
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        drawPanel(poseStack, font, buffers, data, metrics);
        buffers.endBatch();
    }

    /**
     * Measures panel dimensions based on label, upgrade text, and goo row content.
     *
     * @param font the font renderer for width measurement
     * @param data the slot data containing label, compression, and goo contents
     * @return the computed panel metrics
     */
    private static PanelMetrics measurePanel(Font font, SlotData data) {
        String label = data.label;
        boolean hasLabel = label != null && !label.isEmpty();
        boolean hasUpgrade = data.compression > 0;
        String upgradeText = hasUpgrade ? UPGRADE_PREFIX + data.compression : EMPTY_UPGRADE;
        float contentWidth = measureContentWidth(font, data.contents, label, upgradeText, hasLabel, hasUpgrade);
        int rowCount = countRows(data.contents.typeCount(), hasLabel, hasUpgrade);
        return buildMetrics(contentWidth, rowCount, label, upgradeText, hasLabel, hasUpgrade);
    }

    /**
     * Computes final panel width/height and assembles metrics.
     *
     * @param contentWidth the widest content row width
     * @param rowCount the total number of rows
     * @param label the label text
     * @param upgradeText the upgrade text
     * @param hasLabel whether a label is present
     * @param hasUpgrade whether an upgrade is present
     * @return the assembled panel metrics
     */
    private static PanelMetrics buildMetrics(float contentWidth, int rowCount,
            @Nullable String label, String upgradeText,
            boolean hasLabel, boolean hasUpgrade) {
        float width = contentWidth + InWorldHud.BORDER * HALF;
        float height = InWorldHud.BORDER * HALF + rowCount * InWorldHud.ROW_HEIGHT;
        return new PanelMetrics(width, height, label, upgradeText, hasLabel, hasUpgrade);
    }

    /**
     * Computes the widest content row across goo rows, label, and upgrade text.
     *
     * @param font the font renderer
     * @param contents the goo contents for row width measurement
     * @param label the label text, or null
     * @param upgradeText the upgrade text
     * @param hasLabel whether a label is present
     * @param hasUpgrade whether an upgrade line is present
     * @return the maximum content width in pixels
     */
    private static float measureContentWidth(Font font, GooContents contents,
            @Nullable String label, String upgradeText,
            boolean hasLabel, boolean hasUpgrade) {
        float maxRowWidth = InWorldHud.computeMaxRowWidth(font, contents);
        float labelWidth = hasLabel ? font.width(label) : 0;
        float upgradeWidth = hasUpgrade ? font.width(upgradeText) : 0;
        return Math.max(maxRowWidth, Math.max(labelWidth, upgradeWidth));
    }

    /**
     * Counts the total number of panel rows (headers + goo types).
     *
     * @param gooRows the number of goo type rows
     * @param hasLabel whether a label header is present
     * @param hasUpgrade whether an upgrade header is present
     * @return the total row count
     */
    private static int countRows(int gooRows, boolean hasLabel, boolean hasUpgrade) {
        int headerRows = (hasLabel ? 1 : 0) + (hasUpgrade ? 1 : 0);
        return headerRows + gooRows;
    }

    /**
     * Draws the background, header rows, and goo rows onto the panel.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source
     * @param data the slot data containing goo contents
     * @param metrics the pre-computed panel metrics
     */
    private static void drawPanel(PoseStack poseStack, Font font,
            MultiBufferSource.BufferSource buffers, SlotData data, PanelMetrics metrics) {
        float halfW = metrics.width / HALF_F;
        InWorldHud.renderBackground(poseStack, buffers, -halfW, -metrics.height, metrics.width, metrics.height);
        float contentX = -halfW + InWorldHud.BORDER;
        float baseY = -metrics.height + InWorldHud.BORDER;
        int row = drawHeaders(font, buffers, poseStack, metrics, contentX, baseY);
        renderGooRows(poseStack, font, buffers, data.contents, contentX, baseY, row);
    }

    /**
     * Draws the optional label and upgrade header rows, returning the next row index.
     *
     * @param font the font renderer
     * @param buffers the buffer source
     * @param poseStack the pose stack
     * @param metrics the panel metrics with header text
     * @param x the left X
     * @param baseY the panel content top Y
     * @return the row index after all headers
     */
    private static int drawHeaders(Font font, MultiBufferSource.BufferSource buffers,
            PoseStack poseStack, PanelMetrics metrics, float x, float baseY) {
        int row = 0;
        if (metrics.hasLabel) {
            row += renderHeaderRow(font, buffers, poseStack, metrics.label, x, baseY, row, LABEL_COLOR);
        }
        if (metrics.hasUpgrade) {
            row += renderHeaderRow(font, buffers, poseStack, metrics.upgradeText, x, baseY, row, UPGRADE_COLOR);
        }
        return row;
    }

    /**
     * Renders a single header text row (label or upgrade), vertically centered.
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

    /** Targeted canister slot with XZ center offset, Y lift, hit face, and block-above state. */
    private record Target(BlockPos pos, int slot, double cx, double cz,
            double lift, Direction hitFace, boolean hasBlockAbove) {
    }

    /** Goo contents, label, and compression level for a targeted slot. */
    private record SlotData(GooContents contents, @Nullable String label, int compression) {
    }

    /** Pre-computed panel dimensions and resolved header strings. */
    private record PanelMetrics(float width, float height,
            @Nullable String label, String upgradeText,
            boolean hasLabel, boolean hasUpgrade) {
    }
}
