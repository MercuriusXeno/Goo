package com.xeno.goo.library;

public class GooValue
{
    private String fluidResourceLocation;
    private double amount;

    public GooValue(String goo, double amount) {
        this.fluidResourceLocation = goo;
        this.amount = amount; //Helper.truncateValue(amount);
    }

    public String getFluidResourceLocation() {
        return fluidResourceLocation;
    }

    public double amount() {
        return amount;
    }
}
