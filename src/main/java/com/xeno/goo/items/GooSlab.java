package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;

import java.util.function.Supplier;

public class GooSlab extends CrystallizedGooAbstract {
    public GooSlab(Supplier<GooFluid> f) {
        super(f, 100000);
    }
}
