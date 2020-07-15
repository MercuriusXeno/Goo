package com.xeno.goop.setup;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import java.nio.file.Path;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber
public class Config {

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

    // format here is String (name of object), List of Map<String, Integer> is Goop Registry Paths and Integer amounts in mB.
    public static ForgeConfigSpec.ConfigValue<Map<String, List<Map<String, Integer>>>> GOOP_ITEM_VALUES;

    public static ForgeConfigSpec COMMON_CONFIG;

    public static final String CATEGORY_MACHINES = "machines";

    public static final String CATEGORY_MAPPINGS = "baselineMappings";

    public static final String DERIVED_MAPPINGS = "derivedMappings";

    public static final String DENIED_MAPPINGS = "deniedMappings";

    private static ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();

    static {
        setupGeneralMachineConfig();
        setupValueMappingConfig();
        setupDerivedMappingConfig();
        setupDeniedMappingConfig();

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

    // establish baseline values for items which aren't the product of a recipe output, or are but have explicit specifications by design.
    private static void setupValueMappingConfig() {
        COMMON_BUILDER.comment("Baseline value mappings").push(CATEGORY_MAPPINGS);
        COMMON_BUILDER.pop();
    }

    // establish derivative values based on recipe outputs.
    private static void setupDerivedMappingConfig() {
        COMMON_BUILDER.comment("Derived value mappings").push(DERIVED_MAPPINGS);
        COMMON_BUILDER.pop();
    }

    private static void setupDeniedMappingConfig() {
        COMMON_BUILDER.comment("Denied value mappings").push(DENIED_MAPPINGS);
        COMMON_BUILDER.pop();
    }

    public static void loadConfig(ForgeConfigSpec spec, Path path) {

        final CommentedFileConfig configData = CommentedFileConfig.builder(path)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();

        configData.load();
        spec.setConfig(configData);
    }

    @SubscribeEvent
    public static void onLoad(final ModConfig.Loading configEvent) {
        // not sure what kind of listening conditions need to go here.
        // the goal is to validate all items have values, and ones that don't are supposed to not have them
        // also validating that recipe outputs are consistent with inputs, or have explicit overrides/transformations in the baseline.
        ValueValidation.validateMappings();
    }

    @SubscribeEvent
    public static void onReload(final ModConfig.Reloading configEvent) {
        // NOOP
    }
}
