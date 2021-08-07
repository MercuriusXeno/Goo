package com.xeno.goo.aequivaleo;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Supplier;

public class GooValue
{
    private String fluidResourceLocation;
    private int amount;
    //private Supplier<FluidStack> fluidStackSupplier;

    public GooValue(GooFluid fluid, int amount) {
        this.fluidResourceLocation = fluid.getFluid().getRegistryName().toString();
        this.amount = amount;
    }

    public GooValue(String goo, int amount) {
        this.fluidResourceLocation = goo;
        this.amount = amount;
        // this.fluidStackSupplier = () -> new FluidStack(Registry.getFluid(goo).getFluid(), (int)Math.floor(amount));
    }

    public String getFluidResourceLocation() {
        return fluidResourceLocation;
    }

    public int amount() {
        return amount;
    }

//	public FluidStack fluidStack() {
//        return fluidStackSupplier.get();
//	}
}
