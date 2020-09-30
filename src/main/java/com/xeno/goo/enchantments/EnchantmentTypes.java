package com.xeno.goo.enchantments;

import com.xeno.goo.items.Basin;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.enchantment.EnchantmentType;

public class EnchantmentTypes
{
    public static final EnchantmentType VALID_FOR_CONTAINMENT = EnchantmentType.create("holding", (i) ->
            i instanceof Basin || i instanceof Gauntlet || i.equals(ItemsRegistry.GooBulb.get()));
}
