package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.fluid.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

/**
 * Common interface for block entities that hold canister slots with metadata
 * and support fluid insert/extract operations. Both {@link CanisterBlockEntity}
 * and {@link HubBlockEntity} store canister item stacks in numbered slots.
 * This interface lets callers operate on either type without type-branching.
 *
 * <p>All slot operations delegate to the {@link SlottedCanisterState} returned
 * by {@link #containerState()}. Defaults handle fluid read/write, metadata,
 * and capacity checks; implementors only need to supply the component.</p>
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface") // not a lambda target; sole abstract is a composed-state accessor
public interface ICanisterHolder {

    /**
     * Returns the behavioral component that owns canister stacks, handlers,
     * and stream state for this container.
     *
     * @return the slotted container state
     */
    SlottedCanisterState containerState();

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
     * Returns the fluid content of the canister in the given slot, or
     * {@link CanisterFluidContent#EMPTY} if the slot is empty.
     *
     * @param slot the slot index
     * @return the slot fluid content
     */
    default CanisterFluidContent getSlotFluidContent(int slot) {
        return containerState().getSlotFluidContent(slot);
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
     * Returns true if the given slot can accept more fluid (has remaining capacity).
     *
     * @param slot the slot index to check
     * @return true if the slot has a canister with space remaining
     */
    default boolean canAccept(int slot) {
        return containerState().canAccept(slot);
    }

    /**
     * Inserts fluid into the canister at the given slot, capping at capacity.
     *
     * @param slot   the slot index to insert into
     * @param fluid  the fluid to insert
     * @param volume volume in microblobs to insert
     * @return the amount actually inserted
     */
    default int insertFluid(int slot, Fluid fluid, int volume) {
        return containerState().insertFluid(slot, fluid, volume);
    }

    /**
     * Convenience: insert goo by type.
     *
     * @param slot         the slot index
     * @param incomingType the goo type to insert
     * @param volume       volume in microblobs
     * @return the amount actually inserted
     */
    default int insertGoo(int slot, GooType incomingType, int volume) {
        return insertFluid(slot, GooFluids.SOURCES.get(incomingType).get(), volume);
    }

    /**
     * Extracts fluid from the canister at the given slot.
     *
     * @param slot      the slot index to extract from
     * @param fluid     the fluid to extract
     * @param requested volume in microblobs to extract
     * @return the amount actually extracted
     */
    default int extractFluid(int slot, Fluid fluid, int requested) {
        return containerState().extractFluid(slot, fluid, requested);
    }

    /**
     * Convenience: extract goo by type.
     *
     * @param slot      the slot index
     * @param type      the goo type to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    default int extractGoo(int slot, GooType type, int requested) {
        return extractFluid(slot, GooFluids.SOURCES.get(type).get(), requested);
    }

    // --- Shared slot lifecycle utilities ---

    /**
     * Creates a live fluid handler for the given slot, loaded from the canister
     * ItemStack's fluid content. The handler's onChange callback syncs back to the
     * item stack via {@link #syncSlotToItemStack}.
     *
     * @param be   the block entity that holds canister slots
     * @param slot the slot index
     * @param <T>  block entity type implementing ICanisterHolder
     * @return a new fluid handler for the slot
     */
    static <T extends BlockEntity & ICanisterHolder> CanisterSlotFluidHandler createSlotHandler(T be, int slot) {
        ItemStack stack = be.containerState().canisters.get(slot);
        int capacity = (int) ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(stack));
        CanisterSlotFluidHandler handler = new CanisterSlotFluidHandler(capacity,
            () -> syncSlotToItemStack(be, slot),
            () -> be.getLevel() != null ? be.getLevel().getGameTime() : 0);
        handler.loadFrom(CanisterItem.getFluidContent(stack));
        return handler;
    }

    /**
     * Writes the slot handler's current state back to the canister ItemStack,
     * snapshots the stream visualization state, and triggers a client sync.
     *
     * @param be   the block entity that holds canister slots
     * @param slot the slot index
     * @param <T>  block entity type implementing ICanisterHolder
     */
    static <T extends BlockEntity & ICanisterHolder> void syncSlotToItemStack(T be, int slot) {
        SlottedCanisterState state = be.containerState();
        ItemStack stack = state.canisters.get(slot);
        if (stack.isEmpty() || state.slots.handlers()[slot] == null) { return; }
        CanisterItem.setFluidContent(stack, state.slots.handlers()[slot].toFluidContent());
        snapshotSlotStream(be, slot);
        BlockEntitySync.markDirtyAndSync(be);
    }

    /**
     * Copies the handler's transient stream state (type, rate, tick) to the
     * component's arrays so the renderer can visualize active transfers.
     *
     * @param be   the block entity that holds canister slots
     * @param slot the slot index
     * @param <T>  block entity type implementing ICanisterHolder
     */
    static <T extends BlockEntity & ICanisterHolder> void snapshotSlotStream(T be, int slot) {
        SlottedCanisterState state = be.containerState();
        CanisterSlotFluidHandler h = state.slots.handlers()[slot];
        if (h == null) { return; }
        long tick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.slots.streamType()[slot] = h.getStreamGooType(tick);
        state.slots.streamRate()[slot] = h.getStreamRate(tick);
        state.slots.streamTick()[slot] = tick;
    }
}
