package com.xeno.goop.setup;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import java.util.Dictionary;

@Mod.EventBusSubscriber
public class Config {
    public static int GOOP_BULB_CAPACITY = 2000000000;

    public static double getGoopLogScale() {
        return Math.pow(GOOP_BULB_CAPACITY, (1d / 16d));
    }

    public static ForgeConfigSpec SERVER_CONFIG;
    public static final String CATEGORY_MAPPINGS = "mappings";
    ForgeConfigSpec.ConfigValue<Dictionary<String, Integer>[]> GOOP_VALUE_MAPPINGS;

    static {

        ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

        SERVER_BUILDER.comment("Value mappings").push(CATEGORY_MAPPINGS);
        SERVER_BUILDER.pop();

        SERVER_CONFIG = SERVER_BUILDER.build();
    }
}
