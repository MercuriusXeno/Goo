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

    private void setupGeneralMachineConfig() {
        serverBuilder.comment().push("machines");

        int defaultGooTransferRate = 30;
        GOO_MAX_TRANSFER_RATE = serverBuilder.comment("Maximum total transfer rate between bulbs, default: " + defaultGooTransferRate)
                .defineInRange("maxTransferRate", defaultGooTransferRate, 0, Integer.MAX_VALUE);

        int defaultGooProcessingRate = 15;
        GOO_MAX_PROCESSING_RATE = serverBuilder.comment("Maximum total processing rate of gooifiers and solidifiers, default: " + defaultGooProcessingRate)
                .defineInRange("maxProcessingRate", defaultGooProcessingRate, 0, Integer.MAX_VALUE);

        int defaultBulbCapacity = 16000;
        GOO_BULB_TOTAL_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single bulb, default: " + defaultBulbCapacity)
                .defineInRange("maxBulbCapacity", defaultBulbCapacity, 0, Integer.MAX_VALUE);

        int defaultMixerInputCapacity = 16000;
        MIXER_INPUT_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single mixer input tank, default: " + defaultMixerInputCapacity)
                .defineInRange("maxMixerInputCapacity", defaultMixerInputCapacity, 0, Integer.MAX_VALUE);

        int defaultCrucibleInputCapacity = 16000;
        CRUCIBLE_INPUT_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a crucible tank, default: " + defaultCrucibleInputCapacity)
                .defineInRange("maxCrucibleInputCapacity", defaultCrucibleInputCapacity, 0, Integer.MAX_VALUE);

        int defaultPumpTransferRate = 30;
        GOO_PUMP_TRANSFER_RATE = serverBuilder.comment("Max quantity of fluid pumped per tick, default: " + defaultPumpTransferRate)
                .defineInRange("pumpTransferRate", defaultPumpTransferRate, 0, Integer.MAX_VALUE);

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
