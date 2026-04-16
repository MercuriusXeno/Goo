package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.OmniblobQuickCraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

/**
 * Server-side mixin to handle omniblob quickcraft (drag-to-distribute).
 * Vanilla quickcraft is entirely count-based and blocks omniblobs at multiple
 * points. This mixin intercepts all three phases when the carried item is an
 * omniblob and performs volume-based distribution instead.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class OmniblobQuickCraftMixin {

    /** Mixin target method name for click injection. */
    private static final String TARGET_METHOD = "doClick";
    /** Mixin injection point at the method head. */
    private static final String INJECT_AT = "HEAD";

    /** Quickcraft phase: drag started, recording type. */
    private static final int PHASE_START = 0;
    /** Quickcraft phase: collecting slots as the cursor drags. */
    private static final int PHASE_COLLECT = 1;
    /** Quickcraft phase: distribute volume across collected slots. */
    private static final int PHASE_DISTRIBUTE = 2;
    /** Sentinel return value indicating the slot holds an incompatible goo type. */
    private static final int INCOMPATIBLE_SLOT = -1;

    @Shadow
    private int quickcraftType;

    @Shadow
    private int quickcraftStatus;

    @Shadow
    @Final
    private Set<Slot> quickcraftSlots;

    @Shadow
    public abstract ItemStack getCarried();

    @Shadow
    public abstract void setCarried(ItemStack stack);

    @Shadow
    protected abstract void resetQuickCraft();

    @Shadow
    public abstract boolean canDragTo(Slot slot);

    @Shadow
    public abstract void broadcastChanges();

    /**
     * Intercepts doClick when clickType is QUICK_CRAFT and carried item is an omniblob.
     * Handles all three quickcraft phases with volume-based logic instead of count-based.
     *
     * @param slotId    the clicked slot index
     * @param button    the mouse button code
     * @param clickType the container input type
     * @param player    the interacting player
     * @param ci        the mixin callback info
     */
    @Inject(method = TARGET_METHOD, at = @At(INJECT_AT), cancellable = true)
    private void goo$omniblobQuickCraft(int slotId, int button, ContainerInput clickType,
            Player player, CallbackInfo ci) {
        if (clickType != ContainerInput.QUICK_CRAFT) { return; }
        if (!OmniblobQuickCraft.isOmniblobQuickCraft(getCarried())) { return; }

        int header = AbstractContainerMenu.getQuickcraftHeader(button);
        handleOmniblobPhase(slotId, button, header, player);
        ci.cancel();
    }

    /**
     * Dispatches to the appropriate quickcraft phase for omniblob distribution.
     *
     * @param slotId the clicked slot index
     * @param button the mouse button code
     * @param header the quickcraft phase header
     * @param player the interacting player
     */
    private void handleOmniblobPhase(int slotId, int button, int header, Player player) {
        int previousStatus = quickcraftStatus;
        quickcraftStatus = header;

        if (!isValidTransition(previousStatus, header)) {
            resetQuickCraft();
            return;
        }

        switch (header) {
            case PHASE_START -> handlePhaseStart(button, player);
            case PHASE_COLLECT -> handlePhaseCollect(slotId);
            case PHASE_DISTRIBUTE -> handlePhaseDistribute(player);
            default -> resetQuickCraft();
        }
    }

    /**
     * Returns true if the phase transition is valid. Mirrors vanilla validation:
     * previous must be 0 for start, or previous+1 == current for progression.
     *
     * @param previousStatus the previous quickcraft status
     * @param newStatus      the new quickcraft status
     * @return true if the transition is valid
     */
    private boolean isValidTransition(int previousStatus, int newStatus) {
        if (getCarried().isEmpty()) { return false; }
        return isPhaseRepeat(previousStatus, newStatus)
            || isCollectToDistribute(previousStatus, newStatus);
    }

    /**
     * Returns true if the previous and new status represent the same phase.
     *
     * @param prev the previous quickcraft status
     * @param next the new quickcraft status
     * @return true if the phase is unchanged
     */
    private static boolean isPhaseRepeat(int prev, int next) {
        return prev == next;
    }

    /**
     * Returns true if transitioning from the collect phase to the distribute phase.
     *
     * @param prev the previous quickcraft status
     * @param next the new quickcraft status
     * @return true if advancing from collect to distribute
     */
    private static boolean isCollectToDistribute(int prev, int next) {
        return prev == PHASE_COLLECT && next == PHASE_DISTRIBUTE;
    }

    /**
     * Phase 0: start quickcraft. Records drag type and clears collected slots.
     *
     * @param button the mouse button code
     * @param player the interacting player
     */
    private void handlePhaseStart(int button, Player player) {
        quickcraftType = AbstractContainerMenu.getQuickcraftType(button);
        if (AbstractContainerMenu.isValidQuickcraftType(quickcraftType, player)) {
            quickcraftStatus = PHASE_COLLECT;
            quickcraftSlots.clear();
        } else {
            resetQuickCraft();
        }
    }

    /**
     * Phase 1: collect slots. Replaces canItemQuickReplace with a volume-aware
     * check that allows same-type goo merging into occupied slots.
     *
     * @param slotId the slot index being dragged over
     */
    private void handlePhaseCollect(int slotId) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        Slot slot = self.slots.get(slotId);
        ItemStack carried = getCarried();

        if (canOmniblobQuickReplace(slot, carried)
                && slot.mayPlace(carried)
                && canDragTo(slot)) {
            quickcraftSlots.add(slot);
        }
    }

    /**
     * Volume-aware replacement for canItemQuickReplace. Accepts empty slots
     * and occupied slots with matching goo type for merge distribution.
     *
     * @param slot    the target slot
     * @param carried the carried item stack
     * @return true if the slot accepts the omniblob
     */
    private boolean canOmniblobQuickReplace(Slot slot, ItemStack carried) {
        ItemStack existing = slot.getItem();
        if (existing.isEmpty()) { return true; }
        GooType carriedType = BlobStacks.gooTypeOf(carried);
        GooType existingType = BlobStacks.gooTypeOf(existing);
        return carriedType != null && carriedType == existingType;
    }

    /**
     * Phase 2: distribute volume across collected slots, then reset.
     * Left-click: divide total volume evenly. Right-click: 1 blob per slot.
     *
     * @param player the interacting player
     */
    private void handlePhaseDistribute(Player player) {
        if (quickcraftSlots.isEmpty()) {
            resetQuickCraft();
            return;
        }

        ItemStack carried = getCarried();
        GooType gooType = BlobStacks.gooTypeOf(carried);
        int totalVolume = BlobStacks.volumeOf(carried);

        int perSlot = computePerSlotVolume(totalVolume, quickcraftSlots.size());
        int distributed = distributeToSlots(gooType, perSlot, totalVolume);
        setCarriedRemainder(gooType, totalVolume - distributed);
        broadcastChanges();
        resetQuickCraft();
    }

    /**
     * Computes the per-slot volume based on drag type.
     * Charitable (left-click): even division. Greedy (right-click): 1 blob each.
     *
     * @param totalVolume the total volume available
     * @param slotCount   the number of collected slots
     * @return the volume to place per slot
     */
    private int computePerSlotVolume(int totalVolume, int slotCount) {
        if (quickcraftType == AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY) {
            return OmniblobQuickCraft.greedyPerSlot();
        }
        return OmniblobQuickCraft.charitablePerSlot(totalVolume, slotCount);
    }

    /**
     * Places items into each collected slot, merging with existing same-type
     * contents. Skips slots with incompatible items. Returns total volume distributed.
     *
     * @param gooType     the goo type being distributed
     * @param perSlot     the volume to place per slot
     * @param totalVolume the total available volume
     * @return total volume actually distributed
     */
    private int distributeToSlots(GooType gooType, int perSlot, int totalVolume) {
        int distributed = 0;
        for (Slot slot : quickcraftSlots) {
            if (!canDistributeMore(perSlot, distributed, totalVolume)) { break; }
            if (!isSlotEligible(slot)) { continue; }
            int placed = placeIntoSlot(slot, gooType, perSlot);
            if (placed > 0) { distributed += placed; }
        }
        return distributed;
    }

    /**
     * Returns true if there is enough remaining volume to distribute another slot.
     *
     * @param perSlot     the volume per slot in microblobs
     * @param distributed the total volume already distributed
     * @param totalVolume the total volume available
     * @return true if another slot can receive its share
     */
    private static boolean canDistributeMore(int perSlot, int distributed, int totalVolume) {
        return perSlot > 0 && distributed + perSlot <= totalVolume;
    }

    /**
     * Returns true if the slot accepts placement and is a valid drag target.
     *
     * @param slot the inventory slot to check
     * @return true if the slot can receive goo during quick-craft
     */
    private boolean isSlotEligible(Slot slot) {
        return slot.mayPlace(getCarried()) && canDragTo(slot);
    }

    /**
     * Merges goo into a single slot, returning volume placed or INCOMPATIBLE_SLOT if incompatible.
     * @param slot the target inventory slot
     * @param gooType the goo type being distributed
     * @param perSlot the volume in microblobs to place in this slot
     * @return the volume actually placed, or INCOMPATIBLE_SLOT if the slot has an incompatible item
     */
    private int placeIntoSlot(Slot slot, GooType gooType, int perSlot) {
        ItemStack existing = slot.getItem();
        int mergedVolume = perSlot;
        if (!existing.isEmpty()) {
            GooType existingType = BlobStacks.gooTypeOf(existing);
            if (existingType != gooType) { return INCOMPATIBLE_SLOT; }
            mergedVolume += BlobStacks.volumeOf(existing);
        }
        slot.setByPlayer(BlobStacks.createForOutput(gooType, mergedVolume));
        return perSlot;
    }

    /**
     * Sets the carried item to the remainder after distribution.
     * Empty if no remainder, otherwise an output-rule item.
     *
     * @param gooType   the goo type being distributed
     * @param remainder the remaining volume after distribution
     */
    private void setCarriedRemainder(GooType gooType, int remainder) {
        if (remainder <= 0) {
            setCarried(ItemStack.EMPTY);
        } else {
            setCarried(BlobStacks.createForOutput(gooType, remainder));
        }
    }
}
