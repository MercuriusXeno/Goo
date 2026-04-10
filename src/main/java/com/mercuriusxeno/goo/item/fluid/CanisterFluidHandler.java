package com.mercuriusxeno.goo.item.fluid;

import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Capability adapter exposing a canister item's {@link com.mercuriusxeno.goo.item.GooContents}
 * as a 15-tank fluid {@link net.neoforged.neoforge.transfer.ResourceHandler}.
 * Each tank index maps to a {@link com.mercuriusxeno.goo.GooType} ordinal. Shared capacity
 * derived from {@link com.mercuriusxeno.goo.item.CanisterMetadata#matrices()}.
 *
 * <p>Pipe mods iterate tanks and insert/extract the correct goo type
 * at the matching index. The shared capacity ensures total volume never
 * exceeds the canister's limit.</p>
 */
public class CanisterFluidHandler extends GooItemFluidHandler {

    /**
     * Creates a handler wrapping the given canister's ItemAccess.
     *
     * @param itemAccess the item access for the canister stack
     */
    public CanisterFluidHandler(ItemAccess itemAccess) {
        super(itemAccess);
    }

    /**
     * Canister capacity scales with the Compression enchantment level.
     *
     * @param item the item resource to read enchantments from
     * @return canister capacity in microblobs
     */
    @Override
    protected long getContainerCapacity(ItemResource item) {
        return readCapacity(item);
    }

    // --- Canister-specific helpers ---

    /**
     * Reads canister capacity from the Compression enchantment on the item.
     *
     * @param item the item resource to read from
     * @return capacity in microblobs
     */
    private static long readCapacity(ItemResource item) {
        ItemEnchantments enchants = item.getComponents().getOrDefault(
            DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int compression = findCompressionLevel(enchants);
        return ContainerCapacity.canisterCapacity(compression);
    }

    /**
     * Scans enchantments for the Compression enchant and returns its level.
     *
     * @param enchants the item's enchantment set to scan
     * @return the Compression enchantment level, or 0 if not present
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
