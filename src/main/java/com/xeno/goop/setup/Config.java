package com.xeno.goop.setup;

import com.xeno.goop.GoopMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import java.util.Dictionary;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Config {
    public static ForgeConfigSpec SERVER_CONFIG;
    public static final String CATEGORY_MAPPINGS = "mappings";
    ForgeConfigSpec.ConfigValue<Dictionary<String, Integer>[]> GOOP_VALUE_MAPPINGS;

    static {

        ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

        SERVER_BUILDER.comment("Power settings").push(CATEGORY_MAPPINGS);
        SERVER_BUILDER.pop();

        SERVER_CONFIG = SERVER_BUILDER.build();
    }
}
