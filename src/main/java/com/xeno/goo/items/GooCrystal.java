package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;

import java.util.function.Supplier;

public class GooCrystal extends CrystallizedGooAbstract {
    public GooCrystal(Supplier<GooFluid> f) {
        super(f, 1000);
    }
}
