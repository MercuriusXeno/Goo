package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;

public class ComboGauntlet extends GooHolder
{
    public ComboGauntlet()
    {
        super(2, GooDrainBehavior.HELD_CHARGE);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        return tryGooDrainBehavior(stack, context);
    }

    @Override
    public int baseCapacity()
    {
        return GooMod.config.comboGauntletBaseCapacity();
    }

    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.comboGauntletHoldingMultipler();
    }
}
