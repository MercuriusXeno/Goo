package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.SlottedContainerState;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Block-level fluid handler for the Hub. Presents 15 virtual tanks
 * (one per {@link GooType} ordinal) that aggregate volumes across all
 * internal canister items. Insert routes via {@link SlottedContainerState#routeGoo},
 * extract scans canisters for the first match.
 *
 * <p>This is a read-through/write-through adapter: no data duplication.
 * All state lives on the Hub's internal canister ItemStacks.</p>
 */
public class HubFluidHandler implements ResourceHandler<FluidResource> {

    private static final int TANK_COUNT = GooType.values().length;

    private final HubBlockEntity hub;

    /** Creates a handler backed by the given hub.
     *
     * @param hub the hub block entity
     */
    public HubFluidHandler(HubBlockEntity hub) {
        this.hub = hub;
    }

    /** Returns 15 (one tank per goo type).
     *
     * @return the integer value
     */
    @Override
    public int size() {
        return TANK_COUNT;
    }

    /** Returns the FluidResource for the goo type at this index. Empty if no goo of that type.
     *
     * @param index the tank index
     * @return the resource
     */
    @Override
    public FluidResource getResource(int index) {
        GooType type = typeForIndex(index);
        if (type == null) { return FluidResource.EMPTY; }
        long volume = aggregateVolume(type);
        return volume > 0
            ? FluidResource.of(GooFluids.SOURCES.get(type).get())
            : FluidResource.EMPTY;
    }

    /** Returns the aggregate volume of the given type across all canisters.
     *
     * @param index the tank index
     * @return the amount as long
     */
    @Override
    public long getAmountAsLong(int index) {
        GooType type = typeForIndex(index);
        return type != null ? aggregateVolume(type) : 0L;
    }

    /** Returns total remaining capacity across all canisters (shared per type).
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return the capacity as long
     */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return aggregateCapacity();
    }

    /** Only the goo fluid matching this tank index is valid.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return true if valid
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) { return false; }
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        return type != null && type.ordinal() == index;
    }

    /**
     * Inserts goo by routing to the first canister with remaining capacity.
     * Transaction context is accepted but not used for rollback (the Hub's
     * routeGoo is not transactional). This is acceptable for ROT-001.
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      volume in microblobs
     * @param transaction the transaction context
     * @return the integer value
     */
    @Override
    public int insert(int index, FluidResource resource, int amount,
            TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) { return 0; }
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        if (type == null || type.ordinal() != index) { return 0; }
        return (int) Math.min(hub.containerState().routeGoo(type, amount), Integer.MAX_VALUE);
    }

    /**
     * Extracts goo by scanning canisters for the requested type.
     * Takes from the first canister with available volume.
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      volume in microblobs
     * @param transaction the transaction context
     * @return the integer value
     */
    @Override
    public int extract(int index, FluidResource resource, int amount,
            TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) { return 0; }
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        if (type == null || type.ordinal() != index) { return 0; }
        return extractFromCanisters(type, amount);
    }

    /** Scans hub canisters and extracts the requested type, syncing if any was taken.
     *
     * @param type   the goo type to extract
     * @param amount the maximum amount to extract
     * @return the total amount extracted
     */
    private int extractFromCanisters(GooType type, int amount) {
        int remaining = scanAndExtract(type, amount);
        int totalExtracted = amount - remaining;
        if (totalExtracted > 0) { BlockEntitySync.markDirtyAndSync(hub); }
        return totalExtracted;
    }

    /** Iterates hub canister slots and removes goo of the requested type.
     *
     * @param type   the goo type to extract
     * @param amount the maximum amount to extract
     * @return the amount remaining after extraction
     */
    private int scanAndExtract(GooType type, int amount) {
        int remaining = amount;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS && remaining > 0; i++) {
            ItemStack stack = hub.getCanister(i);
            if (stack.isEmpty()) { continue; }
            remaining -= (int) CanisterItem.removeGoo(stack, type, remaining);
        }
        return remaining;
    }

    // --- Helpers ---

    /** Returns the GooType for the given tank index, or null if out of range.
     *
     * @param index the tank index
     * @return the goo type, or null
     */
    private static GooType typeForIndex(int index) {
        GooType[] types = GooType.values();
        return index >= 0 && index < types.length ? types[index] : null;
    }

    /** Sums the volume of a single type across all canisters.
     *
     * @param type the goo type
     * @return the long value
     */
    private long aggregateVolume(GooType type) {
        long total = 0;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            ItemStack stack = hub.getCanister(i);
            if (stack.isEmpty()) { continue; }
            total += CanisterItem.getGooContents(stack).getVolume(type);
        }
        return total;
    }

    /** Sums total remaining capacity across all occupied canisters.
     *
     * @return the long value
     */
    private long aggregateCapacity() {
        long total = 0;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            ItemStack stack = hub.getCanister(i);
            if (stack.isEmpty()) { continue; }
            total += ContainerCapacity.canisterCapacity(
                com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(stack));
        }
        return total;
    }
}
