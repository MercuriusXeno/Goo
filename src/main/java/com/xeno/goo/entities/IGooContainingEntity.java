package com.xeno.goo.entities;

import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;

public interface IGooContainingEntity extends ICapabilityProvider {
    FluidStack goo();
}
