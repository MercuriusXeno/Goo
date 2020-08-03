package com.xeno.goop.fluids;

import net.minecraft.item.Item;
import net.minecraftforge.fluids.FluidAttributes;

import java.util.function.Supplier;

public class VitalGoop extends GoopBase {
    public VitalGoop(Supplier<? extends Item> bucket, FluidAttributes.Builder builder) {
        super(bucket, builder);
    }
}
