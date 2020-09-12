package com.xeno.goo.enchantments;

import com.xeno.goo.setup.Registry;
import net.minecraft.enchantment.EnchantmentType;

public class EnchantmentTypes
{
    public static final EnchantmentType VALID_FOR_HOLDING = EnchantmentType.create("holding", (i) ->
            i == Registry.GAUNTLET.get() || i == Registry.BASIN.get() || i == Registry.GOO_BULB_ITEM.get());

    public static final EnchantmentType VALID_FOR_GEOMANCY = EnchantmentType.create("geomancy", (i) ->
            i == Registry.GAUNTLET.get());
}
