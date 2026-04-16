package com.mercuriusxeno.goo.item.fluid;

import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Single-tank item fluid handler for canisters. Accepts any registered fluid
 * (goo types, water, lava, etc.). Only one fluid type at a time - the canister
 * must be emptied before switching fluids.
 *
 * <p>Capacity scales with the Compression enchantment.</p>
 */
public class CanisterFluidHandler extends ItemAccessResourceHandler<FluidResource> {

    /**
     * Creates a handler wrapping the given canister's ItemAccess.
     *
     * @param itemAccess the item access for the canister stack
     */
    public CanisterFluidHandler(ItemAccess itemAccess) {
        super(itemAccess, 1);
    }

    /**
     * Returns the fluid resource stored in the canister, or EMPTY.
     *
     * @param item  the item resource to read from
     * @param index always 0 (single tank)
     * @return the fluid resource
     */
    @Override
    protected FluidResource getResourceFrom(ItemResource item, int index) {
        CanisterFluidContent content = readContent(item);
        return content.isEmpty()
            ? FluidResource.EMPTY
            : FluidResource.of(content.fluid());
    }

    /**
     * Returns the stored amount, clamped to int.
     *
     * @param item  the item resource to read from
     * @param index always 0 (single tank)
     * @return the amount
     */
    @Override
    protected int getAmountFrom(ItemResource item, int index) {
        return (int) Math.min(readContent(item).amount(), Integer.MAX_VALUE);
    }

    /**
     * Updates the canister's fluid content after a transfer operation.
     *
     * @param item      the item resource to update
     * @param oldAmount the previous amount
     * @param resource  the fluid resource being transferred
     * @param newAmount the new amount after transfer
     * @return the updated item resource
     */
    @Override
    protected ItemResource update(ItemResource item, int oldAmount,
            FluidResource resource, int newAmount) {
        CanisterFluidContent content = newAmount > 0
            ? new CanisterFluidContent(resource.getFluid(), newAmount)
            : CanisterFluidContent.EMPTY;
        return content.isEmpty()
            ? item.without(GooDataComponents.CANISTER_FLUID_CONTENT)
            : item.with(GooDataComponents.CANISTER_FLUID_CONTENT, content);
    }

    /**
     * Returns the full canister capacity (Compression enchant scaling).
     *
     * @param index    always 0 (single tank)
     * @param resource the fluid resource
     * @return capacity in microblobs
     */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return (int) Math.min(readCapacity(itemAccess.getResource()), Integer.MAX_VALUE);
    }

    /**
     * Accepts any non-empty fluid if the canister is empty or already holds the same fluid.
     *
     * @param index    always 0 (single tank)
     * @param resource the fluid resource to validate
     * @return true if the fluid can be inserted
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) { return false; }
        CanisterFluidContent content = readContent(itemAccess.getResource());
        return content.isEmpty() || content.fluid() == resource.getFluid();
    }

    // --- Helpers ---

    /**
     * Reads canister fluid content from the item, defaulting to EMPTY.
     * @param item the item resource to read fluid data from
     * @return the stored fluid content, or EMPTY if none
     */
    private static CanisterFluidContent readContent(ItemResource item) {
        CanisterFluidContent content = item.getComponents().get(
            GooDataComponents.CANISTER_FLUID_CONTENT.get());
        return content != null ? content : CanisterFluidContent.EMPTY;
    }

    /**
     * Reads canister capacity from the Compression enchantment.
     * @param item the item resource to read enchantments from
     * @return the canister capacity in mB based on compression level
     */
    private static long readCapacity(ItemResource item) {
        ItemEnchantments enchants = item.getComponents().getOrDefault(
            DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int compression = findCompressionLevel(enchants);
        return ContainerCapacity.canisterCapacity(compression);
    }

    /**
     * Scans enchantments for the Compression enchant and returns its level.
     * @param enchants the enchantment set to search
     * @return the compression enchantment level, or 0 if absent
     */
    private static int findCompressionLevel(ItemEnchantments enchants) {
        for (var entry : enchants.entrySet()) {
            if (entry.getKey().is(GooEnchantments.COMPRESSION)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }
}
