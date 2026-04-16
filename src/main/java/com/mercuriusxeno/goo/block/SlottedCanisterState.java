package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.fluid.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * Behavioral component that owns the mutable slot arrays and fluid operations
 * shared by {@link CanisterBlockEntity} and {@link HubBlockEntity}. Each BE
 * holds one instance and delegates {@link ICanisterHolder} methods here,
 * keeping framework overrides and gasket coordination on the BE itself.
 *
 * <p>Owns: canister stacks, per-slot fluid handlers, per-slot gasket pushers,
 * stream-state arrays, and the cached VoxelShape.</p>
 */
public class SlottedCanisterState {

    private final int maxSlots;
    private final Runnable syncCallback;
    private final Function<List<ItemStack>, VoxelShape> shapeComputer;

    /** Canister stacks stored in the grid, indexed 0 to maxSlots-1. */
    final List<ItemStack> canisters;
    /** Per-slot tracking arrays for handlers, pushers, and stream state. */
    final CanisterSlotArrays slots;
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
    public SlottedCanisterState(int maxSlots, List<ItemStack> canisters,
            Runnable syncCallback, Function<List<ItemStack>, VoxelShape> shapeComputer) {
        this.maxSlots = maxSlots;
        this.canisters = canisters;
        this.syncCallback = syncCallback;
        this.shapeComputer = shapeComputer;
        this.slots = new CanisterSlotArrays(maxSlots);
    }

    /** Per-slot arrays for fluid handlers, gasket pushers, and stream visualization state. */
    record CanisterSlotArrays(
            @Nullable CanisterSlotFluidHandler[] handlers,
            @Nullable IGasketPusher[] pushers,
            @Nullable GooType[] streamType,
            int[] streamRate,
            long[] streamTick) {
        CanisterSlotArrays(int size) {
            this(new CanisterSlotFluidHandler[size], new IGasketPusher[size],
                 new GooType[size], new int[size], new long[size]);
        }
    }

    /** Returns the maximum number of slots.
     *
     * @return the slot count
     */
    public int maxSlots() { return maxSlots; }

    // --- ICanisterHolder delegates ---

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
     * Returns the fluid content for a slot via its handler, falling back to EMPTY.
     *
     * @param slot the slot index
     * @return the fluid content, or EMPTY
     */
    public CanisterFluidContent getSlotFluidContent(int slot) {
        CanisterSlotFluidHandler h = (slot >= 0 && slot < maxSlots) ? slots.handlers()[slot] : null;
        return h != null ? h.toFluidContent() : CanisterFluidContent.EMPTY;
    }

    /**
     * Inserts fluid into the canister at the given slot via its handler.
     *
     * @param slot   the slot index
     * @param fluid  the fluid to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    public int insertFluid(int slot, Fluid fluid, int volume) {
        CanisterSlotFluidHandler h = (slot >= 0 && slot < maxSlots) ? slots.handlers()[slot] : null;
        if (h == null) { return 0; }
        return h.insertFluid(fluid, (int) Math.min(volume, Integer.MAX_VALUE), false);
    }

    /**
     * Convenience: insert goo by type.
     *
     * @param slot         the slot index
     * @param incomingType the goo type to insert
     * @param volume       volume in microblobs
     * @return the amount actually inserted
     */
    public int insertGoo(int slot, GooType incomingType, int volume) {
        return insertFluid(slot, GooFluids.SOURCES.get(incomingType).get(), volume);
    }

    /**
     * Extracts fluid from the canister at the given slot.
     *
     * @param slot      the slot index
     * @param fluid     the fluid to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public int extractFluid(int slot, Fluid fluid, int requested) {
        CanisterSlotFluidHandler h = (slot >= 0 && slot < maxSlots) ? slots.handlers()[slot] : null;
        if (h == null) { return 0; }
        return h.extractFluid(fluid, (int) Math.min(requested, Integer.MAX_VALUE), false);
    }

    /**
     * Convenience: extract goo by type.
     *
     * @param slot      the slot index
     * @param type      the goo type to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public int extractGoo(int slot, GooType type, int requested) {
        return extractFluid(slot, GooFluids.SOURCES.get(type).get(), requested);
    }

    /**
     * Returns true if the given slot can accept more fluid (has remaining capacity).
     *
     * @param slot the slot index
     * @return true if the slot has space
     */
    public boolean canAccept(int slot) {
        if (slot < 0 || slot >= maxSlots || canisters.get(slot).isEmpty()) { return false; }
        CanisterSlotFluidHandler h = slots.handlers()[slot];
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
    public @Nullable CanisterSlotFluidHandler getSlotFluidHandler(int slot) {
        return (slot >= 0 && slot < maxSlots) ? slots.handlers()[slot] : null;
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
        return (currentTick - slots.streamTick()[slot] <= 1) ? slots.streamType()[slot] : null;
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
        return (currentTick - slots.streamTick()[slot] <= 1) ? slots.streamRate()[slot] : 0;
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
     * Routes fluid across all slots: first tries matching slots, then empty slots.
     * Used by Hub's intake to distribute incoming fluid.
     *
     * @param fluid  the fluid to route
     * @param amount volume in microblobs
     * @return the amount routed
     */
    public int routeFluid(Fluid fluid, int amount) {
        int routed = distributeAcrossSlots(fluid, amount);
        if (routed > 0) { syncCallback.run(); }
        return routed;
    }

    /**
     * Convenience: route goo by type.
     *
     * @param type   the goo type
     * @param amount volume in microblobs
     * @return the amount routed
     */
    public int routeGoo(GooType type, int amount) {
        return routeFluid(GooFluids.SOURCES.get(type).get(), amount);
    }

    /**
     * Distributes fluid across slots: matching first, then empty.
     *
     * @param fluid  the fluid to distribute
     * @param amount the total volume to distribute
     * @return the total volume accepted across all slots
     */
    private int distributeAcrossSlots(Fluid fluid, int amount) {
        int remaining = amount;
        // First pass: slots already holding this fluid
        for (int i = 0; i < maxSlots && remaining > 0; i++) {
            CanisterSlotFluidHandler handler = slots.handlers()[i];
            if (handler == null || handler.isEmpty() || handler.getFluid() != fluid) { continue; }
            int toInsert = (int) Math.min(remaining, Integer.MAX_VALUE);
            remaining -= handler.insertFluid(fluid, toInsert, false);
        }
        // Second pass: empty slots
        for (int i = 0; i < maxSlots && remaining > 0; i++) {
            CanisterSlotFluidHandler handler = slots.handlers()[i];
            if (handler == null || !handler.isEmpty()) { continue; }
            int toInsert = (int) Math.min(remaining, Integer.MAX_VALUE);
            remaining -= handler.insertFluid(fluid, toInsert, false);
        }
        return amount - remaining;
    }

    // --- Pusher tick ---

    /** Ticks all active slot pushers. */
    public void tickPushers() {
        for (IGasketPusher pusher : slots.pushers()) {
            if (pusher != null) { pusher.tick(); }
        }
    }

    /** Disposes all active slot pushers (used during block removal). */
    public void disposeAllPushers() {
        for (int i = 0; i < maxSlots; i++) {
            IGasketPusher existing = slots.pushers()[i];
            if (existing != null) {
                existing.dispose();
                slots.pushers()[i] = null;
            }
        }
    }
}
