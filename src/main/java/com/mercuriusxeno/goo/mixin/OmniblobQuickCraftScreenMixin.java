package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.OmniblobQuickCraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

/**
 * Client-side mixin for omniblob quickcraft rendering and slot collection.
 * Vanilla's client-side quickcraft uses count-based gates that block omniblobs
 * (which have count=1 but arbitrary volume). This mixin bypasses those gates
 * and fixes the cursor remainder preview during drag.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class OmniblobQuickCraftScreenMixin {

    @Shadow
    @Final
    protected Set<Slot> quickCraftSlots;

    @Shadow
    private int quickCraftingType;

    @Shadow
    private int quickCraftingRemainder;

    @Shadow
    protected boolean isQuickCrafting;

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Shadow
    protected abstract void renderSlotContents(GuiGraphicsExtractor guiGraphics,
            ItemStack stack, Slot slot, String countString);

    /**
     * Redirects the getCount() call in mouseDragged's quickcraft condition.
     * For omniblobs, returns Integer.MAX_VALUE so the slot-collection condition
     * always passes (actual limits enforced server-side).
     * For non-omniblobs, returns the real count (vanilla behavior).
     */
    @Redirect(
        method = "mouseDragged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getCount()I", ordinal = 1)
    )
    private int goo$omniblobBypassCountGate(ItemStack stack) {
        if (OmniblobQuickCraft.isOmniblobQuickCraft(stack)) {
            return Integer.MAX_VALUE;
        }
        return stack.getCount();
    }

    /**
     * Shared logic for canItemQuickReplace overrides. Returns true for empty
     * slots and occupied slots with matching goo type, enabling merge collection.
     */
    private boolean omniblobCanQuickReplace(Slot slot, ItemStack carried, boolean stackSizeMatters) {
        if (OmniblobQuickCraft.isOmniblobQuickCraft(carried)) {
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) return true;
            GooType carriedType = BlobStacks.gooTypeOf(carried);
            GooType existingType = BlobStacks.gooTypeOf(existing);
            return carriedType != null && carriedType == existingType;
        }
        return AbstractContainerMenu.canItemQuickReplace(slot, carried, stackSizeMatters);
    }

    /**
     * Redirects canItemQuickReplace in mouseDragged so that occupied same-type
     * goo slots are collected into quickCraftSlots during drag.
     * Without this, vanilla rejects them (maxStackSize=1, existing count=1).
     */
    @Redirect(
        method = "mouseDragged",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;canItemQuickReplace(Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/item/ItemStack;Z)Z")
    )
    private boolean goo$allowSameTypeCollect(Slot slot, ItemStack carried, boolean stackSizeMatters) {
        return omniblobCanQuickReplace(slot, carried, stackSizeMatters);
    }

    /**
     * Redirects canItemQuickReplace in renderSlot so that same-type goo items
     * in occupied slots are not ejected from quickCraftSlots during render.
     * Vanilla ejects slots where this returns false, preventing merge previews.
     */
    @Redirect(
        method = "renderSlot",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;canItemQuickReplace(Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/item/ItemStack;Z)Z")
    )
    private boolean goo$allowSameTypeQuickReplace(Slot slot, ItemStack carried, boolean stackSizeMatters) {
        return omniblobCanQuickReplace(slot, carried, stackSizeMatters);
    }

    /**
     * Returns a dummy count for omniblob quickcraft so vanilla's copyWithCount
     * doesn't produce an empty stack. The real preview is built in
     * goo$fixOmniblobPreview via renderSlotContents.
     */
    @Redirect(
        method = "renderSlot",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getQuickCraftPlaceCount(IILnet/minecraft/world/item/ItemStack;)I")
    )
    private int goo$omniblobPlaceCount(int slotCount, int craftType, ItemStack carried) {
        if (OmniblobQuickCraft.isOmniblobQuickCraft(carried)) {
            return 1;
        }
        return AbstractContainerMenu.getQuickCraftPlaceCount(slotCount, craftType, carried);
    }

    /**
     * Redirects renderSlotContents to replace the preview ItemStack with a
     * volume-correct one during omniblob quickcraft, and suppresses the yellow
     * count overlay. Vanilla's preview uses copyWithCount which preserves the
     * original volume component (wrong) and clamps to maxStackSize=1 (yellow "1").
     */
    @Redirect(
        method = "renderSlot",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/inventory/Slot;Ljava/lang/String;)V")
    )
    private void goo$fixOmniblobPreview(AbstractContainerScreen<?> self,
            GuiGraphicsExtractor graphics, ItemStack previewStack, Slot slot, String countString) {
        ItemStack carried = menu.getCarried();
        if (isQuickCrafting && quickCraftSlots.contains(slot)
                && OmniblobQuickCraft.isOmniblobQuickCraft(carried)) {
            GooType type = BlobStacks.gooTypeOf(carried);
            long totalVolume = BlobStacks.volumeOf(carried);
            long perSlot = computeClientPerSlot(totalVolume);
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty() && type == BlobStacks.gooTypeOf(existing)) {
                perSlot += BlobStacks.volumeOf(existing);
            }
            previewStack = BlobStacks.createForOutput(type, perSlot);
            countString = null;
        }
        renderSlotContents(graphics, previewStack, slot, countString);
    }

    /**
     * Overrides recalculateQuickCraftRemaining for omniblobs. Vanilla computes
     * remainder using count/maxStackSize which is meaningless for volume-based items.
     * Keeps the cursor visible during drag by setting remainder to the carried count
     * when volume remains, or 0 when fully distributed.
     */
    @Inject(method = "recalculateQuickCraftRemaining", at = @At("HEAD"), cancellable = true)
    private void goo$omniblobRecalcRemainder(CallbackInfo ci) {
        ItemStack carried = menu.getCarried();
        if (!isQuickCrafting || !OmniblobQuickCraft.isOmniblobQuickCraft(carried)) return;

        long totalVolume = BlobStacks.volumeOf(carried);
        long perSlot = computeClientPerSlot(totalVolume);
        long totalDistributed = perSlot * quickCraftSlots.size();
        long remainder = totalVolume - Math.min(totalDistributed, totalVolume);

        quickCraftingRemainder = remainder > 0 ? carried.getCount() : 0;
        ci.cancel();
    }

    /**
     * Computes per-slot volume on the client side for remainder preview.
     * Mirrors the server-side logic: charitable divides evenly, greedy gives 1 blob.
     */
    private long computeClientPerSlot(long totalVolume) {
        if (quickCraftingType == AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY) {
            return OmniblobQuickCraft.greedyPerSlot();
        }
        int slotCount = quickCraftSlots.size();
        if (slotCount <= 0) return 0L;
        return OmniblobQuickCraft.charitablePerSlot(totalVolume, slotCount);
    }
}
