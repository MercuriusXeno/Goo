package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class Gauntlet extends Item
{
    public Gauntlet()
    {
        super(
                new Item.Properties()
                .maxStackSize(1)
                .isBurnable()
                .group(GooMod.ITEM_GROUP)
        );
    }
}