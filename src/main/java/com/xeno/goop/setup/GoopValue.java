package com.xeno.goop.setup;

import net.minecraft.fluid.Fluid;

public class GoopValue {
    private String fluidResourceLocation;
    private int amount;

    public GoopValue(String goop, int amount) {
        this.fluidResourceLocation = goop;
        this.amount = amount;
    }

    public String getFluidResourceLocation() {
        return fluidResourceLocation;
    }

    public int getAmount() {
        return amount;
    }
}
