package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;
import net.minecraft.item.Item;

import java.util.function.Supplier;

public class GooChunk extends CrystallizedGooAbstract {
    public GooChunk(Supplier<GooFluid> f, Supplier<Item> source) {
        super(f, source,10000);
    }
}
