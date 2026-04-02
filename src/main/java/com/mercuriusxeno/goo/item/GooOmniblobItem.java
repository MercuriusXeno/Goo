package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * Omniblob: a single-type, uncapped-capacity goo container for volumes that
 * do not fit in a regular blob stack (sub-blob remainders or amounts exceeding
 * 64,000 mB). One registration per goo type (15 total).
 *
 * <p>Inventory cursor interactions allow inserting and extracting blobs
 * via click mechanics.</p>
 */
public class GooOmniblobItem extends Item implements IGooItemInteraction {

    private final GooType gooType;

    /**
     * Creates a new omniblob item for the given goo type.
     *
     * @param gooType the goo type this omniblob carries
     * @param properties item properties (should include stacksTo(1))
     */
    public GooOmniblobItem(GooType gooType, Properties properties) {
        super(properties);
        this.gooType = gooType;
    }

    /** Returns the goo type this omniblob carries. */
    public GooType getGooType() {
        return gooType;
    }

    /**
     * Returns the volume stored in the given omniblob stack, in microblobs.
     *
     * @param stack the omniblob item stack
     * @return volume in microblobs, or 0 if unset
     */
    public static long getVolume(ItemStack stack) {
        Long vol = stack.get(GooDataComponents.BLOB_VOLUME.get());
        return vol != null ? vol : 0L;
    }

    /**
     * Sets the volume on the given omniblob stack.
     *
     * @param stack  the omniblob item stack
     * @param volume volume in microblobs
     */
    public static void setVolume(ItemStack stack, long volume) {
        stack.set(GooDataComponents.BLOB_VOLUME.get(), volume);
    }

    /**
     * Creates an omniblob ItemStack with the given goo type and volume.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return a new omniblob item stack
     */
    public static ItemStack createWithVolume(GooType type, long volume) {
        ItemStack stack = new ItemStack(GooItems.OMNIBLOBS.get(type).get());
        setVolume(stack, volume);
        return stack;
    }

    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        long volume = getVolume(stack);
        String tierName = BlobTiers.computeTierName(volume);
        String typeName = gooType.getId().substring(0, 1).toUpperCase()
            + gooType.getId().substring(1);
        return Component.literal(typeName + " " + tierName);
    }

    // -- Cursor interactions --

    /**
     * Omniblob in cursor, clicking onto a slot target.
     * Right-click on empty slot: place ONE blob (1,000 mB).
     * Left-click on same-type blob: absorb entire blob stack into omniblob.
     * Right-click on same-type blob: absorb 1 blob into omniblob.
     */
    @Override
    public boolean overrideStackedOnOther(@NonNull ItemStack omniblob, @NonNull Slot slot,
            @NonNull ClickAction action, @NonNull Player player) {
        ItemStack target = slot.getItem();

        if (target.getItem() instanceof GooBlobItem blobItem && blobItem.getGooType() == gooType) {
            return handleAbsorbFromSlot(omniblob, target, slot, action, player);
        }

        if (action != ClickAction.SECONDARY) return false;
        if (!target.isEmpty()) return false;

        long volume = getVolume(omniblob);
        if (volume < BlobStacks.MB_PER_BLOB) return false;

        long placed = BlobStacks.MB_PER_BLOB;
        long remaining = volume - placed;

        slot.set(BlobStacks.createBlobStack(gooType, 1));

        if (remaining <= 0) {
            omniblob.shrink(1);
        } else if (BlobStacks.isCleanBlobStack(remaining)) {
            player.containerMenu.setCarried(BlobStacks.createBlobStack(gooType, (int) (remaining / BlobStacks.MB_PER_BLOB)));
        } else {
            setVolume(omniblob, remaining);
        }
        return true;
    }

    /**
     * Omniblob cursor onto same-type blob stack in slot.
     * Left-click: merge everything into one omniblob in the slot, cursor clears.
     * Right-click: place 1 blob from omniblob into the stack (grow stack by 1).
     */
    private boolean handleAbsorbFromSlot(ItemStack omniblob, ItemStack target, Slot slot,
            ClickAction action, Player player) {
        if (action == ClickAction.PRIMARY) {
            long omniVol = getVolume(omniblob);
            long targetVol = (long) target.getCount() * BlobStacks.MB_PER_BLOB;
            slot.set(BlobStacks.createForOutput(gooType, omniVol + targetVol));
            player.containerMenu.setCarried(ItemStack.EMPTY);
        } else {
            long volume = getVolume(omniblob);
            if (volume < BlobStacks.MB_PER_BLOB) return false;

            target.grow(1);
            long remaining = volume - BlobStacks.MB_PER_BLOB;
            if (remaining <= 0) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
            } else if (BlobStacks.isCleanBlobStack(remaining)) {
                player.containerMenu.setCarried(BlobStacks.createBlobStack(gooType, (int) (remaining / BlobStacks.MB_PER_BLOB)));
            } else {
                setVolume(omniblob, remaining);
            }
        }
        return true;
    }

    /**
     * Something clicking onto omniblob in a slot.
     * Left-click + blob stack: absorb entire stack.
     * Right-click + blob stack: absorb 1 (shift: all).
     * Left/right-click + same-type omniblob: combine into slot omniblob.
     * Right-click + empty cursor: split volume in half.
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack omniblob, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (cursor.isEmpty() && action == ClickAction.SECONDARY) {
            return handleEmptyCursorExtract(omniblob, slot, cursorAccess);
        }

        if (cursor.getItem() instanceof GooOmniblobItem cursorOmni
                && cursorOmni.getGooType() == gooType) {
            return handleOmniblobCombine(omniblob, cursor, cursorAccess);
        }

        if (cursor.getItem() instanceof GooBlobItem blobItem && blobItem.getGooType() == gooType) {
            return handleBlobAbsorb(omniblob, cursor, action, cursorAccess, player);
        }

        return false;
    }

    /**
     * Splits the omniblob in half. One half goes to the cursor, the other stays
     * in the slot. Each half follows the output rule (blob stack if clean, omniblob otherwise).
     * Sub-blob remainder case (volume < 1000) gives the whole omniblob to the cursor.
     */
    private boolean handleEmptyCursorExtract(ItemStack omniblob, Slot slot, SlotAccess cursorAccess) {
        long volume = getVolume(omniblob);
        if (volume <= 0) return false;

        long wholeBlobs = BlobStacks.wholeBlobs(volume);
        if (wholeBlobs <= 0) {
            cursorAccess.set(omniblob.copy());
            omniblob.shrink(1);
            return true;
        }

        long half = volume / 2;
        long other = volume - half;

        cursorAccess.set(BlobStacks.createForOutput(gooType, half));

        if (other <= 0) {
            omniblob.shrink(1);
        } else if (BlobStacks.isCleanBlobStack(other)) {
            slot.set(BlobStacks.createBlobStack(gooType, (int) (other / BlobStacks.MB_PER_BLOB)));
        } else {
            setVolume(omniblob, other);
        }
        return true;
    }

    /**
     * Combines a cursor omniblob of the same type into the slot omniblob.
     * The cursor omniblob's volume is added to the slot omniblob, and the cursor is cleared.
     */
    private boolean handleOmniblobCombine(ItemStack slotOmniblob, ItemStack cursorOmniblob,
            SlotAccess cursorAccess) {
        long cursorVol = getVolume(cursorOmniblob);
        long slotVol = getVolume(slotOmniblob);
        setVolume(slotOmniblob, slotVol + cursorVol);
        cursorAccess.set(ItemStack.EMPTY);
        return true;
    }

    /** Absorbs blob stack into the omniblob. */
    private boolean handleBlobAbsorb(ItemStack omniblob, ItemStack cursor,
            ClickAction action, SlotAccess cursorAccess, Player player) {
        int absorbCount;
        if (action == ClickAction.PRIMARY) {
            absorbCount = cursor.getCount();
        } else {
            absorbCount = player.isShiftKeyDown() ? cursor.getCount() : 1;
        }

        long absorbVolume = (long) absorbCount * BlobStacks.MB_PER_BLOB;
        long currentVolume = getVolume(omniblob);
        setVolume(omniblob, currentVolume + absorbVolume);

        cursor.shrink(absorbCount);
        if (cursor.isEmpty()) {
            cursorAccess.set(ItemStack.EMPTY);
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.BLOB_INSERT;
    }
}
