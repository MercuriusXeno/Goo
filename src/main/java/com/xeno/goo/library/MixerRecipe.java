package com.xeno.goo.library;

import net.minecraftforge.fluids.FluidStack;

import java.util.*;

public class MixerRecipe
{
    private List<FluidStack> inputs = new ArrayList<>();
    private FluidStack output;

    public MixerRecipe(FluidStack output, FluidStack...  inputs)
    {
        this.output = output;
        this.inputs.addAll(Arrays.asList(inputs));
    }

    public FluidStack output() {
        return this.output;
    }

    public List<FluidStack> inputs() {
        return this.inputs;
    }
}
