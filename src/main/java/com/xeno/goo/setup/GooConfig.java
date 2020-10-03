package com.xeno.goo.setup;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.xeno.goo.GooMod;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.interactions.IBlobInteraction;
import com.xeno.goo.interactions.ISplatInteraction;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.Tuple;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Mod.EventBusSubscriber
public class GooConfig
{
    // config types and builders
    public ForgeConfigSpec server;
    private ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();

    public GooConfig() {
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

    private ForgeConfigSpec.IntValue BASIN_HOLDING_MULTIPLIER;
    public int basinHoldingMultiplier() { return BASIN_HOLDING_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue GAUNTLET_HOLDING_MULTIPLIER;
    public int gauntletHoldingMultiplier() { return GAUNTLET_HOLDING_MULTIPLIER.get(); }

    private ForgeConfigSpec.DoubleValue ENERGETIC_MINING_BLAST_RADIUS;
    public double energeticMiningBlastRadius() { return ENERGETIC_MINING_BLAST_RADIUS.get(); }

    // -1 means disabled, 0 means free??! or just don't ever be free, and unallowed values are disabled.
    private Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> SPLAT_RESOLVER_COSTS = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> SPLAT_RESOLVER_RETURNS = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> BLOB_RESOLVER_COSTS = new HashMap<>();
    private Map<Fluid, ForgeConfigSpec.IntValue> THROWN_GOO_AMOUNTS = new HashMap<>();

    private void registerBlobInteractions(Fluid fluid, Map<Tuple<Integer, String>, IBlobInteraction> blobInteractions) {
        int defaultCostForInteractions = 16;
        HashMap<String, ForgeConfigSpec.IntValue> costMap = new HashMap<>();
        serverBuilder.push(Objects.requireNonNull(fluid.getRegistryName()).toString());
        blobInteractions.forEach((k, v) -> {
            int actualCost = defaultCostForInteractions;
            if (fluid.equals(Registry.DECAY_GOO.get())) {
                actualCost = 2;
            }
            ForgeConfigSpec.IntValue costOfInteraction = serverBuilder.comment("Cost of blob interaction " + k.getB() + ", -1 to disable, default:" + actualCost)
                    .defineInRange(k.getB(), actualCost, -1, 1000);
            costMap.put(k.getB(), costOfInteraction);
        });
        serverBuilder.pop();
        BLOB_RESOLVER_COSTS.put(fluid, costMap);
    }

    private void registerSplatInteractions(Fluid fluid, Map<Tuple<Integer, String>, ISplatInteraction> splatInteractions)
    {
        int defaultCostForInteractions = 16;
        HashMap<String, ForgeConfigSpec.IntValue> costMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.IntValue> returnMap = new HashMap<>();
        serverBuilder.push(Objects.requireNonNull(fluid.getRegistryName()).toString());
        int[] lowestCost = {Integer.MAX_VALUE};
        splatInteractions.forEach((k, v) -> {
            int actualCost = defaultCostForInteractions;
            if (isBreakerSplatEffect(fluid, k.getB())) {
                actualCost = 2;
                int returnedAmount = actualCost - 1;
                ForgeConfigSpec.IntValue returnOfInteraction = serverBuilder.comment("Returned on splat interaction " + k.getB() + ", -1 to disable, default:" + (actualCost - 1))
                        .defineInRange(k.getB() + "_returned", returnedAmount, -1, 1000);
                returnMap.put(k.getB() + "_returned", returnOfInteraction);
            }
            if (fluid.equals(Registry.FAUNAL_GOO.get())) {
                actualCost = 1;
            }
            ForgeConfigSpec.IntValue costOfInteraction = serverBuilder.comment("Cost of splat interaction " + k.getB() + ", -1 to disable, default:" + actualCost)
                    .defineInRange(k.getB(), actualCost, -1, 1000);
            costMap.put(k.getB(), costOfInteraction);
            if (actualCost < lowestCost[0]) {
                lowestCost[0] = actualCost;
            }
        });
        ForgeConfigSpec.IntValue thrownAmount = serverBuilder.comment("Thrown amount of " + fluid.getRegistryName().toString() + ", -1 to disable, default: " + (lowestCost))
                .defineInRange("thrown_amount", lowestCost[0], -1, 1000);
        serverBuilder.pop();
        SPLAT_RESOLVER_COSTS.put(fluid, costMap);
        SPLAT_RESOLVER_RETURNS.put(fluid, returnMap);
        THROWN_GOO_AMOUNTS.put(fluid, thrownAmount);
    }

    private boolean isBreakerSplatEffect(Fluid fluid, String key)
    {
        return fluid.equals(Registry.CRYSTAL_GOO.get()) // fortune + diamond pick
                || fluid.equals(Registry.HONEY_GOO.get()) // silk touch any
                || fluid.equals(Registry.METAL_GOO.get()) // iron pick
                || fluid.equals(Registry.REGAL_GOO.get()); // fortune + iron pick;
    }

    public int costOfSplatInteraction(Fluid fluid, String key) {
        if (!SPLAT_RESOLVER_COSTS.containsKey(fluid)) {
            return -1;
        }
        if (!SPLAT_RESOLVER_COSTS.get(fluid).containsKey(key)) {
            return -1;
        }
        return SPLAT_RESOLVER_COSTS.get(fluid).get(key).get();
    }

    public int costOfBlobInteraction(Fluid fluid, String key) {
        if (!BLOB_RESOLVER_COSTS.containsKey(fluid)) {
            return -1;
        }
        if (!BLOB_RESOLVER_COSTS.get(fluid).containsKey(key)) {
            return -1;
        }
        return BLOB_RESOLVER_COSTS.get(fluid).get(key).get();
    }

    public int returnOfInteraction(Fluid fluid, String key) {
        key = key + "_returned";
        if (!SPLAT_RESOLVER_RETURNS.containsKey(fluid)) {
            return -1;
        }
        if (!SPLAT_RESOLVER_RETURNS.get(fluid).containsKey(key)) {
            return -1;
        }
        return SPLAT_RESOLVER_RETURNS.get(fluid).get(key).get();
    }

    public int thrownGooAmount(Fluid fluid) {
        if (!THROWN_GOO_AMOUNTS.containsKey(fluid)) {
            return -1;
        }
        return THROWN_GOO_AMOUNTS.get(fluid).get();
    }

    private static class Defaults {
        private static final int GOO_TRANSFER_RATE = 30;
        private static final int GOO_PROCESSING_RATE = 15;
        private static final int BULB_CAPACITY = 16000;
        private static final int BULB_HOLDING_MULTIPLIER = 8;
        private static final int MIXER_INPUT_CAPACITY = 16000;
        private static final int CRUCIBLE_INPUT_CAPACITY = 16000;
        private static final int PUMP_TRANSFER_RATE = 30;
        private static final int BASIN_CAPACITY = 8000;
        private static final int BASIN_HOLDING_MULTIPLIER = 8;
        private static final int GAUNTLET_CAPACITY = 400;
        private static final int GAUNTLET_HOLDING_MULTIPLIER = 2;
        private static final double ENERGETIC_MINING_BLAST_RADIUS = 2.25d;
    }

    private void setupGeneralMachineConfig() {
        serverBuilder.comment().push("general");
        GOO_MAX_TRANSFER_RATE = serverBuilder.comment("Maximum total transfer rate between bulbs, default: " + Defaults.GOO_TRANSFER_RATE)
                .defineInRange("maxTransferRate", Defaults.GOO_TRANSFER_RATE, 0, 10000);
        GOO_MAX_PROCESSING_RATE = serverBuilder.comment("Maximum total processing rate of gooifiers and solidifiers, default: " + Defaults.GOO_PROCESSING_RATE)
                .defineInRange("maxProcessingRate", Defaults.GOO_PROCESSING_RATE, 0, 10000);
        GOO_BULB_TOTAL_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single bulb, default: " + Defaults.BULB_CAPACITY)
                .defineInRange("maxBulbCapacity", Defaults.BULB_CAPACITY, 0, 100000);
        BULB_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanted holding capacity of bulbs is multiplied by this, per level, default: " + Defaults.BULB_HOLDING_MULTIPLIER)
                .defineInRange("bulbHoldingMultiplier", Defaults.BULB_HOLDING_MULTIPLIER, 0, 10);
        MIXER_INPUT_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single mixer input tank, default: " + Defaults.MIXER_INPUT_CAPACITY)
                .defineInRange("maxMixerInputCapacity", Defaults.MIXER_INPUT_CAPACITY, 0, 100000);
        CRUCIBLE_INPUT_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a crucible tank, default: " + Defaults.CRUCIBLE_INPUT_CAPACITY)
                .defineInRange("maxCrucibleInputCapacity", Defaults.CRUCIBLE_INPUT_CAPACITY, 0, 100000);
        GOO_PUMP_TRANSFER_RATE = serverBuilder.comment("Max quantity of fluid pumped per tick, default: " + Defaults.PUMP_TRANSFER_RATE)
                .defineInRange("pumpTransferRate", Defaults.PUMP_TRANSFER_RATE, 0, 10000);
        BASIN_CAPACITY = serverBuilder.comment("Max quantity of fluid held in a basin, default: " + Defaults.BASIN_CAPACITY)
                .defineInRange("basinCapacity", Defaults.BASIN_CAPACITY, 0, 100000);
        BASIN_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanted holding capacity of basins is multiplied by this, per level, default: " + Defaults.BASIN_HOLDING_MULTIPLIER)
                .defineInRange("basinHoldingMultiplier", Defaults.BASIN_HOLDING_MULTIPLIER, 0, 10);
        GAUNTLET_CAPACITY = serverBuilder.comment("Max quantity of fluid held on a gauntlet, default: " + Defaults.GAUNTLET_CAPACITY)
                .defineInRange("gauntletCapacity", Defaults.GAUNTLET_CAPACITY, 0, 1000000);
        GAUNTLET_HOLDING_MULTIPLIER = serverBuilder.comment("Enchanted holding capacity of gauntlets is multiplied by this, per level, default: " + Defaults.GAUNTLET_HOLDING_MULTIPLIER)
                .defineInRange("gauntletHoldingMultiplier", Defaults.GAUNTLET_HOLDING_MULTIPLIER, 0, 10);
        ENERGETIC_MINING_BLAST_RADIUS = serverBuilder.comment("Mining blast radius of energetic goo, default: " + Defaults.ENERGETIC_MINING_BLAST_RADIUS)
                .defineInRange("energeticMiningBlastRadius", Defaults.ENERGETIC_MINING_BLAST_RADIUS, 1d, 10d);
        GooInteractions.splatRegistry.forEach(this::registerSplatInteractions);
        GooInteractions.blobRegistry.forEach(this::registerBlobInteractions);
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
