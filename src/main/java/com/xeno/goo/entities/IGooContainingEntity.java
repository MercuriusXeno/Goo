package com.xeno.goo.entities;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public interface IGooContainingEntity extends IFluidHandler {
    FluidStack goo();
}
