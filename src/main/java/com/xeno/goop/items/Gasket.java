package com.xeno.goop.items;

import com.xeno.goop.setup.CommonSetup;
import net.minecraft.item.Item;

public class Gasket extends Item {
    public Gasket() {
        super(
                new Item.Properties()
                        .maxStackSize(64)
                .group(CommonSetup.ITEM_GROUP)
        );
    }
}
