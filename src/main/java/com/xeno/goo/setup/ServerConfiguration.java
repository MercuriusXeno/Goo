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
//
//    private ForgeConfigSpec.IntValue MOBIUS_CRUCIBLE_BASE_CAPACITY;
//    public int mobiusCrucibleBaseCapacity() { return MOBIUS_CRUCIBLE_BASE_CAPACITY.get(); }
//
//    private ForgeConfigSpec.IntValue MOBIUS_CRUCIBLE_HOLDING_MULTIPLIER;
//    public int mobiusCrucibleHoldingMultiplier() { return MOBIUS_CRUCIBLE_HOLDING_MULTIPLIER.get(); }

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

    private ForgeConfigSpec.IntValue GAUNTLET_BASE_CAPACITY;
    public int gauntletBaseCapacity() {
        return GAUNTLET_BASE_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue GAUNTLET_HOLDING_MULTIPLIER;
    public int gauntletHoldingMultiplier() {
        return GAUNTLET_HOLDING_MULTIPLIER.get();
    }
//
//    private ForgeConfigSpec.DoubleValue COMBO_GAUNTLET_BREAKPOINT_CHARGE_DELAY;
//    public double comboGauntletBreakpointChargeDelay() {
//        return GAUNTLET_BREAKPOINT_CHARGE_DELAY.get();
//    }
//
//    private ForgeConfigSpec.DoubleValue COMBO_GAUNTLET_LOB_VELOCITY;
//    public double comboGauntletLobVelocity() {
//        return COMBO_GAUNTLET_LOB_VELOCITY.get();
//    }
//
//    private ForgeConfigSpec.DoubleValue COMBO_GAUNTLET_POWER_MULTIPLIER;
//    public double comboGauntletPowerMultiplier() {
//        return GAUNTLET_POWER_MULTIPLIER.get();
//    }
//
//    private ForgeConfigSpec.IntValue COMBO_GAUNTLET_BASE_CAPACITY;
//    public int comboGauntletBaseCapacity() {
//        return COMBO_GAUNTLET_BASE_CAPACITY.get();
//    }
//
//    private ForgeConfigSpec.IntValue COMBO_GAUNTLET_HOLDING_MULTIPLIER;
//    public int comboGauntletHoldingMultipler() { return COMBO_GAUNTLET_HOLDING_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue MAX_HOLDING_ENCHANTMENT;
    public int maxHoldingEnchantment() { return MAX_HOLDING_ENCHANTMENT.get(); }

    private ForgeConfigSpec.IntValue MAX_ARMSTRONG_ENCHANTMENT;
    public int maxArmstrongEnchantment() { return MAX_ARMSTRONG_ENCHANTMENT.get(); }

    private void setupUtilityConfig()
    {
        serverBuilder.comment().push("utility");

        int defaultCrucibleBaseCapacity = 200;
        CRUCIBLE_BASE_CAPACITY = serverBuilder.comment("Max goo you can hold in a single unenchanted crucible, default: " + defaultCrucibleBaseCapacity)
                .defineInRange("crucibleBaseCapacity", defaultCrucibleBaseCapacity, 0, Integer.MAX_VALUE);

        int defaultCrucibleHoldingMultiplier = 5;
        CRUCIBLE_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanting a crucible with holding multiplies its storage by this amount, default: " + defaultCrucibleHoldingMultiplier)
                .defineInRange("crucibleHoldingMultiplier", defaultCrucibleHoldingMultiplier, 0, Integer.MAX_VALUE);

//        int defaultMobiusCrucibleBaseCapacity = 200;
//        MOBIUS_CRUCIBLE_BASE_CAPACITY = serverBuilder.comment("Max goo you can hold in a single unenchanted mobius crucible, per tank, default: " + defaultMobiusCrucibleBaseCapacity)
//                .defineInRange("mobiusCrucibleBaseCapacity", defaultMobiusCrucibleBaseCapacity, 0, Integer.MAX_VALUE);
//
//        int defaultMobiusCrucibleHoldingMultiplier = 5;
//        MOBIUS_CRUCIBLE_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanting a mobius crucible with holding multiplies its storage by this amount, default: " + defaultMobiusCrucibleHoldingMultiplier)
//                .defineInRange("mobiusCrucibleHoldingMultiplier", defaultMobiusCrucibleHoldingMultiplier, 0, Integer.MAX_VALUE);

        int defaultGauntletBaseCapacity = 25;
        GAUNTLET_BASE_CAPACITY = serverBuilder.comment("Max goo you can hold in a single unenchanted gauntlet, default: " + defaultGauntletBaseCapacity)
                .defineInRange("gauntletBaseCapacity", defaultGauntletBaseCapacity, 0, Integer.MAX_VALUE);

        int defaultGauntletHoldingMultiplier = 2;
        GAUNTLET_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanting a gauntlet with holding multiplies its storage by this amount, default: " + defaultGauntletHoldingMultiplier)
                .defineInRange("gauntletHoldingMultiplier", defaultGauntletHoldingMultiplier, 0, Integer.MAX_VALUE);

        double defaultGauntletBreakpointChargeDelay = 0.75d;
        GAUNTLET_BREAKPOINT_CHARGE_DELAY = serverBuilder.comment("Number of seconds it takes to charge to the next gauntlet goo breakpoint, default: " + defaultGauntletBreakpointChargeDelay)
                .defineInRange("gauntletBreakpointChargeDelay", defaultGauntletBreakpointChargeDelay, 0, Double.MAX_VALUE);

        double defaultGauntletLobVelocity = 3d;
        GAUNTLET_LOB_VELOCITY = serverBuilder.comment("Velocity of lobbed goo from an unenchanted gauntlet, default: " + defaultGauntletLobVelocity)
                .defineInRange("gauntletLobVelocity", defaultGauntletLobVelocity, 0, Double.MAX_VALUE);

        double defaultGauntletPowerMultiplier = 1.4d;
        GAUNTLET_POWER_MULTIPLIER = serverBuilder.comment("Enchanting a gauntlet with power multiplies its lob velocity by this amount, default: " + defaultGauntletPowerMultiplier)
                .defineInRange("gauntletPowerMultiplier", defaultGauntletPowerMultiplier, 0, Double.MAX_VALUE);

//        int defaultComboGauntletBaseCapacity = 25;
//        COMBO_GAUNTLET_BASE_CAPACITY = serverBuilder.comment("Max goo you can hold in a single unenchanted combo gauntlet, default: " + defaultComboGauntletBaseCapacity)
//                .defineInRange("comboGauntletBaseCapacity", defaultComboGauntletBaseCapacity, 0, Integer.MAX_VALUE);
//
//        int defaultComboGauntletHoldingMultiplier = 2;
//        COMBO_GAUNTLET_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanting a combo gauntlet with holding multiplies its storage by this amount, default: " + defaultComboGauntletHoldingMultiplier)
//                .defineInRange("comboGauntletHoldingMultiplier", defaultComboGauntletHoldingMultiplier, 0, Integer.MAX_VALUE);
//
//        double defaultComboGauntletBreakpointChargeDelay = 0.5d;
//        COMBO_GAUNTLET_BREAKPOINT_CHARGE_DELAY = serverBuilder.comment("Number of seconds it takes to charge to the next combo gauntlet goo breakpoint, default: " + defaultComboGauntletBreakpointChargeDelay)
//                .defineInRange("comboGauntletBreakpointChargeDelay", defaultComboGauntletBreakpointChargeDelay, 0, Double.MAX_VALUE);
//
//        double defaultComboGauntletLobVelocity = 3d;
//        COMBO_GAUNTLET_LOB_VELOCITY = serverBuilder.comment("Velocity of lobbed goo from an unenchanted combo gauntlet, default: " + defaultComboGauntletLobVelocity)
//                .defineInRange("comboGauntletLobVelocity", defaultComboGauntletLobVelocity, 0, Double.MAX_VALUE);
//
//        double defaultComboGauntletPowerMultiplier = 1.4d;
//        COMBO_GAUNTLET_POWER_MULTIPLIER = serverBuilder.comment("Enchanting a combo gauntlet with power multiplies its lob velocity by this amount, default: " + defaultComboGauntletPowerMultiplier)
//                .defineInRange("comboGauntletPowerMultiplier", defaultComboGauntletPowerMultiplier, 0, Double.MAX_VALUE);

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

        int defaultBulbCapacity = 1000;
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

        double defaultVitalSaturationRatio = 5d;
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
