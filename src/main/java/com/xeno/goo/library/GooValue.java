package com.xeno.goo.library;

public class GooValue
{
    private String fluidResourceLocation;
    private double amount;

    public GooValue(String goop, double amount) {
        this.fluidResourceLocation = goop;
        this.amount = amount; //Helper.truncateValue(amount);
    }

    public String getFluidResourceLocation() {
        return fluidResourceLocation;
    }

    public double getAmount() {
        return amount;
    }
}
