package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.fluid.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import net.minecraft.world.item.ItemStack;

/**
 * Per-slot fluid handler and gasket pusher lifecycle for CanisterBlockEntity.
 * Creates, syncs, rebuilds, and disposes handlers and pushers when canister
 * slots change. Also handles insert/remove orchestration to keep the entity
 * under the TooManyMethods threshold.
 */
final class CanisterSlotHandlers {

    private CanisterSlotHandlers() {}

    // --- Insert / Remove orchestration ---

    /**
     * Inserts a canister into the given slot, optionally stripping gasket UUIDs.
     *
     * @param be            the canister block entity
     * @param slot          the slot index
     * @param canisterStack the canister item stack to insert
     * @param stripGaskets  true to clear gasket UUIDs on the copy
     * @return true if inserted
     */
    static boolean insertCanister(CanisterBlockEntity be, int slot, ItemStack canisterStack, boolean stripGaskets) {
        SlottedCanisterState state = be.containerState();
        if (!canInsertAt(state, slot, canisterStack)) { return false; }
        ItemStack copy = prepareCanisterCopy(canisterStack, stripGaskets);
        placeAndActivate(be, state, slot, copy);
        return true;
    }

    /**
     * Copies a single canister, optionally stripping gasket metadata.
     * @param stack the source canister item stack
     * @param stripGaskets true to clear gasket UUIDs on the copy
     * @return a single-count copy of the canister, optionally stripped
     */
    private static ItemStack prepareCanisterCopy(ItemStack stack, boolean stripGaskets) {
        ItemStack copy = stack.copyWithCount(1);
        if (stripGaskets) { CanisterSlotLifecycle.stripGasketMetadata(copy); }
        return copy;
    }

    /**
     * Places a canister in the slot and wires up its handler and pusher.
     * @param be the canister block entity
     * @param state the container's current slot state
     * @param slot the target slot index
     * @param copy the canister item stack to place
     */
    private static void placeAndActivate(CanisterBlockEntity be, SlottedCanisterState state, int slot, ItemStack copy) {
        state.canisters.set(slot, copy);
        state.slots.handlers()[slot] = createSlotHandler(be, slot);
        rebuildSlotPusher(be, slot);
        onSlotStructureChanged(be, slot, true);
    }

    /**
     * Validates that the slot index is in range, the stack is a canister, and the slot is empty.
     * @param state         the container's current slot state
     * @param slot          the target slot index
     * @param canisterStack the canister item stack being inserted
     * @return true if the slot is valid, empty, and the stack is a canister
     */
    private static boolean canInsertAt(SlottedCanisterState state, int slot, ItemStack canisterStack) {
        return slot >= 0 && slot < CanisterBlockEntity.MAX_SLOTS
                && canisterStack.getItem() instanceof CanisterItem
                && state.canisters.get(slot).isEmpty();
    }

    /**
     * Removes the canister from the given slot.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     * @return the removed canister stack, or EMPTY
     */
    static ItemStack removeCanister(CanisterBlockEntity be, int slot) {
        SlottedCanisterState state = be.containerState();
        if (slot < 0 || slot >= CanisterBlockEntity.MAX_SLOTS
                || state.canisters.get(slot).isEmpty()) { return ItemStack.EMPTY; }
        tearDownSlot(be, slot, state);
        ItemStack removed = state.canisters.get(slot).copy();
        state.canisters.set(slot, ItemStack.EMPTY);
        onSlotStructureChanged(be, slot, false);
        return removed;
    }

    /**
     * Disposes the pusher, syncs the handler, clears the handler, and deregisters gaskets.
     * @param be    the canister block entity
     * @param slot  the slot index being torn down
     * @param state the container's current slot state
     */
    private static void tearDownSlot(CanisterBlockEntity be, int slot, SlottedCanisterState state) {
        disposeSlotPusher(be, slot);
        syncSlotToItemStack(be, slot);
        state.slots.handlers()[slot] = null;
        CanisterGasketOps.deregisterSlotGaskets(be, slot);
    }

    /**
     * Wires up a freshly placed canister slot: creates handler, rebuilds pusher,
     * invalidates shape/capabilities, registers gaskets, and syncs.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     */
    /**
     * Wires up a freshly placed canister slot: creates handler, rebuilds pusher,
     * invalidates shape/capabilities, registers gaskets, and syncs.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     */
    static void wireSlotAfterPlacement(CanisterBlockEntity be, int slot) {
        be.containerState().slots.handlers()[slot] = createSlotHandler(be, slot);
        onSlotStructureChanged(be, slot, true);
    }

    /**
     * Shared lifecycle after a slot's canister changes. Invalidates shape and
     * capabilities, optionally registers gaskets, and syncs to clients.
     *
     * @param be       the canister block entity
     * @param slot     the slot that changed
     * @param register true to register gaskets (insert), false to skip
     */
    private static void onSlotStructureChanged(CanisterBlockEntity be, int slot, boolean register) {
        be.containerState().invalidateShape();
        BlockEntitySync.invalidateCapabilities(be);
        if (register) { CanisterGasketOps.registerSlotGaskets(be, slot); }
        BlockEntitySync.markDirtyAndSync(be);
    }

    // --- Per-slot fluid handler lifecycle (delegates to ICanisterHolder) ---

    /**
     * Creates a live handler for the given slot, loaded from the canister ItemStack.
     * Delegates to {@link ICanisterHolder#createSlotHandler}.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     * @return a new fluid handler for the slot
     */
    static CanisterSlotFluidHandler createSlotHandler(CanisterBlockEntity be, int slot) {
        return ICanisterHolder.createSlotHandler(be, slot);
    }

    /**
     * Writes the slot handler's current state back to the canister ItemStack.
     * Delegates to {@link ICanisterHolder#syncSlotToItemStack}.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     */
    static void syncSlotToItemStack(CanisterBlockEntity be, int slot) {
        ICanisterHolder.syncSlotToItemStack(be, slot);
    }

    /** Rebuilds slot handlers for all occupied slots (used after deserialization).
     *
     * @param be the canister block entity
     */
    static void rebuildAllSlotHandlers(CanisterBlockEntity be) {
        SlottedCanisterState state = be.containerState();
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            state.slots.handlers()[i] = state.canisters.get(i).isEmpty() ? null : createSlotHandler(be, i);
        }
    }

    // --- Per-slot gasket pusher lifecycle ---

    /**
     * Rebuilds the gasket pusher for a slot based on its bottom gasket state.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     */
    static void rebuildSlotPusher(CanisterBlockEntity be, int slot) {
        disposeSlotPusher(be, slot);
        if (!slotNeedsPusher(be, slot)) { return; }
        SlottedCanisterState state = be.containerState();
        state.slots.pushers()[slot] = buildPusher(be, state, slot);
    }

    /**
     * Constructs a GasketPusher wired to the slot's bottom gasket metadata.
     * @param be the canister block entity
     * @param state the container's current slot state
     * @param slot the slot index to build a pusher for
     * @return a new GasketPusher wired to the slot's bottom gasket
     */
    private static GasketPusher buildPusher(CanisterBlockEntity be, SlottedCanisterState state, int slot) {
        GasketPusher pusher = new GasketPusher(state.slots.handlers()[slot],
            () -> CanisterItem.getMetadata(state.canisters.get(slot)).bottomGasketId(),
            () -> CanisterItem.getMetadata(state.canisters.get(slot)).bottomPartner(),
            be::getLevel, be::getBlockPos,
            () -> syncSlotToItemStack(be, slot), be.gasketRegistryAccess);
        pusher.rebuildCache();
        return pusher;
    }

    /**
     * Returns true if the slot has a handler, a canister, and a paired bottom gasket.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     * @return true if a pusher should be created for this slot
     */
    private static boolean slotNeedsPusher(CanisterBlockEntity be, int slot) {
        SlottedCanisterState state = be.containerState();
        if (state.slots.handlers()[slot] == null) { return false; }
        ItemStack stack = state.canisters.get(slot);
        if (stack.isEmpty()) { return false; }
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        return meta.bottomGasketId() != null && meta.bottomPartner() != null;
    }

    /**
     * Disposes and nulls the pusher for the given slot.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     */
    static void disposeSlotPusher(CanisterBlockEntity be, int slot) {
        SlottedCanisterState state = be.containerState();
        IGasketPusher existing = state.slots.pushers()[slot];
        if (existing != null) {
            existing.dispose();
            state.slots.pushers()[slot] = null;
        }
    }

    /** Rebuilds pushers for all occupied slots.
     *
     * @param be the canister block entity
     */
    static void rebuildAllSlotPushers(CanisterBlockEntity be) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            rebuildSlotPusher(be, i);
        }
    }
}
