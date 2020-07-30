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
    // config types and builders
    public ForgeConfigSpec common;
    private ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();

    // machine config values
    private ForgeConfigSpec.IntValue GOOP_MAX_TRANSFER_RATE;
    public int goopTransferRate() {
        return GOOP_MAX_TRANSFER_RATE.get();
    }

    private ForgeConfigSpec.IntValue GOOP_BULB_TOTAL_CAPACITY;
    public int bulbGoopCapacity() {
        return GOOP_BULB_TOTAL_CAPACITY.get();
    }

    public Config() {
        this.init();
    }

    public void init() {
        // ensure path exists.
        FMLPaths.getOrCreateGameRelativePath(FMLPaths.CONFIGDIR.get().resolve(GoopMod.MOD_ID), GoopMod.MOD_ID);

        setupGeneralMachineConfig();

        finalizeCommonConfig();
    }

    private void finalizeCommonConfig() {
        common = commonBuilder.build();
    }

    private void setupGeneralMachineConfig() {
        commonBuilder.comment().push("machines");
        // This is the maximum work any machine (bulb or otherwise) can do in a single tick, irrespective of the number of bulbs connected.
        int defaultGoopTransferRate = 15;
        GOOP_MAX_TRANSFER_RATE = commonBuilder.comment("Maximum total transfer rate of bulbs and machines per tick")
                .defineInRange("maxTransferRate", defaultGoopTransferRate, 0, Integer.MAX_VALUE);
        // default bulb capacity, at the time of writing set to 1e8
        int defaultBulbCapacity = 100000;
        GOOP_BULB_TOTAL_CAPACITY = commonBuilder.comment("Maximum total amount of goop in a single bulb")
                .defineInRange("maxBulbCapacity", defaultBulbCapacity, 0, Integer.MAX_VALUE);
        commonBuilder.pop();
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
