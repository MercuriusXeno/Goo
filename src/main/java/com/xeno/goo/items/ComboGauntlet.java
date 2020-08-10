package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class ComboGauntlet extends Item implements IGooHolder
{
    public ComboGauntlet()
    {
        super(new Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP)
                .maxDamage(2048));
    }

    @Override
    public int tanks()
    {
        return 2;
    }
}
