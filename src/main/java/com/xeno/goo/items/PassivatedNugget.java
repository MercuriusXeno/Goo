package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class PassivatedNugget extends Item {
    public PassivatedNugget() {
        super(
                new Properties()
                        .maxStackSize(64)
                .group(GooMod.ITEM_GROUP)
        );
    }
}
