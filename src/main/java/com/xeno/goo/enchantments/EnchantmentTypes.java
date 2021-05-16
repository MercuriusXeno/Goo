package com.xeno.goo.enchantments;

import com.xeno.goo.items.Basin;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.ItemsRegistry;
import net.minecraft.enchantment.EnchantmentType;

public class EnchantmentTypes
{
    public static final EnchantmentType VALID_FOR_CONTAINMENT = EnchantmentType.create("valid_for_containment", (i) ->
            i instanceof Basin || i instanceof Gauntlet || i.equals(ItemsRegistry.GOO_BULB.get()));
}
