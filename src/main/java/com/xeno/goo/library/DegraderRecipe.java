package com.xeno.goo.library;

import net.minecraftforge.fluids.FluidStack;

public class DegraderRecipe
{
    private final FluidStack input;
    private final FluidStack output;
    private final FluidStack fuel;

    public DegraderRecipe(FluidStack output, FluidStack input, FluidStack fuel) {
        this.input = input;
        this.output = output;
        this.fuel = fuel;
    }

    public FluidStack input() {
        return this.input;
    }

    public FluidStack output() {
        return this.output;
    }

    public FluidStack fuel() { return this.fuel; }
}
