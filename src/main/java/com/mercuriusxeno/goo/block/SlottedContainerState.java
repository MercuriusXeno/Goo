package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * Behavioral component that owns the mutable slot arrays and goo operations
 * shared by {@link CanisterBlockEntity} and {@link HubBlockEntity}. Each BE
 * holds one instance and delegates {@link ISlottedGooContainer} methods here,
 * keeping framework overrides and gasket coordination on the BE itself.
 *
 * <p>Owns: canister stacks, per-slot fluid handlers, per-slot gasket pushers,
 * stream-state arrays, and the cached VoxelShape.</p>
 */
public class SlottedContainerState {

    private final int maxSlots;
    private final Runnable syncCallback;
    private final Function<List<ItemStack>, VoxelShape> shapeComputer;

    /** Canister stacks stored in the grid, indexed 0 to maxSlots-1. */
    final List<ItemStack> canisters;
    /** Live fluid handlers per slot - non-null when the slot is occupied. */
    final @Nullable GooFluidHandler[] slotHandlers;
    /** Per-slot gasket pushers - non-null when the slot has an active transmitter. */
    final @Nullable IGasketPusher[] slotPushers;
    /** Per-slot goo type of the active incoming stream, or null if idle. */
    final @Nullable GooType[] slotStreamType;
    /** Per-slot transfer rate of the active stream in mB/tick. */
    final int[] slotStreamRate;
    /** Per-slot game tick of the last stream event. */
    final long[] slotStreamTick;
    /** Cached composite shape of all occupied slots. Null when dirty. */
    @Nullable VoxelShape cachedShape;

    /**
     * Creates a new container state component.
     *
     * @param maxSlots      number of canister slots
     * @param canisters     the canister list (must be pre-sized to maxSlots)
     * @param syncCallback  called when contents change (markDirtyAndSync)
     * @param shapeComputer computes the VoxelShape from the canister list
     */
    public SlottedContainerState(int maxSlots, List<ItemStack> canisters,
            Runnable syncCallback, Function<List<ItemStack>, VoxelShape> shapeComputer) {
        this.maxSlots = maxSlots;
        this.canisters = canisters;
        this.syncCallback = syncCallback;
        this.shapeComputer = shapeComputer;
        this.slotHandlers = new GooFluidHandler[maxSlots];
        this.slotPushers = new IGasketPusher[maxSlots];
        this.slotStreamType = new GooType[maxSlots];
        this.slotStreamRate = new int[maxSlots];
        this.slotStreamTick = new long[maxSlots];
    }

    /** Returns the maximum number of slots.
     *
     * @return the slot count
     */
    public int maxSlots() { return maxSlots; }

    // --- ISlottedGooContainer delegates ---

    /**
     * Returns the canister stack in the given slot, or EMPTY if out of range.
     *
     * @param slot the slot index
     * @return the canister stack
     */
    public ItemStack getCanister(int slot) {
        if (slot < 0 || slot >= maxSlots) { return ItemStack.EMPTY; }
        return canisters.get(slot);
    }

    /** Notifies the owning BE that slot contents changed. */
    public void onSlotChanged() { syncCallback.run(); }

    /**
     * Returns the goo contents for a slot via its handler, falling back to
     * the item stack if no handler is wired.
     *
     * @param slot the slot index
     * @return the goo contents, or EMPTY
     */
    public GooContents getSlotGooContents(int slot) {
        GooFluidHandler h = (slot >= 0 && slot < maxSlots) ? slotHandlers[slot] : null;
        return h != null ? h.toGooContents() : GooContents.EMPTY;
    }

    /**
     * Inserts goo into the canister at the given slot via its handler.
     *
     * @param slot         the slot index
     * @param incomingType the goo type to insert
     * @param volume       volume in microblobs
     * @return the amount actually inserted
     */
    public long insertGoo(int slot, GooType incomingType, long volume) {
        GooFluidHandler h = (slot >= 0 && slot < maxSlots) ? slotHandlers[slot] : null;
        if (h == null) { return 0L; }
        return h.insertGoo(incomingType, (int) Math.min(volume, Integer.MAX_VALUE), false);
    }

    /**
     * Extracts goo of a specific type from the canister at the given slot.
     *
     * @param slot      the slot index
     * @param type      the goo type to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public long extractGoo(int slot, GooType type, long requested) {
        GooFluidHandler h = (slot >= 0 && slot < maxSlots) ? slotHandlers[slot] : null;
        if (h == null) { return 0L; }
        return h.extractGoo(type, (int) Math.min(requested, Integer.MAX_VALUE), false);
    }

    /**
     * Returns true if the given slot can accept more goo (has remaining capacity).
     *
     * @param slot the slot index
     * @return true if the slot has space
     */
    public boolean canAccept(int slot) {
        if (slot < 0 || slot >= maxSlots || canisters.get(slot).isEmpty()) { return false; }
        GooFluidHandler h = slotHandlers[slot];
        if (h == null) { return false; }
        int compression = GooEnchantments.getCompressionLevel(canisters.get(slot));
        return h.totalVolume() < ContainerCapacity.canisterCapacity(compression);
    }

    // --- Accessors ---

    /**
     * Returns true if any slot contains a canister.
     *
     * @return true if any slot is occupied
     */
    public boolean hasAnyCanister() {
        for (ItemStack stack : canisters) {
            if (!stack.isEmpty()) { return true; }
        }
        return false;
    }

    /**
     * Returns the live fluid handler for a slot, or null if empty.
     *
     * @param slot the slot index
     * @return the handler, or null
     */
    public @Nullable GooFluidHandler getSlotFluidHandler(int slot) {
        return (slot >= 0 && slot < maxSlots) ? slotHandlers[slot] : null;
    }

    /**
     * Returns the stream goo type for a slot, or null if no active stream.
     *
     * @param slot        the slot index
     * @param currentTick the current game tick
     * @return the stream type, or null
     */
    public @Nullable GooType getSlotStreamType(int slot, long currentTick) {
        if (slot < 0 || slot >= maxSlots) { return null; }
        return (currentTick - slotStreamTick[slot] <= 1) ? slotStreamType[slot] : null;
    }

    /**
     * Returns the stream rate for a slot in mB/tick, or 0 if no active stream.
     *
     * @param slot        the slot index
     * @param currentTick the current game tick
     * @return the stream rate
     */
    public int getSlotStreamRate(int slot, long currentTick) {
        if (slot < 0 || slot >= maxSlots) { return 0; }
        return (currentTick - slotStreamTick[slot] <= 1) ? slotStreamRate[slot] : 0;
    }

    // --- Shape ---

    /**
     * Returns the cached composite shape, recomputing if invalidated.
     *
     * @return the voxel shape
     */
    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = shapeComputer.apply(canisters);
        }
        return cachedShape;
    }

    /** Invalidates the cached shape so it is recomputed on next access. */
    public void invalidateShape() { cachedShape = null; }

    // --- Routing ---

    /**
     * Routes goo across all slots with remaining capacity.
     * Used by Hub's intake to distribute incoming goo.
     *
     * @param type   the goo type
     * @param amount volume in microblobs
     * @return the amount routed
     */
    public long routeGoo(GooType type, long amount) {
        long remaining = amount;
        for (int i = 0; i < maxSlots && remaining > 0; i++) {
            GooFluidHandler handler = slotHandlers[i];
            if (handler == null) { continue; }
            int toInsert = (int) Math.min(remaining, Integer.MAX_VALUE);
            remaining -= handler.insertGoo(type, toInsert, false);
        }
        if (remaining < amount) { syncCallback.run(); }
        return amount - remaining;
    }

    // --- Pusher tick ---

    /** Ticks all active slot pushers. */
    public void tickPushers() {
        for (IGasketPusher pusher : slotPushers) {
            if (pusher != null) { pusher.tick(); }
        }
    }

    /** Disposes all active slot pushers (used during block removal). */
    public void disposeAllPushers() {
        for (int i = 0; i < maxSlots; i++) {
            IGasketPusher existing = slotPushers[i];
            if (existing != null) {
                existing.dispose();
                slotPushers[i] = null;
            }
        }
    }
}
