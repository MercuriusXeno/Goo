package com.xeno.goo.library;

import net.minecraftforge.fluids.FluidStack;

public class DegraderRecipe
{
    private final FluidStack input;
    private final FluidStack output;

    public DegraderRecipe(FluidStack output, FluidStack input) {
        this.input = input;
        this.output = output;
    }

    public FluidStack input() {
        return this.input;
    }

    public FluidStack output() {
        return this.output;
    }
}
