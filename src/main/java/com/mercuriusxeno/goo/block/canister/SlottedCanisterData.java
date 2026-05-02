package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Behavioral component that owns the slot grid for {@link CanisterBlockEntity}
 * and {@link HubBlockEntity}. Each BE holds one instance and exposes its slots
 * via {@link #slots}. Per-slot operations live on {@link CanisterSlot} as
 * instance methods. This class only handles cross-slot work: composite shape
 * (re-ORed on slot mutation, not access), fluid routing across slots, and
 * lifecycle for all pushers.
 */
public class SlottedCanisterData {

    /** Per-slot state. Index range is {@code [0, maxSlots)}. */
    public final CanisterSlot[] slots;

    private final int maxSlots;
    private final Runnable syncCallback;
    private final Function<CanisterSlot[], VoxelShape> shapeBuilder;
    private VoxelShape compositeShape;

    /**
     * Creates the slot grid.
     *
     * @param maxSlots     number of slots
     * @param slotShapeFor function from slot index to its filled voxel shape
     * @param shapeBuilder builds the composite voxel shape from the slot array;
     *                     called on each structural change. The BE supplies
     *                     this so it can include block-level geometry (e.g.,
     *                     hub frame) and pick a fallback when no slot is occupied.
     * @param syncCallback called when contents change (typically markDirtyAndSync)
     */
    public SlottedCanisterData(int maxSlots,
            IntFunction<VoxelShape> slotShapeFor,
            Function<CanisterSlot[], VoxelShape> shapeBuilder,
            Runnable syncCallback) {
        this.maxSlots = maxSlots;
        this.syncCallback = syncCallback;
        this.shapeBuilder = shapeBuilder;
        this.slots = new CanisterSlot[maxSlots];
        for (int i = 0; i < maxSlots; i++) {
            slots[i] = new CanisterSlot(i, slotShapeFor.apply(i),
                    syncCallback, this::onStructureChanged);
        }
        this.compositeShape = shapeBuilder.apply(this.slots);
    }

    /** @return the configured slot count */
    public int maxSlots() {
        return maxSlots;
    }

    /**
     * @param index the slot index
     * @return true if {@code index} is within the slot grid
     */
    public boolean inRange(int index) {
        return index >= 0 && index < maxSlots;
    }

    /** @return true if any slot is occupied */
    public boolean hasAnyCanister() {
        for (CanisterSlot slot : slots) {
            if (!slot.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** @return the eagerly-maintained composite voxel shape across all slots */
    public VoxelShape compositeShape() {
        return compositeShape;
    }

    /** Triggers the BE's sync callback. Used by callers that mutate slot
     *  state outside the slot's own setters and need to signal sync. */
    public void markChanged() {
        syncCallback.run();
    }

    // --- Slot-keyed accessors (route through slot) ---

    /** @param index the slot index
     *  @return the canister stack at the given slot, or EMPTY if out of range */
    public ItemStack getCanister(int index) {
        return inRange(index) ? slots[index].canister() : ItemStack.EMPTY;
    }

    /** @param index the slot index
     *  @return the slot's fluid content, or EMPTY */
    public CanisterFluidContent getSlotFluidContent(int index) {
        return inRange(index) ? slots[index].fluidContent() : CanisterFluidContent.EMPTY;
    }

    /** @param index the slot index
     *  @return the live fluid handler for the slot, or null */
    public @Nullable CanisterSlotFluidHandler getSlotFluidHandler(int index) {
        return inRange(index) ? slots[index].handler() : null;
    }

    /** @param index the slot index
     *  @return true if the slot has a canister with remaining capacity */
    public boolean canAccept(int index) {
        return inRange(index) && slots[index].canAccept();
    }

    /**
     * Inserts fluid into the slot's canister.
     *
     * @param index  the slot index
     * @param fluid  the fluid to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    public int insertFluid(int index, Fluid fluid, int volume) {
        return inRange(index) ? slots[index].insertFluid(fluid, volume) : 0;
    }

    /**
     * Extracts fluid from the slot's canister.
     *
     * @param index     the slot index
     * @param fluid     the fluid to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public int extractFluid(int index, Fluid fluid, int requested) {
        return inRange(index) ? slots[index].extractFluid(fluid, requested) : 0;
    }

    /**
     * Convenience: insert goo by type.
     *
     * @param index  the slot index
     * @param type   the goo type to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    public int insertGoo(int index, GooType type, int volume) {
        return inRange(index) ? slots[index].insertGoo(type, volume) : 0;
    }

    /**
     * Convenience: extract goo by type.
     *
     * @param index     the slot index
     * @param type      the goo type
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public int extractGoo(int index, GooType type, int requested) {
        return inRange(index) ? slots[index].extractGoo(type, requested) : 0;
    }

    /**
     * @param index       the slot index
     * @param currentTick the current game tick
     * @return the slot's snapshot stream goo type if fresh, else null
     */
    public @Nullable GooType getSlotStreamType(int index, long currentTick) {
        return inRange(index) ? slots[index].getStreamType(currentTick) : null;
    }

    /**
     * @param index       the slot index
     * @param currentTick the current game tick
     * @return the slot's snapshot stream fluid if fresh, else null
     */
    public @Nullable Fluid getSlotStreamFluid(int index, long currentTick) {
        return inRange(index) ? slots[index].getStreamFluid(currentTick) : null;
    }

    /**
     * @param index       the slot index
     * @param currentTick the current game tick
     * @return the slot's snapshot stream rate in mB/tick if fresh, else 0
     */
    public int getSlotStreamRate(int index, long currentTick) {
        return inRange(index) ? slots[index].getStreamRate(currentTick) : 0;
    }

    /**
     * Recomputes {@link #compositeShape} and signals sync. Invoked by each
     * slot's structure-changed callback so the cache stays current at
     * mutation time, not at access time.
     */
    private void onStructureChanged() {
        compositeShape = shapeBuilder.apply(slots);
        syncCallback.run();
    }

    /**
     * Recomputes {@link #compositeShape} from current slot state without
     * firing the sync callback. Call after a batch slot mutation that
     * bypasses {@link CanisterSlot#setCanister(net.minecraft.world.item.ItemStack)}
     * (e.g., NBT load), since {@link CanisterSlot#load} writes the slot's
     * canister and shape fields directly without triggering the structure-
     * changed callback.
     */
    public void rebuildCompositeShape() {
        compositeShape = shapeBuilder.apply(slots);
    }

    // --- Cross-slot fluid routing (used by Hub intake) ---

    /**
     * Distributes fluid across slots: matching slots first, then empty slots.
     *
     * @param fluid  the fluid to route
     * @param amount volume in microblobs
     * @return total volume accepted across all slots
     */
    public int routeFluid(Fluid fluid, int amount) {
        int routed = distributeAcrossSlots(fluid, amount);
        if (routed > 0) {
            syncCallback.run();
        }
        return routed;
    }

    /**
     * Convenience: route goo by type.
     *
     * @param type   the goo type
     * @param amount volume in microblobs
     * @return total volume accepted across all slots
     */
    public int routeGoo(GooType type, int amount) {
        return routeFluid(GooFluids.SOURCES.get(type).get(), amount);
    }

    private int distributeAcrossSlots(Fluid fluid, int amount) {
        int remaining = distributePass(fluid, amount, true);
        remaining = distributePass(fluid, remaining, false);
        return amount - remaining;
    }

    private int distributePass(Fluid fluid, int remaining, boolean existing) {
        int left = remaining;
        for (CanisterSlot slot : slots) {
            if (left <= 0) {
                break;
            }
            CanisterSlotFluidHandler handler = slot.handler();
            if (handler == null) {
                continue;
            }
            if (!isEligibleFluidHolder(fluid, existing, handler)) {
                continue;
            }
            left -= handler.insertFluid(fluid, left, false);
        }
        return left;
    }

    private static boolean isEligibleFluidHolder(Fluid fluid, boolean existing,
            CanisterSlotFluidHandler handler) {
        return handler.isEmpty()
                || (existing && !handler.isEmpty() && handler.getFluid() == fluid);
    }

    // --- Pusher lifecycle (cross-slot) ---

    /** Ticks all active slot pushers. */
    public void tickPushers() {
        for (CanisterSlot slot : slots) {
            IGasketPusher pusher = slot.pusher();
            if (pusher != null) {
                pusher.tick();
            }
        }
    }

    /** Disposes all active slot pushers (used during block removal). */
    public void disposeAllPushers() {
        for (CanisterSlot slot : slots) {
            slot.disposePusher();
        }
    }
}
