package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.item.Item;

public class NetheriteAsh extends Item {
    public NetheriteAsh() {
        super(
                new Properties()
                        .maxStackSize(64)
                .group(GooMod.ITEM_GROUP)
        );
    }
}
