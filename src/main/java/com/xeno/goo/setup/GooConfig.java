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

    private ForgeConfigSpec.IntValue TROUGH_CAPACITY;
    public int troughCapacity() {
        return TROUGH_CAPACITY.get();
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

    // private ForgeConfigSpec.DoubleValue ENERGETIC_MINING_BLAST_RADIUS;
    private ForgeConfigSpec.IntValue ENERGETIC_MINING_BLAST_DISTANCE;
    // public double energeticMiningBlastRadius() { return ENERGETIC_MINING_BLAST_RADIUS.get(); }
    public int energeticMiningBlastDistance() { return ENERGETIC_MINING_BLAST_DISTANCE.get(); }

    private ForgeConfigSpec.IntValue RADIAL_MENU_HELD_TICKS_THRESHOLD;
    public int radialMenuThreshold() { return RADIAL_MENU_HELD_TICKS_THRESHOLD.get(); }

    // -1 means disabled, 0 means free??! or just don't ever be free, and unallowed values are disabled.
    private Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> SPLAT_RESOLVER_COSTS = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.DoubleValue>> SPLAT_TRIGGER_CHANCE = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.DoubleValue>> SPLAT_FAILURE_CHANCE = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.DoubleValue>> SPLAT_DRAIN_CHANCE = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> BLOB_RESOLVER_COSTS = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.DoubleValue>> BLOB_TRIGGER_CHANCE = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.DoubleValue>> BLOB_FAILURE_CHANCE = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.DoubleValue>> BLOB_DRAIN_CHANCE = new HashMap<>();
    private Map<Fluid, ForgeConfigSpec.IntValue> THROWN_GOO_AMOUNTS = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> SPLAT_COOLDOWNS = new HashMap<>();

    private void registerBlobInteractions(Fluid fluid, Map<Tuple<Integer, String>, IBlobInteraction> blobInteractions) {
        int defaultCostForInteractions = 16;
        double defaultChanceOfInteraction = 1.0d;
        double defaultChanceOfDrain = 1.0d;
        double defaultChanceofFail = 0d;
        HashMap<String, ForgeConfigSpec.IntValue> costMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.DoubleValue> triggerMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.DoubleValue> drainMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.DoubleValue> failMap = new HashMap<>();
        serverBuilder.push(Objects.requireNonNull(fluid.getRegistryName()).toString());
        blobInteractions.forEach((k, v) -> {
            int actualCost = defaultCostForInteractions;
            double actualChance = defaultChanceOfInteraction;
            double actualDrainChance = defaultChanceOfDrain;
            double actualFailChance = defaultChanceofFail;
            if (fluid.equals(Registry.DECAY_GOO.get())) {
                actualCost = 2;
            }
            ForgeConfigSpec.IntValue costOfInteraction = serverBuilder.comment("Cost of blob interaction " + k.getB() + ", -1 to disable, default:" + actualCost)
                    .defineInRange(k.getB(), actualCost, -1, 1000);
            ForgeConfigSpec.DoubleValue chanceOfInteraction = serverBuilder.comment("Chance of blob interaction " + k.getB() + ", 0 to disable, default:" + actualChance)
                    .defineInRange(k.getB() + "_chance", actualChance, 0d, 1d);
            ForgeConfigSpec.DoubleValue chanceOfDrain = serverBuilder.comment("Chance of blob cost deduction " + k.getB() + ", 0 is free, default:" + actualDrainChance)
                    .defineInRange(k.getB() + "_drain_chance", actualDrainChance, 0d, 1d);
            ForgeConfigSpec.DoubleValue chanceOfFail = serverBuilder.comment("Chance of interaction failure " + k.getB() + ", 1 to disable, default:" + actualFailChance)
                    .defineInRange(k.getB() + "_fail_chance", actualFailChance, 0d, 1d);
            costMap.put(k.getB(), costOfInteraction);
            triggerMap.put(k.getB() + "_chance", chanceOfInteraction);
            drainMap.put(k.getB() + "_drain_chance", chanceOfDrain);
            failMap.put(k.getB() + "_fail_chance", chanceOfFail);
        });
        serverBuilder.pop();
        BLOB_RESOLVER_COSTS.put(fluid, costMap);
        BLOB_TRIGGER_CHANCE.put(fluid, triggerMap);
        BLOB_DRAIN_CHANCE.put(fluid, drainMap);
        BLOB_FAILURE_CHANCE.put(fluid, failMap);
    }

    private void registerSplatInteractions(Fluid fluid, Map<Tuple<Integer, String>, ISplatInteraction> splatInteractions)
    {
        int defaultCostForInteractions = 16;
        double defaultChanceOfInteraction = 1.0d;
        double defaultChanceOfDrain = 1.0d;
        double defaultChanceofFail = 0d;
        int defaultTickCooldown = 0;
        HashMap<String, ForgeConfigSpec.IntValue> costMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.DoubleValue> triggerMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.DoubleValue> drainMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.DoubleValue> failMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.IntValue> cooldownMap = new HashMap<>();
        serverBuilder.push(Objects.requireNonNull(fluid.getRegistryName()).toString());
        int[] lowestCost = {Integer.MAX_VALUE};
        splatInteractions.forEach((k, v) -> {
            int actualCost = defaultCostForInteractions;
            double actualChance = defaultChanceOfInteraction;
            double actualDrainChance = defaultChanceOfDrain;
            double actualFailChance = defaultChanceofFail;
            int actualCooldown = defaultTickCooldown;
            // specific overrides for map defaults for goos that need it.
            if (fluid.equals(Registry.CRYSTAL_GOO.get())) {
                actualCost = 1;
            }
            if (fluid.equals(Registry.METAL_GOO.get())) {
                actualCost = 1;
            }
            if (fluid.equals(Registry.OBSIDIAN_GOO.get())) {
                actualCost = 2;
            }
            if (fluid.equals(Registry.RADIANT_GOO.get())) {
                actualCost = 4;
            }
            if (fluid.equals(Registry.REGAL_GOO.get())) {
                actualCost = 8;
            }
            if (fluid.equals(Registry.FAUNAL_GOO.get())) {
                actualCost = 8;
            }
            if (fluid.equals(Registry.FLORAL_GOO.get()) && k.getB().equals("flourish")) {
                actualFailChance = 0.8d;
            }
            if (fluid.equals(Registry.HONEY_GOO.get())) {
                actualDrainChance = 0.01d;
                actualCost = 1;
            }
            if (fluid.equals(Registry.LOGIC_GOO.get())) {
                actualDrainChance = 0.01d;
                actualCost = 1;
            }
            if (fluid.equals(Registry.SLIME_GOO.get())) {
                actualDrainChance = 0.125d;
                actualCost = 1;
            }
            if (fluid.equals(Registry.VITAL_GOO.get())) {
                actualDrainChance = 0.5d;
                actualCost = 1;
            }
            if (fluid.equals(Registry.WEIRD_GOO.get())) {
                actualCooldown = 60;
            }
            ForgeConfigSpec.IntValue costOfInteraction = serverBuilder.comment("Cost of splat interaction " + k.getB() + ", -1 to disable, default:" + actualCost)
                    .defineInRange(k.getB(), actualCost, -1, 1000);
            ForgeConfigSpec.DoubleValue chanceOfInteraction = serverBuilder.comment("Chance of blob interaction " + k.getB() + ", 0 to disable, default:" + actualChance)
                    .defineInRange(k.getB() + "_chance", actualChance, 0d, 1d);
            ForgeConfigSpec.DoubleValue chanceOfDrain = serverBuilder.comment("Chance of blob cost deduction " + k.getB() + ", 0 is free, default:" + actualDrainChance)
                    .defineInRange(k.getB() + "_drain_chance", actualDrainChance, 0d, 1d);
            ForgeConfigSpec.DoubleValue chanceOfFail = serverBuilder.comment("Chance of interaction failure " + k.getB() + ", 1 to disable, default:" + actualFailChance)
                    .defineInRange(k.getB() + "_fail_chance", actualFailChance, 0d, 1d);
            ForgeConfigSpec.IntValue cooldownOfInteraction = serverBuilder.comment("Cooldown of splat interaction " + k.getB() + ", default:" + actualCooldown)
                    .defineInRange(k.getB() + "_cooldown", actualCooldown, 0, 1000);
            costMap.put(k.getB(), costOfInteraction);
            triggerMap.put(k.getB() + "_chance", chanceOfInteraction);
            drainMap.put(k.getB() + "_drain_chance", chanceOfDrain);
            failMap.put(k.getB() + "_fail_chance", chanceOfFail);
            cooldownMap.put(k.getB() + "_cooldown", cooldownOfInteraction);
            if (actualCost < lowestCost[0]) {
                lowestCost[0] = actualCost;
            }

            // specifically override cost back to defaults on goos that generally have lower costs
            // by default the config wants to make the thrown amount as low as possible
            // but it's better conveyance if you don't do that in some cases.
            if (fluid.equals(Registry.HONEY_GOO.get())
                    || fluid.equals(Registry.LOGIC_GOO.get())
                    || fluid.equals(Registry.SLIME_GOO.get())
                    || fluid.equals(Registry.VITAL_GOO.get())
                    || fluid.equals(Registry.FAUNAL_GOO.get())
            ) {
                lowestCost[0] = defaultCostForInteractions;
            }
        });
        ForgeConfigSpec.IntValue thrownAmount = serverBuilder.comment("Thrown amount of " + fluid.getRegistryName().toString() + ", -1 to disable, default: " + (lowestCost))
                .defineInRange("thrown_amount", lowestCost[0], -1, 1000);
        serverBuilder.pop();
        SPLAT_RESOLVER_COSTS.put(fluid, costMap);
        SPLAT_TRIGGER_CHANCE.put(fluid, triggerMap);
        SPLAT_DRAIN_CHANCE.put(fluid, drainMap);
        SPLAT_FAILURE_CHANCE.put(fluid, failMap);
        SPLAT_COOLDOWNS.put(fluid, cooldownMap);
        THROWN_GOO_AMOUNTS.put(fluid, thrownAmount);
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

    public int cooldownOfSplatInteraction(Fluid fluid, String key) {
        key = key + "_cooldown";
        if (!SPLAT_COOLDOWNS.containsKey(fluid)) {
            return -1;
        }
        if (!SPLAT_COOLDOWNS.get(fluid).containsKey(key)) {
            return -1;
        }
        return SPLAT_COOLDOWNS.get(fluid).get(key).get();
    }

    public double chanceOfSplatInteraction(Fluid fluid, String key) {
        key = key + "_chance";
        if (!SPLAT_TRIGGER_CHANCE.containsKey(fluid)) {
            return 0d;
        }
        if (!SPLAT_TRIGGER_CHANCE.get(fluid).containsKey(key)) {
            return 0d;
        }
        return SPLAT_TRIGGER_CHANCE.get(fluid).get(key).get();
    }

    public double chanceOfSplatInteractionFailure(Fluid fluid, String key) {
        key = key + "_fail_chance";
        if (!SPLAT_FAILURE_CHANCE.containsKey(fluid)) {
            return 0d;
        }
        if (!SPLAT_FAILURE_CHANCE.get(fluid).containsKey(key)) {
            return 0d;
        }
        return SPLAT_FAILURE_CHANCE.get(fluid).get(key).get();
    }

    public double chanceOfSplatInteractionCost(Fluid fluid, String key) {
        key = key + "_drain_chance";
        if (!SPLAT_DRAIN_CHANCE.containsKey(fluid)) {
            return 0d;
        }
        if (!SPLAT_DRAIN_CHANCE.get(fluid).containsKey(key)) {
            return 0d;
        }
        return SPLAT_DRAIN_CHANCE.get(fluid).get(key).get();
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

    public double chanceOfBlobInteraction(Fluid fluid, String key) {
        key = key + "_chance";
        if (!BLOB_TRIGGER_CHANCE.containsKey(fluid)) {
            return 0d;
        }
        if (!BLOB_TRIGGER_CHANCE.get(fluid).containsKey(key)) {
            return 0d;
        }
        return BLOB_TRIGGER_CHANCE.get(fluid).get(key).get();
    }

    public double chanceOfBlobInteractionFailure(Fluid fluid, String key) {
        key = key + "_fail_chance";
        if (!BLOB_FAILURE_CHANCE.containsKey(fluid)) {
            return 0d;
        }
        if (!BLOB_FAILURE_CHANCE.get(fluid).containsKey(key)) {
            return 0d;
        }
        return BLOB_FAILURE_CHANCE.get(fluid).get(key).get();
    }

    public double chanceOfBlobInteractionCost(Fluid fluid, String key) {
        key = key + "_drain_chance";
        if (!BLOB_DRAIN_CHANCE.containsKey(fluid)) {
            return 0d;
        }
        if (!BLOB_DRAIN_CHANCE.get(fluid).containsKey(key)) {
            return 0d;
        }
        return BLOB_DRAIN_CHANCE.get(fluid).get(key).get();
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
        private static final int BULB_HOLDING_MULTIPLIER = 4;
        private static final int MIXER_INPUT_CAPACITY = 16000;
        private static final int CRUCIBLE_INPUT_CAPACITY = 16000;
        private static final int PUMP_TRANSFER_RATE = 30;
        private static final int BASIN_CAPACITY = 8000;
        private static final int BASIN_HOLDING_MULTIPLIER = 4;
        private static final int GAUNTLET_CAPACITY = 400;
        private static final int GAUNTLET_HOLDING_MULTIPLIER = 4;
        private static final int TROUGH_CAPACITY = 1000;
        // private static final double ENERGETIC_MINING_BLAST_RADIUS = 2.25d;
        private static final int ENERGETIC_MINING_BLAST_DISTANCE = 1;
        private static final int RADIAL_HELD_THRESHOLD_TICKS = 10;
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
        TROUGH_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a trough, default: " + Defaults.TROUGH_CAPACITY)
                .defineInRange("maxTroughCapacity", Defaults.TROUGH_CAPACITY, 0, 100000);
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
        ENERGETIC_MINING_BLAST_DISTANCE = serverBuilder.comment("Mining blast reach of energetic goo, default: " + Defaults.ENERGETIC_MINING_BLAST_DISTANCE)
                .defineInRange("energeticMiningBlastDistance", Defaults.ENERGETIC_MINING_BLAST_DISTANCE, 1, 10);
        RADIAL_MENU_HELD_TICKS_THRESHOLD = serverBuilder.comment("Held ticks threshold for radial menu to open, default: " + Defaults.RADIAL_HELD_THRESHOLD_TICKS)
                .defineInRange("heldTicksRadialMenuThreshold", Defaults.RADIAL_HELD_THRESHOLD_TICKS, 10, 60);
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
