package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.registry.GooEnchantments;
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

/**
 * Renders an in-world HUD panel when the player's crosshair targets a canister
 * or hub canister. Shows multi-type goo contents (icon + amount per type)
 * and optional label for the targeted slot.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CanisterHudRenderer {

    private static final HudAnimator<Target> ANIMATOR =
            new HudAnimator<>((a, b) -> a.pos.equals(b.pos) && a.slot == b.slot);

    private CanisterHudRenderer() {
    }

    /**
     * Renders the canister HUD after entities.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        ANIMATOR.tick(CanisterTargetResolver.getTarget());
        Target target = ANIMATOR.tracked();
        if (target == null) {
            return;
        }
        SlotData data = lookupSlotData(target.pos, target.slot);
        if (data == null || (data.content.isEmpty() && data.compression <= 0)) {
            ANIMATOR.clear();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        PanelAnchor anchor = new PanelAnchor(target.cx, target.lift, target.cz,
                target.hitFace, target.hasBlockAbove, ANIMATOR.pitch());
        CanisterPanelPainter.renderPanel(event.getPoseStack(), camera, data,
                target.pos, target.slot, anchor);
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
