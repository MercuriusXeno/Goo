package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;

import java.util.function.Supplier;

public class GooShard extends CrystallizedGooAbstract {
    public GooShard(Supplier<GooFluid> f) {
        super(f, 100);
    }
}
