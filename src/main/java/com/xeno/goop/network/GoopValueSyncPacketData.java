package com.xeno.goop.network;

import com.xeno.goop.setup.GoopValue;
import net.minecraft.item.Item;

public class GoopValueSyncPacketData {
    private Item item;
    private GoopValue[] values;
    public GoopValueSyncPacketData(Item item, GoopValue[] values) {
        this.item = item;
        this.values = values;
    }

    public Item getItem() {
        return item;
    }

    public GoopValue[] getValues() {
        return values;
    }
}
