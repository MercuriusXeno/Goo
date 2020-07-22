package com.xeno.goop.setup;

import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultMappings {
    public static String volatileGoop = Registry.VOLATILE_GOOP.get().getRegistryName().toString();
    public static String earthenGoop = Registry.EARTHEN_GOOP.get().getRegistryName().toString();
    public static String aquaticGoop = Registry.AQUATIC_GOOP.get().getRegistryName().toString();
    public static String esotericGoop = Registry.ESOTERIC_GOOP.get().getRegistryName().toString();
    public static String floralGoop = Registry.FLORAL_GOOP.get().getRegistryName().toString();
    public static String faunalGoop = Registry.FAUNAL_GOOP.get().getRegistryName().toString();
    public static String fungalGoop = Registry.FUNGAL_GOOP.get().getRegistryName().toString();
    public static String regalGoop = Registry.REGAL_GOOP.get().getRegistryName().toString();

    public static List<GoopValueMapping> values = new ArrayList<>();
    static {
        addMapping(Items.CHARCOAL, goopValue(volatileGoop, 240));
    }

    private static GoopValue goopValue(String s, int i) {
        return new GoopValue(s, i);
    }

    private static void addMapping(Item item, GoopValue... args) {
        addMapping(item.getRegistryName().toString(), args);
    }

    private static void addMapping(String resourceLocation, GoopValue... args) {
        values.add(new GoopValueMapping(resourceLocation, new ArrayList<>(Arrays.asList(args))));
    }

}
