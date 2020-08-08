package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class Crucible extends Item
{
    public Crucible()
    {
        super(new Item.Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP));
    }
}
