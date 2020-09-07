package com.xeno.goo.library;

import net.minecraft.fluid.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;

public class MixerRecipe
{
    private Map<Fluid, Integer> inputs = new HashMap<>();
    private FluidStack output;

    public MixerRecipe(FluidStack output, FluidStack...  inputs)
    {
        this.output = output;
        int inputCount = 0;
        for(FluidStack input : inputs) {
            if (inputCount == 1) {
                break;
            }
            this.inputs.put(input.getFluid(), input.getAmount());
            inputCount++;
        }
    }

    public FluidStack output() {
        return this.output;
    }

    public Map<Fluid, Integer> inputs() {
        return this.inputs;
    }
}
