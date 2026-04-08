package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Capability adapter exposing a canister item's {@link GooContents} as a
 * 15-tank fluid {@link net.neoforged.neoforge.transfer.ResourceHandler}.
 * Each tank index maps to a {@link GooType} ordinal. Shared capacity
 * derived from {@link CanisterMetadata#matrices()}.
 *
 * <p>Pipe mods iterate tanks and insert/extract the correct goo type
 * at the matching index. The shared capacity ensures total volume never
 * exceeds the canister's limit.</p>
 */
public class CanisterFluidHandler extends ItemAccessResourceHandler<FluidResource> {

    private static final int TANK_COUNT = GooType.values().length;

    /**
     * Creates a handler wrapping the given canister's ItemAccess.
     *
     * @param itemAccess the item access for the canister stack
     */
    public CanisterFluidHandler(ItemAccess itemAccess) {
        super(itemAccess, TANK_COUNT);
    }

    /**
     * Returns the FluidResource for the goo type at this tank index.
     * Empty if the canister has no goo of that type.
     *
     * @param item  the item resource to read from
     * @param index the tank index (GooType ordinal)
     * @return the fluid resource, or EMPTY if none
     */
    @Override
    protected FluidResource getResourceFrom(ItemResource item, int index) {
        GooType type = typeForIndex(index);
        if (type == null) { return FluidResource.EMPTY; }
        GooContents contents = readContents(item);
        long volume = contents.getVolume(type);
        return volume > 0
            ? FluidResource.of(GooFluids.SOURCES.get(type).get())
            : FluidResource.EMPTY;
    }

    /**
     * Returns the volume of the goo type at this tank index, clamped to int.
     *
     * @param item  the item resource to read from
     * @param index the tank index (GooType ordinal)
     * @return volume clamped to int
     */
    @Override
    protected int getAmountFrom(ItemResource item, int index) {
        GooType type = typeForIndex(index);
        if (type == null) { return 0; }
        return clampToInt(readContents(item).getVolume(type));
    }

    /**
     * Updates the item's GooContents after a transfer operation.
     * Sets the volume for the matching goo type.
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
        GooContents contents = readContents(item);
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        if (type == null) { return item; }

        long existing = contents.getVolume(type);
        GooContents cleared = contents.withRemoved(type, existing);
        GooContents updated = newAmount > 0
            ? cleared.withAdded(type, newAmount)
            : cleared;

        return updated.isEmpty()
            ? item.without(GooDataComponents.GOO_CONTENTS)
            : item.with(GooDataComponents.GOO_CONTENTS, updated);
    }

    /**
     * Returns the effective capacity for this tank, accounting for shared
     * capacity. The slot's effective limit is total capacity minus volume
     * stored in all other tanks.
     *
     * @param index    the tank index (GooType ordinal)
     * @param resource the fluid resource being queried
     * @return effective capacity clamped to int
     */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        ItemResource item = itemAccess.getResource();
        long totalCapacity = readCapacity(item);
        long otherVolume = 0;
        GooContents contents = readContents(item);
        GooType[] types = GooType.values();
        for (int i = 0; i < types.length; i++) {
            if (i != index) { otherVolume += contents.getVolume(types[i]); }
        }
        return clampToInt(totalCapacity - otherVolume);
    }

    /**
     * Only the goo fluid matching this tank index is valid.
     *
     * @param index    the tank index (GooType ordinal)
     * @param resource the fluid resource to validate
     * @return true if the resource matches this tank's goo type
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) { return false; }
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        return type != null && type.ordinal() == index;
    }

    // --- Helpers ---

    /**
     * Returns the GooType for the given tank index, or null if out of range.
     *
     * @param index the tank index
     * @return the corresponding GooType, or null
     */
    private static GooType typeForIndex(int index) {
        GooType[] types = GooType.values();
        return index >= 0 && index < types.length ? types[index] : null;
    }

    /**
     * Reads GooContents from the item resource, defaulting to EMPTY.
     *
     * @param item the item resource to read from
     * @return the goo contents, never null
     */
    private static GooContents readContents(ItemResource item) {
        GooContents contents = item.getComponents().get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /**
     * Reads canister capacity from the Compression enchantment on the item.
     *
     * @param item the item resource to read from
     * @return capacity in microblobs
     */
    private static long readCapacity(ItemResource item) {
        net.minecraft.world.item.enchantment.ItemEnchantments enchants =
            item.getComponents().getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        int compression = 0;
        for (var entry : enchants.entrySet()) {
            if (entry.getKey().is(com.mercuriusxeno.goo.registry.GooEnchantments.COMPRESSION)) {
                compression = entry.getIntValue();
                break;
            }
        }
        return ContainerCapacity.canisterCapacity(compression);
    }

    /**
     * Safely narrows a long to int, clamping at Integer.MAX_VALUE.
     *
     * @param value the long value to clamp
     * @return the value as int, capped at Integer.MAX_VALUE
     */
    private static int clampToInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
