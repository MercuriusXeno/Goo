package com.xeno.goop.fluids;

import net.minecraft.item.Item;
import net.minecraftforge.fluids.FluidAttributes;

import java.util.function.Supplier;

public class AquaticGoop extends GoopBase {
    public AquaticGoop(Supplier<? extends Item> bucket, FluidAttributes.Builder builder) {
        super(bucket, builder);
    }
}
