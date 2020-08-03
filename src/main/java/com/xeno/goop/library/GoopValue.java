package com.xeno.goop.library;

public class GoopValue {
    private String fluidResourceLocation;
    private double amount;

    public GoopValue(String goop, double amount) {
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
