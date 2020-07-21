package com.xeno.goop.setup;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.xeno.goop.GoopMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

@Mod.EventBusSubscriber
public class Config {
    public static final Path CONFIG_DIRECTORY;

    // This is the maximum work any machine (bulb or otherwise) can do in a single tick, irrespective of the number of bulbs connected.
    public static int DEFAULT_GOOP_TRANSFER_RATE = 15;
    public static ForgeConfigSpec.IntValue GOOP_MAX_TRANSFER_RATE;

    public static int getTransferRate() {
        return GOOP_MAX_TRANSFER_RATE.get();
    }

    // default bulb capacity, at the time of writing set to 1e8
    public static int DEFAULT_GOOP_BULB_CAPACITY = 100000;
    public static ForgeConfigSpec.IntValue GOOP_BULB_TOTAL_CAPACITY;

    public static int getGoopBulbCapacity() {
        return GOOP_BULB_TOTAL_CAPACITY.get();
    }

    public static ForgeConfigSpec COMMON_CONFIG;

    public static final String CATEGORY_MACHINES = "machines";

    private static ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();

    static {
        CONFIG_DIRECTORY = FMLPaths.getOrCreateGameRelativePath(FMLPaths.CONFIGDIR.get().resolve(GoopMod.MOD_ID), GoopMod.MOD_ID);
        setupGeneralMachineConfig();

        COMMON_CONFIG = COMMON_BUILDER.build();
    }

    private static void setupGeneralMachineConfig() {
        COMMON_BUILDER.comment("Machine configs").push(CATEGORY_MACHINES);
        GOOP_MAX_TRANSFER_RATE = COMMON_BUILDER.comment("Maximum total transfer rate of bulbs and machines per tick")
                .defineInRange("maxTransferRate", DEFAULT_GOOP_TRANSFER_RATE, 0, Integer.MAX_VALUE);
        GOOP_BULB_TOTAL_CAPACITY = COMMON_BUILDER.comment("Maximum total amount of goop in a single bulb")
                .defineInRange("maxBulbCapacity", DEFAULT_GOOP_BULB_CAPACITY, 0, Integer.MAX_VALUE);
        COMMON_BUILDER.pop();
    }

    public static void loadConfig(ForgeConfigSpec spec, Path path)
    {
        final CommentedFileConfig configData = CommentedFileConfig.builder(path)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();

        configData.load();
        spec.setConfig(configData);
    }
}
