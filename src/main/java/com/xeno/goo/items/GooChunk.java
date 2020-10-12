package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;

import java.util.function.Supplier;

public class GooChunk extends CrystallizedGooAbstract {
    public GooChunk(Supplier<GooFluid> f) {
        super(f, 10000);
    }
}
