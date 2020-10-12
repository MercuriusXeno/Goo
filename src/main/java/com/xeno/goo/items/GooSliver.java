package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;

import java.util.function.Supplier;

public class GooSliver extends CrystallizedGooAbstract {
    public GooSliver(Supplier<GooFluid> f) {
        super(f, 10);
    }
}
