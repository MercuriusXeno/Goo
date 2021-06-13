package com.xeno.goo.aequivaleo;

import com.xeno.goo.setup.Registry;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Supplier;

public class GooValue
{
    private String fluidResourceLocation;
    private double amount;
    //private Supplier<FluidStack> fluidStackSupplier;

    public GooValue(String goo, double amount) {
        this.fluidResourceLocation = goo;
        this.amount = amount;
        // this.fluidStackSupplier = () -> new FluidStack(Registry.getFluid(goo).getFluid(), (int)Math.floor(amount));
    }

    public String getFluidResourceLocation() {
        return fluidResourceLocation;
    }

    public double amount() {
        return amount;
    }

//	public FluidStack fluidStack() {
//        return fluidStackSupplier.get();
//	}
}
