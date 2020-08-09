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

        setupUtilityConfig();

        setupGeneralMapperConfig();

        finalizeServerConfig();
    }

    // utility config values
    private ForgeConfigSpec.IntValue CRUCIBLE_BASE_CAPACITY;
    public int crucibleBaseCapacity() { return CRUCIBLE_BASE_CAPACITY.get(); }

    private ForgeConfigSpec.IntValue CRUCIBLE_HOLDING_MULTIPLIER;
    public int crucibleHoldingMultiplier() { return CRUCIBLE_HOLDING_MULTIPLIER.get(); }

    private ForgeConfigSpec.DoubleValue GAUNTLET_BREAKPOINT_CHARGE_DELAY;
    public double gauntletBreakpointChargeDelay() {
        return GAUNTLET_BREAKPOINT_CHARGE_DELAY.get();
    }

    private ForgeConfigSpec.DoubleValue GAUNTLET_LOB_VELOCITY;
    public double gauntletLobVelocity() {
        return GAUNTLET_LOB_VELOCITY.get();
    }

    private ForgeConfigSpec.DoubleValue GAUNTLET_POWER_MULTIPLIER;
    public double gauntletPowerMultiplier() {
        return GAUNTLET_POWER_MULTIPLIER.get();
    }

    private void setupUtilityConfig()
    {
        serverBuilder.comment().push("utility");

        int defaultCrucibleBaseCapacity = 1000;
        CRUCIBLE_BASE_CAPACITY = serverBuilder.comment("Max goo you can hold in a single unenchanted crucible, default: " + defaultCrucibleBaseCapacity)
                .defineInRange("crucibleBaseCapacity", defaultCrucibleBaseCapacity, 0, Integer.MAX_VALUE);

        int defaultCrucibleHoldingMultiplier = 10;
        CRUCIBLE_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanting a crucible with holding multiplies its storage by this amount, default: " + defaultCrucibleHoldingMultiplier)
                .defineInRange("crucibleHoldingMultiplier", defaultCrucibleHoldingMultiplier, 0, Integer.MAX_VALUE);

        double defaultGauntletBreakpointChargeDelay = 0.75d;
        GAUNTLET_BREAKPOINT_CHARGE_DELAY = serverBuilder.comment("Number of seconds it takes to charge to the next gauntlet goo breakpoint, default: " + defaultGauntletBreakpointChargeDelay)
                .defineInRange("gauntletBreakpointChargeDelay", defaultGauntletBreakpointChargeDelay, 0, Double.MAX_VALUE);

        double defaultGauntletLobVelocity = 1d;
        GAUNTLET_LOB_VELOCITY = serverBuilder.comment("Velocity of lobbed goo from an unenchanted gauntlet, default: " + defaultGauntletLobVelocity)
                .defineInRange("gauntletLobVelocity", defaultGauntletLobVelocity, 0, Double.MAX_VALUE);

        double defaultGauntletPowerMultiplier = 1.4d;
        GAUNTLET_POWER_MULTIPLIER = serverBuilder.comment("Enchanting a gauntlet with power multiplies its lob velocity by this amount, default: " + defaultGauntletPowerMultiplier)
                .defineInRange("gauntletPowerMultiplier", defaultGauntletPowerMultiplier, 0, Double.MAX_VALUE);

        serverBuilder.pop();
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

    private void setupGeneralMachineConfig() {
        serverBuilder.comment().push("machines");

        int defaultGooTransferRate = 8;
        GOO_MAX_TRANSFER_RATE = serverBuilder.comment("Maximum total transfer rate between bulbs, default: " + defaultGooTransferRate)
                .defineInRange("maxTransferRate", defaultGooTransferRate, 0, Integer.MAX_VALUE);

        int defaultGooProcessingRate = 1;
        GOO_MAX_PROCESSING_RATE = serverBuilder.comment("Maximum total processing rate of gooifiers and solidifiers, default: " + defaultGooProcessingRate)
                .defineInRange("maxProcessingRate", defaultGooProcessingRate, 0, Integer.MAX_VALUE);

        int defaultBulbCapacity = 10000;
        GOO_BULB_TOTAL_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single bulb, default: " + defaultBulbCapacity)
                .defineInRange("maxBulbCapacity", defaultBulbCapacity, 0, Integer.MAX_VALUE);

        serverBuilder.pop();
    }

    private ForgeConfigSpec.DoubleValue SMELTING_ITEM_MOLTEN_RATIO;
    public double smeltingRatio() { return SMELTING_ITEM_MOLTEN_RATIO.get(); }

    private ForgeConfigSpec.DoubleValue FOOD_HUNGER_VITAL_RATIO;
    public double foodHungerRatio() { return FOOD_HUNGER_VITAL_RATIO.get(); }

    private ForgeConfigSpec.DoubleValue FOOD_SATURATION_VITAL_RATIO;
    public double foodSaturationRatio() { return FOOD_SATURATION_VITAL_RATIO.get(); }

    private void setupGeneralMapperConfig()
    {
        serverBuilder.comment().push("mappings");
        double defaultMoltenRatio = 3d;
        SMELTING_ITEM_MOLTEN_RATIO = serverBuilder.comment("Ratio of molten goo per item of burn time (eg coal, 8 items, 8x molten), default: " + defaultMoltenRatio)
                .defineInRange("moltenSmeltingRatio", defaultMoltenRatio, 0d, Double.MAX_VALUE);

        double defaultVitalHungerRatio = 1d;
        FOOD_HUNGER_VITAL_RATIO = serverBuilder.comment("Ratio of food hunger restoration to vital goo, default: " + defaultVitalHungerRatio)
                .defineInRange("foodHungerRatio", defaultVitalHungerRatio, 0d, Double.MAX_VALUE);

        double defaultVitalSaturationRatio = 10d;
        FOOD_SATURATION_VITAL_RATIO = serverBuilder.comment("Ratio of food saturation restoration to vital goo, default: " + defaultVitalSaturationRatio)
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
