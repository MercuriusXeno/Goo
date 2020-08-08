package com.xeno.goo.setup;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.xeno.goo.GooMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

@Mod.EventBusSubscriber
public class ServerConfiguration
{
    // config types and builders
    public ForgeConfigSpec server;
    private ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();

    // machine config values
    private ForgeConfigSpec.IntValue GOOP_MAX_TRANSFER_RATE;
    public int goopTransferRate() { return GOOP_MAX_TRANSFER_RATE.get(); }

    private ForgeConfigSpec.IntValue GOOP_MAX_PROCESSING_RATE;
    public int goopProcessingRate() { return GOOP_MAX_PROCESSING_RATE.get(); }

    private ForgeConfigSpec.IntValue GOOP_BULB_TOTAL_CAPACITY;
    public int bulbGoopCapacity() {
        return GOOP_BULB_TOTAL_CAPACITY.get();
    }

    private ForgeConfigSpec.DoubleValue SMELTING_ITEM_MOLTEN_RATIO;
    public double smeltingRatio() { return SMELTING_ITEM_MOLTEN_RATIO.get(); }

    private ForgeConfigSpec.DoubleValue FOOD_HUNGER_VITAL_RATIO;
    public double foodHungerRatio() { return FOOD_HUNGER_VITAL_RATIO.get(); }

    private ForgeConfigSpec.DoubleValue FOOD_SATURATION_VITAL_RATIO;
    public double foodSaturationRatio() { return FOOD_SATURATION_VITAL_RATIO.get(); }

    public ServerConfiguration() {
        this.init();
    }

    public void init() {
        // ensure path exists.
        FMLPaths.getOrCreateGameRelativePath(FMLPaths.CONFIGDIR.get().resolve(GooMod.MOD_ID), GooMod.MOD_ID);

        setupGeneralMachineConfig();

        setupGeneralMapperConfig();

        finalizeServerConfig();
    }

    private void finalizeServerConfig() {
        server = serverBuilder.build();
    }

    private void setupGeneralMachineConfig() {
        serverBuilder.comment().push("machines");

        int defaultGoopTransferRate = 8;
        GOOP_MAX_TRANSFER_RATE = serverBuilder.comment("Maximum total transfer rate between bulbs, default: " + defaultGoopTransferRate)
                .defineInRange("maxTransferRate", defaultGoopTransferRate, 0, Integer.MAX_VALUE);

        int defaultGoopProcessingRate = 1;
        GOOP_MAX_PROCESSING_RATE = serverBuilder.comment("Maximum total processing rate of goopifiers and solidifiers, default: " + defaultGoopProcessingRate)
                .defineInRange("maxProcessingRate", defaultGoopProcessingRate, 0, Integer.MAX_VALUE);

        int defaultBulbCapacity = 10000;
        GOOP_BULB_TOTAL_CAPACITY = serverBuilder.comment("Maximum total amount of goop in a single bulb, default: " + defaultBulbCapacity)
                .defineInRange("maxBulbCapacity", defaultBulbCapacity, 0, Integer.MAX_VALUE);

        serverBuilder.pop();
    }

    private void setupGeneralMapperConfig()
    {
        serverBuilder.comment().push("mappings");
        double defaultMoltenRatio = 3d;
        SMELTING_ITEM_MOLTEN_RATIO = serverBuilder.comment("Ratio of molten goop per item of burn time (eg coal, 8 items, 8x molten), default: " + defaultMoltenRatio)
                .defineInRange("moltenSmeltingRatio", defaultMoltenRatio, 0d, Double.MAX_VALUE);

        double defaultVitalHungerRatio = 1d;
        FOOD_HUNGER_VITAL_RATIO = serverBuilder.comment("Ratio of food hunger restoration to vital goop, default: " + defaultVitalHungerRatio)
                .defineInRange("foodHungerRatio", defaultVitalHungerRatio, 0d, Double.MAX_VALUE);

        double defaultVitalSaturationRatio = 10d;
        FOOD_SATURATION_VITAL_RATIO = serverBuilder.comment("Ratio of food saturation restoration to vital goop, default: " + defaultVitalSaturationRatio)
                .defineInRange("foodSaturationRatio", defaultVitalSaturationRatio, 0d, Double.MAX_VALUE);


        serverBuilder.pop();
    }

    public void loadConfig(ForgeConfigSpec spec, Path path)
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
