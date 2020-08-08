package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class Gauntlet extends Item
{
    public Gauntlet()
    {
        super(new Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP));
    }
}
