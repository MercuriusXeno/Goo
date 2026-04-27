package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Renders an in-world HUD panel when the player's crosshair targets a canister
 * or hub canister. Shows multi-type goo contents (icon + amount per type)
 * and optional label for the targeted slot.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CanisterHudRenderer {
    /**
     * Exponential smoothing time constant.
     */
    private static final float SMOOTH_TAU = 0.1f;

    /**
     * Pitch threshold for retract completion.
     */
    private static final float RETRACT_THRESHOLD = 0.01f;

    /**
     * Canister body height in blocks (12/16).
     */
    private static final double BODY_HEIGHT = 12.0 / 16.0;
    private static final long[] LAST_FRAME_NANOS = {0};
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

    private CanisterHudRenderer() {
    }

    /**
     * Renders the canister HUD after entities.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Target target = CanisterTargetResolver.getTarget();
        float dt = InWorldHud.computeDeltaTime(LAST_FRAME_NANOS);
        updateState(target, dt);

        if (trackedPos == null) {
            return;
        }

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
        if (data == null || (data.content.isEmpty() && data.compression <= 0)) {
            clearState();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        PanelAnchor anchor = new PanelAnchor(trackedCx, trackedLift, trackedCz,
                trackedFace, trackedBlockAbove, currentPitch);
        CanisterPanelPainter.renderPanel(poseStack, camera, data, trackedPos, trackedSlot, anchor);
    }

    /**
     * State machine: manages emerge and retract transitions.
     *
     * @param target the current aim target
     * @param dt     the delta time in seconds
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
        if (trackedPos != null && !retracting) {
            retracting = true;
        }
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
        if (retracting) {
            retracting = false;
        }
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
        trackedSlot = NO_SLOT;
        currentPitch = 0f;
        retracting = false;
    }

    /**
     * Looks up the goo contents, label, and compression at the given position and slot.
     *
     * @param pos  the block position
     * @param slot the slot index
     * @return the slotData, or null if not found
     */
    private static @Nullable SlotData lookupSlotData(BlockPos pos, int slot) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return dispatchSlotData(be, slot);
    }

    /**
     * Routes to the appropriate slot-data extractor based on block entity type.
     *
     * @param be   the block entity at the target position
     * @param slot the canister slot index
     * @return slot data for the matched entity, or null
     */
    private static @Nullable SlotData dispatchSlotData(@Nullable BlockEntity be, int slot) {
        if (be instanceof TapBlockEntity tap) {
            return matchTapSlot(tap, slot);
        }
        if (be instanceof ReactorBlockEntity reactor) {
            return matchReactorSlot(reactor, slot);
        }
        if (be instanceof ICanisterHolder holder) {
            return lookupContainerSlotData(holder, slot);
        }
        return null;
    }

    /**
     * Returns tap slot data only when the slot index matches the tap's dedicated slot.
     *
     * @param tap  the tap block entity
     * @param slot the requested slot index
     * @return slot data if the slot matches, or null
     */
    private static @Nullable SlotData matchTapSlot(TapBlockEntity tap, int slot) {
        return slot == CanisterTargetResolver.TAP_SLOT ? lookupTapSlotData(tap) : null;
    }

    /**
     * Returns reactor slot data only when the slot index matches the reactor's output slot.
     *
     * @param reactor the reactor block entity
     * @param slot    the requested slot index
     * @return slot data if the slot matches, or null
     */
    private static @Nullable SlotData matchReactorSlot(ReactorBlockEntity reactor, int slot) {
        return slot == CanisterTargetResolver.REACTOR_SLOT ? lookupReactorSlotData(reactor) : null;
    }

    /**
     * Extracts slot data from a tap block entity's held canister.
     *
     * @param tap the tap block entity
     * @return the slot data, or null if the tap holds no canister
     */
    private static @Nullable SlotData lookupTapSlotData(TapBlockEntity tap) {
        ItemStack canister = tap.getCanister();
        if (canister.isEmpty()) {
            return null;
        }
        int compression = GooEnchantments.getCompressionLevel(canister);
        return new SlotData(tap.getFluidContent(), null, compression);
    }

    /**
     * Extracts slot data from a reactor's output canister.
     *
     * @param reactor the reactor block entity
     * @return the slot data, or null if no output canister
     */
    private static @Nullable SlotData lookupReactorSlotData(ReactorBlockEntity reactor) {
        ItemStack canister = reactor.getOutputCanister();
        if (canister.isEmpty()) {
            return null;
        }
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        int compression = GooEnchantments.getCompressionLevel(canister);
        return new SlotData(content, null, compression);
    }

    /**
     * Extracts slot data from a slotted goo container at a specific slot index.
     *
     * @param holder the slotted goo container
     * @param slot   the slot index
     * @return the slot data, or null if the slot index is invalid
     */
    private static @Nullable SlotData lookupContainerSlotData(
            ICanisterHolder holder, int slot) {
        if (slot < 0) {
            return null;
        }
        CanisterMetadata meta = holder.getSlotMetadata(slot);
        ItemStack canister = holder.getCanister(slot);
        int compression = GooEnchantments.getCompressionLevel(canister);
        return new SlotData(holder.getSlotFluidContent(slot), meta.label(), compression);
    }

    /**
     * Targeted canister slot with XZ center offset, Y lift, hit face, and block-above state.
     */
    record Target(BlockPos pos, int slot, double cx, double cz,
                  double lift, Direction hitFace, boolean hasBlockAbove) {
    }

    /**
     * Fluid content, label, and compression level for a targeted slot.
     */
    record SlotData(CanisterFluidContent content, @Nullable String label, int compression) {
    }
}
