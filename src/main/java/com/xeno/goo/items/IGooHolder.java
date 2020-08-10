package com.xeno.goo.items;

import net.minecraft.item.ItemStack;

public interface IGooHolder
{
    int tanks();

    int capacity(ItemStack stack);

    GooDrainBehavior behavior();

    int baseCapacity();

    int holdingMultiplier();

    int enchantmentFactor(ItemStack stack);
}
