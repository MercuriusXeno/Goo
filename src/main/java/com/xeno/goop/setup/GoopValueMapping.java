package com.xeno.goop.setup;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GoopValueMapping {
    private String itemResourceLocation;
    private List<GoopValue> goopValues;
    private boolean isDenied;

    public GoopValueMapping(String itemResourceLocation, List<GoopValue> goopValues) {
        this.itemResourceLocation = itemResourceLocation;
        this.goopValues = goopValues;
        this.isDenied = false;
    }

    public GoopValueMapping(String itemResourceLocation) {
        this.itemResourceLocation = itemResourceLocation;
        this.goopValues = new ArrayList<>();
        this.isDenied = true;
    }

    public boolean getIsDenied() {return this.isDenied; }

    public List<GoopValue> getGoopValues() {
        return this.goopValues;
    }

    public String getItemResourceLocation() {
        return this.itemResourceLocation;
    }

    public static Comparator<GoopValueMapping> registryNameSansNamespaceComparator = (s1, s2) -> {
        int namespaceIndex1 = s1.getItemResourceLocation().indexOf(':');
        int namespaceIndex2 = s2.getItemResourceLocation().indexOf(':');
        String registryName1 = s1.getItemResourceLocation().substring(namespaceIndex1);
        String registryName2 = s2.getItemResourceLocation().substring(namespaceIndex2);

        return registryName1.compareTo(registryName2);
    };

    public List<GoopValue> subtract(String resourceLocation, List<GoopValue> subtractBy) {
        // clone the goop values from this object.
        List<GoopValue> result = new ArrayList<>(this.goopValues);
        try {
            for (GoopValue v : subtractBy) {
                GoopValue resultValue = result.stream().filter(r -> r.getFluidResourceLocation().equals(v.getFluidResourceLocation())).findFirst().orElseThrow(() -> new Exception("Attempted to derive mapping that results in negative quantity."));
                resultValue.setAmount(resultValue.getAmount() - v.getAmount());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<GoopValue> add(String resourceLocation, List<GoopValue> addBy) {
        // clone the goop values from this object.
        List<GoopValue> result = new ArrayList<>(this.goopValues);
        for (GoopValue v : addBy) {
            // can't find a match, so add a new type
            if (result.stream().noneMatch(r -> r.getFluidResourceLocation().equals(v.getFluidResourceLocation()))) {
                result.add(v.copy());
            } else {
                GoopValue resultValue = result.stream().filter(r -> r.getFluidResourceLocation().equals(v.getFluidResourceLocation())).findFirst().orElse(new GoopValue(v.getFluidResourceLocation(), 0));
                resultValue.setAmount(resultValue.getAmount() + v.getAmount());
            }
        }
        return result;
    }

    public List<GoopValue> multiply(int i) {
        List<GoopValue> result = new ArrayList<>(this.goopValues);
        for(GoopValue v : result) {
            v.setAmount(v.getAmount() * i);
        }
        return result;
    }


    public List<GoopValue> divide(int i) {
        List<GoopValue> result = new ArrayList<>(this.goopValues);
        try {
            for (GoopValue v : result) {
                if (v.getAmount() % i != 0) {
                    throw new Exception("Attempted to create an equivalency with uneven division!");
                }
                v.setAmount(v.getAmount() / i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
