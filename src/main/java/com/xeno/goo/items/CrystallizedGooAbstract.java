package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;

import java.util.function.Supplier;

public class CrystallizedGooAbstract extends Item {
    private final Supplier<GooFluid> gooType;
    private final int gooValue;
    public CrystallizedGooAbstract(Supplier<GooFluid> gooType, int value) {
        super(new Properties().group(GooMod.ITEM_GROUP));
        this.gooType = gooType;
        this.gooValue = value;
    }

    public Fluid gooType() {
        return gooType.get();
    }

    public int amount() {
        return gooValue;
    }
}
