package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class MobiusCrucible extends Item
{
    public MobiusCrucible()
    {
        super(new Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP));
    }
}
