package com.xeno.goop.setup;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public class GoopValueMapping {
    private String itemResourceLocation;
    private List<GoopValue> goopValues;

    public GoopValueMapping(String itemResourceLocation, List<GoopValue> goopValues) {
        this.itemResourceLocation = itemResourceLocation;
        this.goopValues = goopValues;
    }

    public GoopValueMapping(Item item, List<GoopValue> goopValues) {
        this.itemResourceLocation = item.getRegistryName().toString();
        this.goopValues = goopValues;
    }

    public void setItemResourceLocation(ResourceLocation resLoc) {
        this.itemResourceLocation = resLoc.toString();
    }

    public void setItemResourceLocation(String resLocString) {
        this.itemResourceLocation = resLocString;
    }

    public void setGoopValues(List<GoopValue> goopValues) {
        this.goopValues = goopValues;
    }

    public List<GoopValue> getGoopValues() {
        return this.goopValues;
    }

    public String getItemResourceLocation() {
        return this.itemResourceLocation;
    }
}
