package com.xeno.goop.items;

import com.xeno.goop.setup.ModSetup;
import net.minecraft.item.Item;

public class Gasket extends Item {
    public Gasket() {
        super(
                new Item.Properties()
                        .maxStackSize(64)
                .group(ModSetup.ITEM_GROUP)
        );
    }
}
