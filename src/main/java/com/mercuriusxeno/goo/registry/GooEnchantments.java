package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * ResourceKey constants and helpers for data-driven enchantments.
 * The actual enchantment definitions live in JSON at
 * data/goo/enchantment/.
 */
public final class GooEnchantments {

    /** Compression: multiplies container capacity by 2^level (levels 1-5). */
    public static final ResourceKey<Enchantment> COMPRESSION =
        ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Goo.MODID, "compression"));

    private GooEnchantments() {}

    /**
     * Reads the Compression enchantment level from an ItemStack without
     * needing registry access. Uses Holder.is(ResourceKey) for matching.
     *
     * @param stack the item stack to inspect
     * @return the compression level (0 if absent)
     */
    public static int getCompressionLevel(ItemStack stack) {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            if (entry.getKey().is(COMPRESSION)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }
}
