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

        finalizeServerConfig();
    }

    // utility config values
    private ForgeConfigSpec.IntValue CRUCIBLE_BASE_CAPACITY;
    public int crucibleBaseCapacity() { return CRUCIBLE_BASE_CAPACITY.get(); }

    private ForgeConfigSpec.IntValue CRUCIBLE_HOLDING_MULTIPLIER;
    public int crucibleHoldingMultiplier() { return CRUCIBLE_HOLDING_MULTIPLIER.get(); }

    private ForgeConfigSpec.DoubleValue GAUNTLET_LOB_VELOCITY;
    public double gauntletLobVelocity() { return GAUNTLET_LOB_VELOCITY.get(); }

    private ForgeConfigSpec.DoubleValue GAUNTLET_POWER_MULTIPLIER;
    public double gauntletPowerMultiplier() {
        return GAUNTLET_POWER_MULTIPLIER.get();
    }

    private ForgeConfigSpec.DoubleValue CRUCIBLE_LOB_VELOCITY;
    public double crucibleLobVelocity() { return CRUCIBLE_LOB_VELOCITY.get(); }

    private ForgeConfigSpec.DoubleValue CRUCIBLE_POWER_MULTIPLIER;
    public double cruciblePowerMultiplier() {
        return CRUCIBLE_POWER_MULTIPLIER.get();
    }

    private ForgeConfigSpec.IntValue GAUNTLET_BASE_CAPACITY;
    public int gauntletBaseCapacity() {
        return GAUNTLET_BASE_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue GAUNTLET_HOLDING_MULTIPLIER;
    public int gauntletHoldingMultiplier() {
        return GAUNTLET_HOLDING_MULTIPLIER.get();
    }

    private ForgeConfigSpec.IntValue MAX_HOLDING_ENCHANTMENT;
    public int maxHoldingEnchantment() { return MAX_HOLDING_ENCHANTMENT.get(); }

    private ForgeConfigSpec.IntValue MAX_ARMSTRONG_ENCHANTMENT;
    public int maxArmstrongEnchantment() { return MAX_ARMSTRONG_ENCHANTMENT.get(); }

    private ForgeConfigSpec.IntValue MAX_SWEEPING_ENCHANTMENT;
    public int maxSweepingEnchantment() { return MAX_SWEEPING_ENCHANTMENT.get(); }

    private ForgeConfigSpec.IntValue  MAX_DESTRUCTIVE_CURSE;
    public int maxDestructiveCurse() { return MAX_DESTRUCTIVE_CURSE.get(); }

    private ForgeConfigSpec.IntValue MAX_GOO_WALKER_ENCHANTMENT;
    public int maxGooWalkerEnchantment() { return MAX_GOO_WALKER_ENCHANTMENT.get(); }

    private void setupUtilityConfig()
    {
        serverBuilder.comment().push("utility");

        int defaultCrucibleBaseCapacity = 8000;
        CRUCIBLE_BASE_CAPACITY = serverBuilder.comment("Max goo you can hold in a single unenchanted crucible, default: " + defaultCrucibleBaseCapacity)
                .defineInRange("crucibleBaseCapacity", defaultCrucibleBaseCapacity, 0, Integer.MAX_VALUE);

        int defaultCrucibleHoldingMultiplier = 2;
        CRUCIBLE_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanting a crucible with holding multiplies its storage by this amount, default: " + defaultCrucibleHoldingMultiplier)
                .defineInRange("crucibleHoldingMultiplier", defaultCrucibleHoldingMultiplier, 0, 64000);

        int defaultGauntletBaseCapacity = 125;
        GAUNTLET_BASE_CAPACITY = serverBuilder.comment("Max goo you can hold in a single unenchanted gauntlet, default: " + defaultGauntletBaseCapacity)
                .defineInRange("gauntletBaseCapacity", defaultGauntletBaseCapacity, 0, 16000);

        int defaultGauntletHoldingMultiplier = 2;
        GAUNTLET_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanting a gauntlet with holding multiplies its storage by this amount, default: " + defaultGauntletHoldingMultiplier)
                .defineInRange("gauntletHoldingMultiplier", defaultGauntletHoldingMultiplier, 0, 10);

        double defaultGauntletLobVelocity = 6d;
        GAUNTLET_LOB_VELOCITY = serverBuilder.comment("Velocity of lobbed goo from an unenchanted gauntlet, default: " + defaultGauntletLobVelocity)
                .defineInRange("gauntletLobVelocity", defaultGauntletLobVelocity, 0, 10f);

        double defaultGauntletPowerMultiplier = 1.5d;
        GAUNTLET_POWER_MULTIPLIER = serverBuilder.comment("Enchanting a gauntlet with power multiplies its lob velocity by this amount, default: " + defaultGauntletPowerMultiplier)
                .defineInRange("gauntletPowerMultiplier", defaultGauntletPowerMultiplier, 0, 10f);

        double defaultCrucibleLobVelocity = 5d;
        CRUCIBLE_LOB_VELOCITY = serverBuilder.comment("Velocity of lobbed goo from an unenchanted crucible, default: " + defaultCrucibleLobVelocity)
                .defineInRange("crucibleLobVelocity", defaultCrucibleLobVelocity, 0, 10f);

        double defaultCruciblePowerMultiplier = 1.4d;
        CRUCIBLE_POWER_MULTIPLIER = serverBuilder.comment("Enchanting a crucible with power multiplies its lob velocity by this amount, default: " + defaultCruciblePowerMultiplier)
                .defineInRange("cruciblePowerMultiplier", defaultCruciblePowerMultiplier, 0, 10f);

        int defaultMaxArmstrongEnchantment = 5;
        MAX_ARMSTRONG_ENCHANTMENT = serverBuilder.comment("Number of stacks of armstrong enchantment, max, default: " + defaultMaxArmstrongEnchantment)
                .defineInRange("maxArmstrongEnchantment", defaultMaxArmstrongEnchantment, 0, 10);

        int defaultMaxHoldingEnchantment = 5;
        MAX_HOLDING_ENCHANTMENT = serverBuilder.comment("Number of stacks of holding enchantment, max, default: " + defaultMaxHoldingEnchantment)
                .defineInRange("maxHoldingEnchantment", defaultMaxHoldingEnchantment, 0, 10);

        int defaultMaxSweepingEnchantment = 5;
        MAX_SWEEPING_ENCHANTMENT = serverBuilder.comment("Range of sweeping enchantment +1, max, default: " + defaultMaxSweepingEnchantment)
                .defineInRange("maxSweepingEnchantment", defaultMaxSweepingEnchantment, 0, 10);

        int defaultMaxDestructionCurse = 2;
        MAX_DESTRUCTIVE_CURSE = serverBuilder.comment("Warning, damage is per block! Number of blocks deep the wave of destruction pulses, max, default:  " + defaultMaxDestructionCurse)
                .defineInRange("maxDestructionCurse", defaultMaxSweepingEnchantment, 0, 10);

        int defaultMaxGooWalkingEnchantment = 2;
        MAX_GOO_WALKER_ENCHANTMENT = serverBuilder.comment ("Max level of goo walking structured abilities. Note that levels above two really do nothing. L1: walk on L2: dashes")
                .defineInRange("maxGooWalkingEnchantment", defaultMaxGooWalkingEnchantment, 0, 2);
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

        int defaultGooTransferRate = 120;
        GOO_MAX_TRANSFER_RATE = serverBuilder.comment("Maximum total transfer rate between bulbs, default: " + defaultGooTransferRate)
                .defineInRange("maxTransferRate", defaultGooTransferRate, 0, Integer.MAX_VALUE);

        int defaultGooProcessingRate = 60;
        GOO_MAX_PROCESSING_RATE = serverBuilder.comment("Maximum total processing rate of gooifiers and solidifiers, default: " + defaultGooProcessingRate)
                .defineInRange("maxProcessingRate", defaultGooProcessingRate, 0, Integer.MAX_VALUE);

        int defaultBulbCapacity = 16000;
        GOO_BULB_TOTAL_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single bulb, default: " + defaultBulbCapacity)
                .defineInRange("maxBulbCapacity", defaultBulbCapacity, 0, Integer.MAX_VALUE);

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
