package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.world.item.ItemStack;

/**
 * Common interface for block entities that hold canister slots with metadata
 * and support goo insert/extract operations. Both {@link CanisterBlockEntity}
 * and {@link HubBlockEntity} store canister item stacks in numbered slots.
 * This interface lets callers operate on either type without type-branching.
 *
 * <p>Provides default implementations for goo read/write operations that
 * delegate to {@link #getCanister(int)} and {@link #onSlotChanged()}.
 */
public interface ISlottedGooContainer {

    /**
     * Returns the canister stack in the given slot without removing it.
     * Returns {@link ItemStack#EMPTY} if the slot is out of range or empty.
     */
    ItemStack getCanister(int slot);

    /**
     * Called after a slot's contents change. Implementors should mark the
     * block entity dirty and trigger a sync packet.
     */
    void onSlotChanged();

    /**
     * Returns the goo contents of the canister in the given slot, or
     * {@link GooContents#EMPTY} if the slot is empty.
     */
    default GooContents getSlotGooContents(int slot) {
        ItemStack stack = getCanister(slot);
        return stack.isEmpty() ? GooContents.EMPTY : CanisterItem.getGooContents(stack);
    }

    /**
     * Returns the metadata for the canister in the given slot, or
     * {@link CanisterMetadata#EMPTY} if the slot is empty.
     */
    default CanisterMetadata getSlotMetadata(int slot) {
        ItemStack stack = getCanister(slot);
        return stack.isEmpty() ? CanisterMetadata.EMPTY : CanisterItem.getMetadata(stack);
    }

    /**
     * Sets the metadata on the canister in the given slot.
     * No-op if the slot is empty.
     */
    default void setSlotMetadata(int slot, CanisterMetadata metadata) {
        ItemStack stack = getCanister(slot);
        if (stack.isEmpty()) return;
        CanisterItem.setMetadata(stack, metadata);
        onSlotChanged();
    }

    /**
     * Returns true if the given slot can accept more goo (has remaining capacity).
     *
     * @param slot the slot index to check
     * @return true if the slot has a canister with space remaining
     */
    default boolean canAccept(int slot) {
        ItemStack canister = getCanister(slot);
        if (canister.isEmpty()) return false;
        GooContents contents = getSlotGooContents(slot);
        int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(canister);
        long capacity = ContainerCapacity.canisterCapacity(compression);
        return contents.totalVolume() < capacity;
    }

    /**
     * Inserts goo into the canister at the given slot, capping at capacity.
     *
     * @param slot         the slot index to insert into
     * @param incomingType the goo type to insert
     * @param volume       volume in microblobs to insert
     * @return the amount actually inserted
     */
    default long insertGoo(int slot, GooType incomingType, long volume) {
        ItemStack stack = getCanister(slot);
        if (stack.isEmpty()) return 0L;
        long accepted = CanisterItem.addGoo(stack, incomingType, volume);
        if (accepted > 0) onSlotChanged();
        return accepted;
    }

    /**
     * Extracts goo of a specific type from the canister at the given slot.
     *
     * @param slot      the slot index to extract from
     * @param type      the goo type to extract
     * @param requested volume in microblobs to extract
     * @return the amount actually extracted
     */
    default long extractGoo(int slot, GooType type, long requested) {
        ItemStack stack = getCanister(slot);
        if (stack.isEmpty()) return 0L;
        long extracted = CanisterItem.removeGoo(stack, type, requested);
        if (extracted > 0) onSlotChanged();
        return extracted;
    }
}
