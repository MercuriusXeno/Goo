package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;
import net.minecraft.item.Item;

import java.util.function.Supplier;

public class GooCrystal extends CrystallizedGooAbstract {
    public GooCrystal(Supplier<GooFluid> f, Supplier<Item> source) {
        super(f, source,1000);
    }
}
