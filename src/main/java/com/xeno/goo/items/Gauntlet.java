package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class Gauntlet extends Item implements IGooHolder
{
    public Gauntlet()
    {
        super(new Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP)
                .maxDamage(512));
    }

    @Override
    public int tanks()
    {
        return 1;
    }
}
