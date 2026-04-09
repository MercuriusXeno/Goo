package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;

/**
 * Static helpers for hub slot lifecycle: handler creation, pusher management,
 * shape caching, and stream snapshotting. Extracted from HubBlockEntity to
 * keep the framework override surface under the TooManyMethods threshold.
 */
final class HubSlotLifecycle {

    private HubSlotLifecycle() {}

    /**
     * Returns true if the given slot can accept a canister insertion.
     *
     * @param be            the hub block entity
     * @param slot          the slot index
     * @param canisterStack the canister item stack
     * @return true if the slot is valid, empty, and the stack is a canister
     */
    static boolean canInsertAt(HubBlockEntity be, int slot, ItemStack canisterStack) {
        return slot >= 0 && slot < HubBlockEntity.MAX_CANISTERS
                && canisterStack.getItem() instanceof CanisterItem
                && be.containerState().canisters.get(slot).isEmpty();
    }

    // --- Insert / Remove orchestration ---

    /**
     * Inserts a canister into the first empty slot.
     *
     * @param be            the hub block entity
     * @param canisterStack the canister item stack
     * @return true if inserted
     */
    static boolean insertCanister(HubBlockEntity be, ItemStack canisterStack) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (canInsertAt(be, i, canisterStack)) {
                return insertCanister(be, i, canisterStack);
            }
        }
        return false;
    }

    /**
     * Inserts a canister into a specific slot.
     *
     * @param be            the hub block entity
     * @param slot          the slot index (0-7)
     * @param canisterStack the canister item stack to insert
     * @return true if inserted
     */
    static boolean insertCanister(HubBlockEntity be, int slot, ItemStack canisterStack) {
        if (!canInsertAt(be, slot, canisterStack)) { return false; }
        SlottedContainerState state = be.containerState();
        state.canisters.set(slot, canisterStack.copyWithCount(1));
        state.slotHandlers[slot] = createSlotHandler(be, slot);
        rebuildSlotPusher(be, slot);
        onSlotStructureChanged(be);
        return true;
    }

    /**
     * Removes the canister from a specific slot.
     *
     * @param be   the hub block entity
     * @param slot the slot index (0-7)
     * @return the removed stack, or EMPTY
     */
    static ItemStack removeCanister(HubBlockEntity be, int slot) {
        SlottedContainerState state = be.containerState();
        if (slot < 0 || slot >= HubBlockEntity.MAX_CANISTERS || state.canisters.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }
        return detachCanister(be, state, slot);
    }

    /** Detaches a canister from a confirmed-occupied slot, returning its stack.
     *
     * @param be    the hub block entity
     * @param state the container state
     * @param slot  the slot index
     * @return the removed canister stack
     */
    private static ItemStack detachCanister(HubBlockEntity be, SlottedContainerState state, int slot) {
        disposeSlotPusher(be, slot);
        syncSlotToItemStack(be, slot);
        state.slotHandlers[slot] = null;
        ItemStack removed = state.canisters.get(slot).copy();
        state.canisters.set(slot, ItemStack.EMPTY);
        onSlotStructureChanged(be);
        return removed;
    }

    /**
     * Shared lifecycle after a slot's canister changes. Invalidates shape, capabilities, and syncs.
     *
     * @param be the hub block entity
     */
    static void onSlotStructureChanged(HubBlockEntity be) {
        be.containerState().invalidateShape();
        BlockEntitySync.invalidateCapabilities(be);
        BlockEntitySync.markDirtyAndSync(be);
    }

    // --- Per-slot fluid handler lifecycle ---

    /**
     * Creates a live handler for the given slot, loaded from the canister ItemStack.
     *
     * @param be   the hub block entity
     * @param slot the slot index
     * @return the new slot handler
     */
    static GooFluidHandler createSlotHandler(HubBlockEntity be, int slot) {
        ItemStack stack = be.containerState().canisters.get(slot);
        int capacity = (int) ContainerCapacity.canisterCapacity(
            GooEnchantments.getCompressionLevel(stack));
        GooFluidHandler handler = new GooFluidHandler(capacity,
            () -> syncSlotToItemStack(be, slot),
            () -> be.getLevel() != null ? be.getLevel().getGameTime() : 0L);
        handler.loadFrom(CanisterItem.getGooContents(stack));
        return handler;
    }

    /**
     * Writes the slot handler's current state back to the canister ItemStack.
     *
     * @param be   the hub block entity
     * @param slot the slot index
     */
    static void syncSlotToItemStack(HubBlockEntity be, int slot) {
        SlottedContainerState state = be.containerState();
        ItemStack stack = state.canisters.get(slot);
        if (stack.isEmpty() || state.slotHandlers[slot] == null) { return; }
        CanisterItem.setGooContents(stack, state.slotHandlers[slot].toGooContents());
        snapshotSlotStream(be, slot);
        BlockEntitySync.markDirtyAndSync(be);
    }

    /**
     * Copies the handler's transient stream state to the component's arrays for sync.
     *
     * @param be   the hub block entity
     * @param slot the slot index
     */
    static void snapshotSlotStream(HubBlockEntity be, int slot) {
        SlottedContainerState state = be.containerState();
        GooFluidHandler h = state.slotHandlers[slot];
        if (h == null) { return; }
        long tick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.slotStreamType[slot] = h.getStreamType(tick);
        state.slotStreamRate[slot] = h.getStreamRate(tick);
        state.slotStreamTick[slot] = tick;
    }

    /**
     * Rebuilds slot handlers for all occupied slots.
     *
     * @param be the hub block entity
     */
    static void rebuildAllSlotHandlers(HubBlockEntity be) {
        SlottedContainerState state = be.containerState();
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            state.slotHandlers[i] = state.canisters.get(i).isEmpty() ? null : createSlotHandler(be, i);
        }
    }

    // --- Per-slot gasket pusher lifecycle ---

    /**
     * Rebuilds the pusher for a slot based on its bottom gasket state.
     *
     * @param be   the hub block entity
     * @param slot the slot index
     */
    static void rebuildSlotPusher(HubBlockEntity be, int slot) {
        SlottedContainerState state = be.containerState();
        disposeSlotPusher(be, slot);
        GooFluidHandler handler = state.slotHandlers[slot];
        if (handler == null) { return; }
        if (!hasBottomGasket(state, slot)) { return; }
        state.slotPushers[slot] = createSlotPusher(be, state, slot, handler);
    }

    /** Returns true if the canister at the given slot has a bottom gasket with a partner.
     *
     * @param state the container state
     * @param slot  the slot index
     * @return true if a bottom gasket and partner are present
     */
    private static boolean hasBottomGasket(SlottedContainerState state, int slot) {
        ItemStack stack = state.canisters.get(slot);
        if (stack.isEmpty()) { return false; }
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        return meta.bottomGasketId() != null && meta.bottomPartner() != null;
    }

    /** Creates and initializes a gasket pusher for the given slot.
     *
     * @param be      the hub block entity
     * @param state   the container state
     * @param slot    the slot index
     * @param handler the slot's fluid handler
     * @return the initialized pusher
     */
    private static GasketPusher createSlotPusher(
            HubBlockEntity be, SlottedContainerState state, int slot, GooFluidHandler handler) {
        final int s = slot;
        GasketPusher pusher = new GasketPusher(
            handler,
            () -> CanisterItem.getMetadata(state.canisters.get(s)).bottomGasketId(),
            () -> CanisterItem.getMetadata(state.canisters.get(s)).bottomPartner(),
            be::getLevel, be::getBlockPos,
            () -> syncSlotToItemStack(be, s),
            be.gasketRegistryAccess);
        pusher.rebuildCache();
        return pusher;
    }

    /**
     * Disposes and nulls the pusher for the given slot.
     *
     * @param be   the hub block entity
     * @param slot the slot index
     */
    static void disposeSlotPusher(HubBlockEntity be, int slot) {
        SlottedContainerState state = be.containerState();
        IGasketPusher existing = state.slotPushers[slot];
        if (existing != null) {
            existing.dispose();
            state.slotPushers[slot] = null;
        }
    }

    /**
     * Rebuilds pushers for all occupied slots.
     *
     * @param be the hub block entity
     */
    static void rebuildAllSlotPushers(HubBlockEntity be) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            rebuildSlotPusher(be, i);
        }
    }

    // --- Dynamic VoxelShape ---

    /**
     * Computes the union of the frame and all occupied slot shapes.
     *
     * @param stacks the canister stacks
     * @return the computed shape
     */
    static VoxelShape computeShape(List<ItemStack> stacks) {
        VoxelShape result = HubBlock.frameShape();
        for (int i = 0; i < stacks.size(); i++) {
            if (!stacks.get(i).isEmpty()) {
                result = Shapes.or(result, HubBlock.slotShape(i));
            }
        }
        return result;
    }
}
