package com.xeno.goo.library;

import jei.GooIngredient;
import net.minecraftforge.fluids.FluidStack;

public class CrucibleRecipe
{
    private final FluidStack input;
    private final FluidStack output;

    public CrucibleRecipe(FluidStack output, FluidStack input) {
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
