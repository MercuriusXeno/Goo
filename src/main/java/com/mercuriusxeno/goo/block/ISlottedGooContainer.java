package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.world.item.ItemStack;

/**
 * Common interface for block entities that hold canister slots with metadata
 * and support goo insert/extract operations. Both {@link CanisterBlockEntity}
 * and {@link HubBlockEntity} store canister item stacks in numbered slots.
 * This interface lets callers operate on either type without type-branching.
 *
 * <p>All slot operations delegate to the {@link SlottedContainerState} returned
 * by {@link #containerState()}. Defaults handle goo read/write, metadata,
 * and capacity checks; implementors only need to supply the component.</p>
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface") // not a lambda target; sole abstract is a composed-state accessor
public interface ISlottedGooContainer {

    /**
     * Returns the behavioral component that owns canister stacks, handlers,
     * and stream state for this container.
     *
     * @return the slotted container state
     */
    SlottedContainerState containerState();

    /**
     * Returns the canister stack in the given slot without removing it.
     * Returns {@link ItemStack#EMPTY} if the slot is out of range or empty.
     *
     * @param slot the slot index
     * @return the canister
     */
    default ItemStack getCanister(int slot) {
        return containerState().getCanister(slot);
    }

    /**
     * Called after a slot's contents change. Delegates to the component's
     * sync callback, which marks the block entity dirty and triggers a
     * client sync packet.
     */
    default void onSlotChanged() {
        containerState().onSlotChanged();
    }

    /**
     * Returns the goo contents of the canister in the given slot, or
     * {@link GooContents#EMPTY} if the slot is empty.
     *
     * @param slot the slot index
     * @return the slot goo contents
     */
    default GooContents getSlotGooContents(int slot) {
        return containerState().getSlotGooContents(slot);
    }

    /**
     * Returns the metadata for the canister in the given slot, or
     * {@link CanisterMetadata#EMPTY} if the slot is empty.
     *
     * @param slot the slot index
     * @return the slot metadata
     */
    default CanisterMetadata getSlotMetadata(int slot) {
        ItemStack stack = getCanister(slot);
        return stack.isEmpty() ? CanisterMetadata.EMPTY : CanisterItem.getMetadata(stack);
    }

    /**
     * Sets the metadata on the canister in the given slot.
     * No-op if the slot is empty.
     *
     * @param slot     the slot index
     * @param metadata the canister metadata to apply
     */
    default void setSlotMetadata(int slot, CanisterMetadata metadata) {
        ItemStack stack = getCanister(slot);
        if (stack.isEmpty()) { return; }
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
        return containerState().canAccept(slot);
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
        return containerState().insertGoo(slot, incomingType, volume);
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
        return containerState().extractGoo(slot, type, requested);
    }
}
