package com.xeno.goo.entities;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

public interface IGooContainingEntity extends IFluidHandler {
    List<FluidStack> goo();
}
