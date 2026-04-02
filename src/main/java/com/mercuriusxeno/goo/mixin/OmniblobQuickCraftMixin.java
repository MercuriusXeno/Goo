package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
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
     */
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void goo$omniblobQuickCraft(int slotId, int button, ContainerInput clickType,
            Player player, CallbackInfo ci) {
        if (clickType != ContainerInput.QUICK_CRAFT) return;
        if (!OmniblobQuickCraft.isOmniblobQuickCraft(getCarried())) return;

        int header = AbstractContainerMenu.getQuickcraftHeader(button);
        handleOmniblobPhase(slotId, button, header, player);
        ci.cancel();
    }

    /**
     * Dispatches to the appropriate quickcraft phase for omniblob distribution.
     */
    private void handleOmniblobPhase(int slotId, int button, int header, Player player) {
        int previousStatus = quickcraftStatus;
        quickcraftStatus = header;

        if (!isValidTransition(previousStatus, header)) {
            resetQuickCraft();
            return;
        }

        switch (header) {
            case 0 -> handlePhaseStart(button, player);
            case 1 -> handlePhaseCollect(slotId);
            case 2 -> handlePhaseDistribute(player);
            default -> resetQuickCraft();
        }
    }

    /**
     * Returns true if the phase transition is valid. Mirrors vanilla validation:
     * previous must be 0 for start, or previous+1 == current for progression.
     */
    private boolean isValidTransition(int previousStatus, int newStatus) {
        if (getCarried().isEmpty()) return false;
        return (previousStatus == 0 && newStatus == 0)
            || (previousStatus == 1 && newStatus == 1)
            || (previousStatus == 1 && newStatus == 2)
            || (previousStatus == newStatus);
    }

    /**
     * Phase 0: start quickcraft. Records drag type and clears collected slots.
     */
    private void handlePhaseStart(int button, Player player) {
        quickcraftType = AbstractContainerMenu.getQuickcraftType(button);
        if (AbstractContainerMenu.isValidQuickcraftType(quickcraftType, player)) {
            quickcraftStatus = 1;
            quickcraftSlots.clear();
        } else {
            resetQuickCraft();
        }
    }

    /**
     * Phase 1: collect slots. Replaces canItemQuickReplace with a volume-aware
     * check that allows same-type goo merging into occupied slots.
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
     */
    private boolean canOmniblobQuickReplace(Slot slot, ItemStack carried) {
        ItemStack existing = slot.getItem();
        if (existing.isEmpty()) return true;
        GooType carriedType = BlobStacks.gooTypeOf(carried);
        GooType existingType = BlobStacks.gooTypeOf(existing);
        return carriedType != null && carriedType == existingType;
    }

    /**
     * Phase 2: distribute volume across collected slots, then reset.
     * Left-click: divide total volume evenly. Right-click: 1 blob per slot.
     */
    private void handlePhaseDistribute(Player player) {
        if (quickcraftSlots.isEmpty()) {
            resetQuickCraft();
            return;
        }

        ItemStack carried = getCarried();
        GooType gooType = BlobStacks.gooTypeOf(carried);
        long totalVolume = BlobStacks.volumeOf(carried);

        long perSlot = computePerSlotVolume(totalVolume, quickcraftSlots.size());
        long distributed = distributeToSlots(gooType, perSlot, totalVolume);
        setCarriedRemainder(gooType, totalVolume - distributed);
        broadcastChanges();
        resetQuickCraft();
    }

    /**
     * Computes the per-slot volume based on drag type.
     * Charitable (left-click): even division. Greedy (right-click): 1 blob each.
     */
    private long computePerSlotVolume(long totalVolume, int slotCount) {
        if (quickcraftType == AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY) {
            return OmniblobQuickCraft.greedyPerSlot();
        }
        return OmniblobQuickCraft.charitablePerSlot(totalVolume, slotCount);
    }

    /**
     * Places items into each collected slot, merging with existing same-type
     * contents. Skips slots with incompatible items. Returns total volume distributed.
     */
    private long distributeToSlots(GooType gooType, long perSlot, long totalVolume) {
        long distributed = 0L;

        for (Slot slot : quickcraftSlots) {
            if (perSlot <= 0 || distributed + perSlot > totalVolume) break;
            if (!slot.mayPlace(getCarried()) || !canDragTo(slot)) continue;

            ItemStack existing = slot.getItem();
            long mergedVolume = perSlot;

            if (!existing.isEmpty()) {
                GooType existingType = BlobStacks.gooTypeOf(existing);
                if (existingType == gooType) {
                    mergedVolume += BlobStacks.volumeOf(existing);
                } else {
                    continue;
                }
            }

            slot.setByPlayer(BlobStacks.createForOutput(gooType, mergedVolume));
            distributed += perSlot;
        }

        return distributed;
    }

    /**
     * Sets the carried item to the remainder after distribution.
     * Empty if no remainder, otherwise an output-rule item.
     */
    private void setCarriedRemainder(GooType gooType, long remainder) {
        if (remainder <= 0) {
            setCarried(ItemStack.EMPTY);
        } else {
            setCarried(BlobStacks.createForOutput(gooType, remainder));
        }
    }
}
