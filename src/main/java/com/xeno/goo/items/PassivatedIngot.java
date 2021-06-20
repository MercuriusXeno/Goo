package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class PassivatedIngot extends Item {
    public PassivatedIngot() {
        super(
                new Properties()
                        .maxStackSize(64)
                .group(GooMod.ITEM_GROUP)
        );
    }
}
