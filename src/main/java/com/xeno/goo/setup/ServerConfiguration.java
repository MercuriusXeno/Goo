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

    public ServerConfiguration() {
        this.init();
    }

    public void init() {
        // ensure path exists.
        FMLPaths.getOrCreateGameRelativePath(FMLPaths.CONFIGDIR.get().resolve(GooMod.MOD_ID), GooMod.MOD_ID);

        setupGeneralMachineConfig();

        finalizeServerConfig();
    }

    private void finalizeServerConfig() {
        server = serverBuilder.build();
    }

    // machine config values
    private ForgeConfigSpec.IntValue GOO_MAX_TRANSFER_RATE;
    public int gooTransferRate() { return GOO_MAX_TRANSFER_RATE.get(); }

    private ForgeConfigSpec.IntValue GOO_MAX_PROCESSING_RATE;
    public int gooProcessingRate() { return GOO_MAX_PROCESSING_RATE.get(); }

    private ForgeConfigSpec.IntValue GOO_BULB_TOTAL_CAPACITY;
    public int bulbCapacity() {
        return GOO_BULB_TOTAL_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue MIXER_INPUT_CAPACITY;
    public int mixerInputCapacity() {
        return MIXER_INPUT_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue CRUCIBLE_INPUT_CAPACITY;
    public int crucibleInputCapacity() {
        return CRUCIBLE_INPUT_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue GOO_PUMP_TRANSFER_RATE;
    public int pumpAmountPerCycle() { return GOO_PUMP_TRANSFER_RATE.get(); }

    private ForgeConfigSpec.IntValue BASIN_CAPACITY;
    public int basinCapacity() { return BASIN_CAPACITY.get(); }

    private ForgeConfigSpec.IntValue GAUNTLET_CAPACITY;
    public int gauntletCapacity() { return GAUNTLET_CAPACITY.get(); }

    private ForgeConfigSpec.IntValue BULB_HOLDING_MULTIPLIER;
    public int bulbHoldingMultiplier() { return BULB_HOLDING_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue MAX_BULB_HOLDING_LEVELS;
    public int maxBulbHolding() { return MAX_BULB_HOLDING_LEVELS.get(); }

    private ForgeConfigSpec.IntValue BASIN_HOLDING_MULTIPLIER;
    public int basinHoldingMultiplier() { return BASIN_HOLDING_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue MAX_BASIN_HOLDING_LEVELS;
    public int maxBasinHolding() { return MAX_BASIN_HOLDING_LEVELS.get(); }

    private ForgeConfigSpec.IntValue GAUNTLET_HOLDING_MULTIPLIER;
    public int gauntletHoldingMultiplier() { return GAUNTLET_HOLDING_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue MAX_GAUNTLET_HOLDING_LEVELS;
    public int maxGauntletHolding() { return MAX_GAUNTLET_HOLDING_LEVELS.get(); }

    private ForgeConfigSpec.BooleanValue GEOMANCY_ENHANCEMENT_ENABLED;
    public boolean geomancyEnabled() { return GEOMANCY_ENHANCEMENT_ENABLED.get(); }


    private void setupGeneralMachineConfig() {
        serverBuilder.comment().push("general");

        int defaultGooTransferRate = 30;
        GOO_MAX_TRANSFER_RATE = serverBuilder.comment("Maximum total transfer rate between bulbs, default: " + defaultGooTransferRate)
                .defineInRange("maxTransferRate", defaultGooTransferRate, 0, Integer.MAX_VALUE);

        int defaultGooProcessingRate = 15;
        GOO_MAX_PROCESSING_RATE = serverBuilder.comment("Maximum total processing rate of gooifiers and solidifiers, default: " + defaultGooProcessingRate)
                .defineInRange("maxProcessingRate", defaultGooProcessingRate, 0, Integer.MAX_VALUE);

        int defaultBulbCapacity = 16000;
        GOO_BULB_TOTAL_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single bulb, default: " + defaultBulbCapacity)
                .defineInRange("maxBulbCapacity", defaultBulbCapacity, 0, Integer.MAX_VALUE);

        int defaultBulbHoldingMultiplier = 8;
        BULB_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanted holding capacity of bulbs is multiplied by this, per level, default: " + defaultBulbHoldingMultiplier)
                .defineInRange("bulbHoldingMultiplier", defaultBulbHoldingMultiplier, 0, Integer.MAX_VALUE);

        int defaultMaxBulbHolding = 4;
        MAX_BULB_HOLDING_LEVELS = serverBuilder.comment("Max level of holding enchantment on bulbs, default: " + defaultMaxBulbHolding)
                .defineInRange("maxBulbHolding", defaultMaxBulbHolding, 0, Integer.MAX_VALUE);

        int defaultMixerInputCapacity = 16000;
        MIXER_INPUT_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single mixer input tank, default: " + defaultMixerInputCapacity)
                .defineInRange("maxMixerInputCapacity", defaultMixerInputCapacity, 0, Integer.MAX_VALUE);

        int defaultCrucibleInputCapacity = 16000;
        CRUCIBLE_INPUT_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a crucible tank, default: " + defaultCrucibleInputCapacity)
                .defineInRange("maxCrucibleInputCapacity", defaultCrucibleInputCapacity, 0, Integer.MAX_VALUE);

        int defaultPumpTransferRate = 30;
        GOO_PUMP_TRANSFER_RATE = serverBuilder.comment("Max quantity of fluid pumped per tick, default: " + defaultPumpTransferRate)
                .defineInRange("pumpTransferRate", defaultPumpTransferRate, 0, Integer.MAX_VALUE);

        int defaultBasinCapacity = 8000;
        BASIN_CAPACITY = serverBuilder.comment("Max quantity of fluid held in a basin, default: " + defaultBasinCapacity)
                .defineInRange("basinCapacity", defaultBasinCapacity, 0, Integer.MAX_VALUE);

        int defaultBasinHoldingMultiplier = 8;
        BASIN_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanted holding capacity of basins is multiplied by this, per level, default: " + defaultBasinHoldingMultiplier)
                .defineInRange("basinHoldingMultiplier", defaultBasinHoldingMultiplier, 0, Integer.MAX_VALUE);

        int defaultMaxBasinHolding = 4;
        MAX_BASIN_HOLDING_LEVELS = serverBuilder.comment("Max level of holding enchantment on basins, default: " + defaultMaxBasinHolding)
                .defineInRange("maxBasinHolding", defaultMaxBasinHolding, 0, Integer.MAX_VALUE);

        int defaultGauntletCapacity = 100;
        GAUNTLET_CAPACITY = serverBuilder.comment("Max quantity of fluid held on a gauntlet, default: " + defaultGauntletCapacity)
                .defineInRange("gauntletCapacity", defaultGauntletCapacity, 0, Integer.MAX_VALUE);

        int defaultGauntletHoldingMultiplier = 2;
        GAUNTLET_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanted holding capacity of gauntlets is multiplied by this, per level, default: " + defaultGauntletHoldingMultiplier)
                .defineInRange("gauntletHoldingMultiplier", defaultGauntletHoldingMultiplier, 0, Integer.MAX_VALUE);

        int defaultMaxGauntletHolding = 4;
        MAX_GAUNTLET_HOLDING_LEVELS = serverBuilder.comment("Max level of holding enchantment on gauntlets, default: " + defaultMaxGauntletHolding)
                .defineInRange("maxGauntletHolding", defaultMaxGauntletHolding, 0, Integer.MAX_VALUE);

        boolean defaultEnableGeomancy = true;
        GEOMANCY_ENHANCEMENT_ENABLED = serverBuilder.comment("Gauntlet geomancy enchantment allowed, enabled by default: " + defaultEnableGeomancy)
                .define("enableGeomancy", defaultEnableGeomancy);

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
