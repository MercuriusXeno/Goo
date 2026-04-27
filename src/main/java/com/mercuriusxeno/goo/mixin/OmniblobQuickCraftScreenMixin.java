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

    /**
     * Mixin method target: shouldAddSlotToQuickCraft. In 26.1 the quickcraft count/replace
     * gate lives here, not inlined in mouseDragged as in older versions.
     */
    private static final String METHOD_SHOULD_ADD_SLOT = "shouldAddSlotToQuickCraft";
    /**
     * Mixin method target: extractSlot. In 26.1 the per-slot render pass was
     * renamed from renderSlot to extractSlot (render state extraction).
     */
    private static final String METHOD_EXTRACT_SLOT = "extractSlot";
    /**
     * Mixin injection point type: invoke.
     */
    private static final String AT_INVOKE = "INVOKE";
    /**
     * Mixin target: ItemStack.getCount().
     */
    private static final String TARGET_GET_COUNT =
            "Lnet/minecraft/world/item/ItemStack;getCount()I";
    /**
     * Mixin target: AbstractContainerMenu.canItemQuickReplace().
     */
    private static final String TARGET_CAN_QUICK_REPLACE =
            "Lnet/minecraft/world/inventory/AbstractContainerMenu;"
                    + "canItemQuickReplace(Lnet/minecraft/world/inventory/Slot;"
                    + "Lnet/minecraft/world/item/ItemStack;Z)Z";
    /**
     * Mixin target: AbstractContainerMenu.getQuickCraftPlaceCount().
     */
    private static final String TARGET_GET_PLACE_COUNT =
            "Lnet/minecraft/world/inventory/AbstractContainerMenu;"
                    + "getQuickCraftPlaceCount(IILnet/minecraft/world/item/ItemStack;)I";
    /**
     * Mixin target: AbstractContainerScreen.renderSlotContents().
     */
    private static final String TARGET_RENDER_SLOT_CONTENTS =
            "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;"
                    + "renderSlotContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/inventory/Slot;Ljava/lang/String;)V";
    /**
     * Mixin method target: recalculateQuickCraftRemaining.
     */
    private static final String METHOD_RECALC = "recalculateQuickCraftRemaining";
    /**
     * Mixin injection point type: HEAD.
     */
    private static final String AT_HEAD = "HEAD";

    @Shadow
    @Final
    protected Set<Slot> quickCraftSlots;
    @Shadow
    protected boolean isQuickCrafting;
    @Shadow
    @Final
    protected AbstractContainerMenu menu;
    @Shadow
    private int quickCraftingType;
    @Shadow
    private int quickCraftingRemainder;

    @Shadow
    protected abstract void renderSlotContents(GuiGraphicsExtractor guiGraphics,
                                               ItemStack stack, Slot slot, String countString);

    /**
     * Redirects the getCount() call inside shouldAddSlotToQuickCraft's gate
     * (carried.getCount() > quickCraftSlots.size() || quickCraftingType == 2).
     * For omniblobs, returns Integer.MAX_VALUE so each dragged-over slot is
     * collected regardless of stack count (omniblobs always have count=1).
     * For non-omniblobs, returns the real count (vanilla behavior).
     *
     * @param stack the item stack being checked
     * @return the effective count for the gate check
     */
    @Redirect(
            method = METHOD_SHOULD_ADD_SLOT,
            at = @At(value = AT_INVOKE, target = TARGET_GET_COUNT)
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
     *
     * @param slot             the target slot
     * @param carried          the carried item stack
     * @param stackSizeMatters whether stack size affects the check
     * @return true if the slot accepts the item for quickcraft
     */
    private boolean omniblobCanQuickReplace(Slot slot, ItemStack carried, boolean stackSizeMatters) {
        if (OmniblobQuickCraft.isOmniblobQuickCraft(carried)) {
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
                return true;
            }
            GooType carriedType = BlobStacks.gooTypeOf(carried);
            GooType existingType = BlobStacks.gooTypeOf(existing);
            return carriedType != null && carriedType == existingType;
        }
        return AbstractContainerMenu.canItemQuickReplace(slot, carried, stackSizeMatters);
    }

    /**
     * Redirects canItemQuickReplace in shouldAddSlotToQuickCraft so that
     * occupied same-type goo slots are collected into quickCraftSlots during drag.
     * Without this, vanilla rejects them (maxStackSize=1, existing count=1).
     *
     * @param slot             the target slot
     * @param carried          the carried item stack
     * @param stackSizeMatters whether stack size affects the check
     * @return true if the slot should be collected
     */
    @Redirect(
            method = METHOD_SHOULD_ADD_SLOT,
            at = @At(value = AT_INVOKE,
                    target = TARGET_CAN_QUICK_REPLACE)
    )
    private boolean goo$allowSameTypeCollect(Slot slot, ItemStack carried, boolean stackSizeMatters) {
        return omniblobCanQuickReplace(slot, carried, stackSizeMatters);
    }

    /**
     * Redirects canItemQuickReplace in renderSlot so that same-type goo items
     * in occupied slots are not ejected from quickCraftSlots during render.
     * Vanilla ejects slots where this returns false, preventing merge previews.
     *
     * @param slot             the target slot
     * @param carried          the carried item stack
     * @param stackSizeMatters whether stack size affects the check
     * @return true if the slot should remain collected
     */
    @Redirect(
            method = METHOD_EXTRACT_SLOT,
            at = @At(value = AT_INVOKE,
                    target = TARGET_CAN_QUICK_REPLACE)
    )
    private boolean goo$allowSameTypeQuickReplace(Slot slot, ItemStack carried, boolean stackSizeMatters) {
        return omniblobCanQuickReplace(slot, carried, stackSizeMatters);
    }

    /**
     * Returns a dummy count for omniblob quickcraft so vanilla's copyWithCount
     * doesn't produce an empty stack. The real preview is built in
     * goo$fixOmniblobPreview via renderSlotContents.
     *
     * @param slotCount the number of slots in the quickcraft set
     * @param craftType the quickcraft drag type
     * @param carried   the carried item stack
     * @return the place count (1 for omniblobs, vanilla result otherwise)
     */
    @Redirect(
            method = METHOD_EXTRACT_SLOT,
            at = @At(value = AT_INVOKE,
                    target = TARGET_GET_PLACE_COUNT)
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
     *
     * @param self         the container screen instance
     * @param graphics     the GUI graphics extractor
     * @param previewStack the vanilla-computed preview stack
     * @param slot         the slot being rendered
     * @param countString  the vanilla count string overlay
     */
    @Redirect(
            method = METHOD_EXTRACT_SLOT,
            at = @At(value = AT_INVOKE,
                    target = TARGET_RENDER_SLOT_CONTENTS)
    )
    private void goo$fixOmniblobPreview(AbstractContainerScreen<?> self,
                                        GuiGraphicsExtractor graphics, ItemStack previewStack, Slot slot, String countString) {
        ItemStack carried = menu.getCarried();
        if (isQuickCrafting && quickCraftSlots.contains(slot)
                && OmniblobQuickCraft.isOmniblobQuickCraft(carried)) {
            renderOmniblobSlotPreview(graphics, slot, carried);
            return;
        }
        renderSlotContents(graphics, previewStack, slot, countString);
    }

    /**
     * Builds and renders a volume-correct omniblob preview for one quickcraft slot.
     *
     * @param graphics the GUI graphics context for rendering
     * @param slot     the slot being previewed during quickcraft drag
     * @param carried  the omniblob item stack on the cursor
     */
    private void renderOmniblobSlotPreview(GuiGraphicsExtractor graphics, Slot slot, ItemStack carried) {
        GooType type = BlobStacks.gooTypeOf(carried);
        int totalVolume = BlobStacks.volumeOf(carried);
        int perSlot = computeClientPerSlot(totalVolume);
        ItemStack existing = slot.getItem();
        if (!existing.isEmpty() && type == BlobStacks.gooTypeOf(existing)) {
            perSlot += BlobStacks.volumeOf(existing);
        }
        ItemStack omniblobPreview = BlobStacks.createForOutput(type, perSlot);
        renderSlotContents(graphics, omniblobPreview, slot, null);
    }

    /**
     * Overrides recalculateQuickCraftRemaining for omniblobs. Vanilla computes
     * remainder using count/maxStackSize which is meaningless for volume-based items.
     * Keeps the cursor visible during drag by setting remainder to the carried count
     * when volume remains, or 0 when fully distributed.
     *
     * @param ci the mixin callback info
     */
    @Inject(method = METHOD_RECALC, at = @At(AT_HEAD), cancellable = true)
    private void goo$omniblobRecalcRemainder(CallbackInfo ci) {
        ItemStack carried = menu.getCarried();
        if (!isQuickCrafting || !OmniblobQuickCraft.isOmniblobQuickCraft(carried)) {
            return;
        }

        int totalVolume = BlobStacks.volumeOf(carried);
        int perSlot = computeClientPerSlot(totalVolume);
        int totalDistributed = perSlot * quickCraftSlots.size();
        int remainder = totalVolume - Math.min(totalDistributed, totalVolume);

        quickCraftingRemainder = remainder > 0 ? carried.getCount() : 0;
        ci.cancel();
    }

    /**
     * Computes per-slot volume on the client side for remainder preview.
     * Mirrors the server-side logic: charitable divides evenly, greedy gives 1 blob.
     *
     * @param totalVolume the total volume being distributed
     * @return the volume per slot
     */
    private int computeClientPerSlot(int totalVolume) {
        if (quickCraftingType == AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY) {
            return OmniblobQuickCraft.greedyPerSlot();
        }
        int slotCount = quickCraftSlots.size();
        if (slotCount <= 0) {
            return 0;
        }
        return OmniblobQuickCraft.charitablePerSlot(totalVolume, slotCount);
    }
}
