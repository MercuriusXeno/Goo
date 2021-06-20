package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class StygianWeepings extends Item {
    public StygianWeepings() {
        super(
                new Properties()
                        .maxStackSize(64)
                .group(GooMod.ITEM_GROUP)
        );
    }
}
