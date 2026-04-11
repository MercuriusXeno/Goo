package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift-click-side stacking fix: when the player shift-clicks a goo blob or
 * omniblob between regions (hotbar <-> main inventory, GUI slot -> inventory,
 * etc.), if a same-type omniblob already exists in the target region the whole
 * source is absorbed into it.
 *
 * <p>Root cause of the bug: omniblobs have {@code maxStackSize=1}, which makes
 * {@link AbstractContainerMenu#moveItemStackTo} skip its merge pass via
 * {@code ItemStack.isStackable()} and fall through to empty-slot placement.</p>
 *
 * <p>The mixin is strictly additive: it only handles the "merge into an existing
 * omniblob" case and leaves every other path (blob->blob merge, omniblob into an
 * empty slot) to vanilla. In particular, an omniblob with no omniblob sink in
 * the target region is left intact to land in an empty slot - it is never split
 * across partial blob stacks.</p>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerShiftClickMergeMixin {

    @Shadow
    @Final
    public NonNullList<Slot> slots;

    /**
     * Intercepts {@link AbstractContainerMenu#moveItemStackTo} at HEAD. If the
     * source is a goo item and a matching-type omniblob lives in the target slot
     * range, the source is absorbed whole into that omniblob and the call returns
     * true. Otherwise control falls through to vanilla.
     *
     * @param stack            the source stack being moved; mutated to empty on a successful absorb
     * @param startIndex       inclusive start of target slot range
     * @param endIndex         exclusive end of target slot range
     * @param reverseDirection iterate slots back-to-front when true (vanilla semantics)
     * @param cir              mixin callback info; set to true when the absorb handled the move
     */
    @Inject(method = "moveItemStackTo",
            at = @At("HEAD"), cancellable = true)
    private void goo$absorbIntoOmniblobInRange(ItemStack stack, int startIndex, int endIndex,
            boolean reverseDirection, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) { return; }
        GooType sourceType = BlobStacks.gooTypeOf(stack);
        if (sourceType == null) { return; }

        Slot sink = findOmniblobSlotInRange(sourceType, startIndex, endIndex, reverseDirection);
        if (sink == null) { return; }

        BlobStacks.absorbIntoOmniblobSlot(stack, sink.getItem());
        sink.setChanged();
        cir.setReturnValue(true);
    }

    /**
     * Scans the slot range for the first same-type omniblob, honoring the
     * {@code reverseDirection} iteration order so shift-click priorities match
     * vanilla's merge pass.
     *
     * @param sourceType       the goo type the source carries
     * @param startIndex       inclusive start of the slot range
     * @param endIndex         exclusive end of the slot range
     * @param reverseDirection iterate back-to-front when true
     * @return the first matching slot, or {@code null} if none in range
     */
    private Slot findOmniblobSlotInRange(GooType sourceType, int startIndex, int endIndex,
            boolean reverseDirection) {
        if (reverseDirection) {
            return scanOmniblobSlotsReverse(sourceType, startIndex, endIndex);
        }
        return scanOmniblobSlotsForward(sourceType, startIndex, endIndex);
    }

    /**
     * Forward-iterating scan for a same-type omniblob slot.
     *
     * @param sourceType the goo type the source carries
     * @param startIndex inclusive start of the slot range
     * @param endIndex   exclusive end of the slot range
     * @return the first matching slot, or {@code null} if none in range
     */
    private Slot scanOmniblobSlotsForward(GooType sourceType, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            Slot slot = slots.get(i);
            if (isOmniblobOfType(slot, sourceType)) { return slot; }
        }
        return null;
    }

    /**
     * Reverse-iterating scan for a same-type omniblob slot.
     *
     * @param sourceType the goo type the source carries
     * @param startIndex inclusive start of the slot range
     * @param endIndex   exclusive end of the slot range (iteration begins at endIndex - 1)
     * @return the first matching slot, or {@code null} if none in range
     */
    private Slot scanOmniblobSlotsReverse(GooType sourceType, int startIndex, int endIndex) {
        for (int i = endIndex - 1; i >= startIndex; i--) {
            Slot slot = slots.get(i);
            if (isOmniblobOfType(slot, sourceType)) { return slot; }
        }
        return null;
    }

    /**
     * True if the slot holds a same-type omniblob.
     *
     * @param slot       the slot to test
     * @param sourceType the goo type to match
     * @return true on an omniblob item of the matching type
     */
    private static boolean isOmniblobOfType(Slot slot, GooType sourceType) {
        ItemStack candidate = slot.getItem();
        return candidate.getItem() instanceof GooOmniblobItem omni
            && omni.getGooType() == sourceType;
    }
}
