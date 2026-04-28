package com.mercuriusxeno.goo.block.hub;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Block-level fluid handler for the Hub. Presents one virtual tank per
 * canister slot (up to {@link HubBlockEntity#MAX_CANISTERS}). Each tank
 * reflects the single-type fluid in the corresponding canister.
 *
 * <p>This is a read-through/write-through adapter: no data duplication.
 * All state lives on the Hub's internal canister ItemStacks.</p>
 */
public class HubFluidHandler implements ResourceHandler<FluidResource> {

    private final HubBlockEntity hub;

    /**
     * Creates a handler backed by the given hub.
     *
     * @param hub the hub block entity
     */
    public HubFluidHandler(HubBlockEntity hub) {
        this.hub = hub;
    }

    /**
     * Returns one tank per canister slot.
     *
     * @return the tank count
     */
    @Override
    public int size() {
        return HubBlockEntity.MAX_CANISTERS;
    }

    /**
     * Returns the fluid in the canister at the given slot.
     *
     * @param index the tank index (canister slot)
     * @return the fluid resource
     */
    @Override
    public FluidResource getResource(int index) {
        CanisterFluidContent content = getSlotContent(index);
        return content.isEmpty()
                ? FluidResource.EMPTY
                : FluidResource.of(content.fluid());
    }

    /**
     * Returns the volume in the canister at the given slot.
     *
     * @param index the tank index
     * @return the amount
     */
    @Override
    public long getAmountAsLong(int index) {
        return getSlotContent(index).amount();
    }

    /**
     * Returns the capacity of the canister at the given slot.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return the capacity
     */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        ItemStack stack = hub.getCanister(index);
        if (stack.isEmpty()) {
            return 0;
        }
        return ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(stack));
    }

    /**
     * Accepts any fluid if the canister slot is empty or already holds it.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return true if valid
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        ItemStack stack = hub.getCanister(index);
        if (stack.isEmpty()) {
            return false;
        }
        CanisterFluidContent content = CanisterItem.getFluidContent(stack);
        return content.isEmpty() || content.fluid() == resource.getFluid();
    }

    /**
     * Inserts fluid by routing to matching or empty canister slots.
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      volume in microblobs
     * @param transaction the transaction context
     * @return the amount inserted
     */
    @Override
    public int insert(int index, FluidResource resource, int amount,
                      TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        Fluid fluid = resource.getFluid();
        return Math.min(
                hub.containerState().routeFluid(fluid, amount),
                Integer.MAX_VALUE);
    }

    /**
     * Extracts fluid by scanning canister slots for the requested type.
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      volume in microblobs
     * @param transaction the transaction context
     * @return the amount extracted
     */
    @Override
    public int extract(int index, FluidResource resource, int amount,
                       TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        Fluid fluid = resource.getFluid();
        return extractFromCanisters(fluid, amount);
    }

    /**
     * Scans hub canisters and extracts the requested fluid.
     *
     * @param fluid  the fluid type to extract
     * @param amount the maximum amount to extract in mB
     * @return the total amount actually extracted
     */
    private int extractFromCanisters(Fluid fluid, int amount) {
        int remaining = scanAndExtract(fluid, amount);
        int totalExtracted = amount - remaining;
        if (totalExtracted > 0) {
            BlockEntitySync.markDirtyAndSync(hub);
        }
        return totalExtracted;
    }

    /**
     * Iterates hub canister slots and removes fluid of the requested type.
     *
     * @param fluid  the fluid type to remove from canisters
     * @param amount the maximum amount to remove in mB
     * @return the remaining amount that could not be extracted
     */
    private int scanAndExtract(Fluid fluid, int amount) {
        int remaining = amount;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS && remaining > 0; i++) {
            ItemStack stack = hub.getCanister(i);
            if (stack.isEmpty()) {
                continue;
            }
            remaining -= CanisterItem.removeFluid(stack, fluid, remaining);
        }
        return remaining;
    }

    // --- Helpers ---

    /**
     * Reads the fluid content from the canister at the given slot.
     *
     * @param index the hub canister slot index to read
     * @return the fluid content of that slot, or EMPTY if vacant
     */
    private CanisterFluidContent getSlotContent(int index) {
        ItemStack stack = hub.getCanister(index);
        return stack.isEmpty() ? CanisterFluidContent.EMPTY : CanisterItem.getFluidContent(stack);
    }
}
