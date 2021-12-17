package com.xeno.goo.setup;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.interactions.IBlobHitInteraction;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.Tuple;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.*;

@Mod.EventBusSubscriber
public class Config
{
    // config types and builders
    public ForgeConfigSpec server;
    public ForgeConfigSpec client;
    private final ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();
    private final ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();

    public Config() {
        setupGeneralMachineConfig();

        setupClientConfig();

        finalizeServerConfig();

        finalizeClientConfig();
    }

    private void finalizeServerConfig() {
        server = serverBuilder.build();
    }

    private void finalizeClientConfig() {
        client = clientBuilder.build();
    }

    private ForgeConfigSpec.BooleanValue GOO_VALUES_VISIBLE_WITHOUT_BOOK;
    public boolean gooValuesAlwaysVisible() { return GOO_VALUES_VISIBLE_WITHOUT_BOOK.get(); }

    private ForgeConfigSpec.BooleanValue DAMAGED_ITEMS_CAN_BE_GOOIFIED;
    public boolean canDamagedItemsBeGooified() { return DAMAGED_ITEMS_CAN_BE_GOOIFIED.get(); }

    // machine config values
    private ForgeConfigSpec.IntValue GOO_MAX_TRANSFER_RATE;
    public int gooTransferRate() { return GOO_MAX_TRANSFER_RATE.get(); }

    private ForgeConfigSpec.IntValue GOOIFIER_PROCESSING_RATE;
    public int gooProcessingRate() { return GOOIFIER_PROCESSING_RATE.get(); }

    private ForgeConfigSpec.IntValue SOLIDIFIER_PROCESSING_RATE;
    public int solidifierProcessingRate() { return SOLIDIFIER_PROCESSING_RATE.get(); }

    private ForgeConfigSpec.IntValue GOO_BULB_TOTAL_CAPACITY;
    public int bulbCapacity() {
        return GOO_BULB_TOTAL_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue CRUCIBLE_TOTAL_CAPACITY;
    public int crucibleCapacity() {
        return CRUCIBLE_TOTAL_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue MIXER_CAPACITY;
    public int mixerCapacity() {
        return MIXER_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue DEGRADER_INPUT_CAPACITY;
    public int degraderInputCapacity() {
        return DEGRADER_INPUT_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue TROUGH_CAPACITY;
    public int troughCapacity() {
        return TROUGH_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue PAD_CAPACITY;
    public int padCapacity() {
        return PAD_CAPACITY.get();
    }

    private ForgeConfigSpec.IntValue GOO_PUMP_TRANSFER_RATE;
    public int pumpAmountPerCycle() { return GOO_PUMP_TRANSFER_RATE.get(); }

    private ForgeConfigSpec.IntValue VESSEL_CAPACITY;
    public int vesselCapacity() { return VESSEL_CAPACITY.get(); }

    private ForgeConfigSpec.IntValue GAUNTLET_CAPACITY;
    public int gauntletCapacity() { return GAUNTLET_CAPACITY.get(); }

    private ForgeConfigSpec.IntValue BULB_CONTAINMENT_MULTIPLIER;
    public int bulbContainmentMultiplier() { return BULB_CONTAINMENT_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue CRUCIBLE_CONTAINMENT_MULTIPLIER;
    public int crucibleContainmentMultiplier() { return CRUCIBLE_CONTAINMENT_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue VESSEL_CONTAINMENT_MULTIPLIER;
    public int vesselContainmentMultiplier() { return VESSEL_CONTAINMENT_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue GAUNTLET_CONTAINMENT_MULTIPLIER;
    public int gauntletContainmentMultiplier() { return GAUNTLET_CONTAINMENT_MULTIPLIER.get(); }

    private ForgeConfigSpec.IntValue ENERGETIC_MINING_BLAST_DISTANCE;
    public int energeticMiningBlastDistance() { return ENERGETIC_MINING_BLAST_DISTANCE.get(); }

    private ForgeConfigSpec.IntValue PRIMORDIAL_SILK_TOUCH_BLAST_DISTANCE;
    public int primordialSilkTouchBlastDistance() { return PRIMORDIAL_SILK_TOUCH_BLAST_DISTANCE.get(); }

    private ForgeConfigSpec.IntValue RADIAL_MENU_HELD_TICKS_THRESHOLD;
    public int radialMenuThreshold() { return RADIAL_MENU_HELD_TICKS_THRESHOLD.get(); }

    private ForgeConfigSpec.IntValue SNAIL_PRODUCTION_AMOUNT;
    public int snailProductionAmount() { return SNAIL_PRODUCTION_AMOUNT.get(); }

    private ForgeConfigSpec.IntValue SNAIL_SPAWN_WEIGHT;
    public int snailSpawnWeight() { return SNAIL_SPAWN_WEIGHT.get(); }

    private ForgeConfigSpec.IntValue LOGIC_POWERS_SOLIDIFIER_TICKS;
    public int logicPowersSolidifierTicks() { return LOGIC_POWERS_SOLIDIFIER_TICKS.get(); }

    private ForgeConfigSpec.IntValue LOGIC_POWERS_MIXER_TICKS;
    public int mixerFuelRatio() {
        return LOGIC_POWERS_MIXER_TICKS.get();
    }

    // -1 means disabled, 0 means free??! or just don't ever be free, and unallowed values are disabled.
    private final Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> SPLAT_RESOLVER_COSTS = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.DoubleValue>> SPLAT_DRAIN_CHANCE = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> BLOB_RESOLVER_COSTS = new HashMap<>();
    private Map<Fluid, ForgeConfigSpec.IntValue> THROWN_GOO_AMOUNTS = new HashMap<>();
    private Map<Fluid, Map<String, ForgeConfigSpec.IntValue>> SPLAT_COOLDOWNS = new HashMap<>();

    private void registerBlobInteractions(Fluid fluid, Map<Tuple<Integer, String>, IBlobHitInteraction> blobInteractions) {
        int defaultCostForInteractions = 4;
        double defaultChanceOfDrain = 1.0d;
        HashMap<String, ForgeConfigSpec.IntValue> costMap = new HashMap<>();
        HashMap<String, ForgeConfigSpec.DoubleValue> drainMap = new HashMap<>();
        serverBuilder.push(Objects.requireNonNull(fluid.getRegistryName()).toString());
        blobInteractions.forEach((k, v) -> {
            int actualCost = defaultCostForInteractions;
            double actualDrainChance = defaultChanceOfDrain;
            if (fluid.equals(Registry.DECAY_GOO.get())) {
                actualCost = 2;
            }
            ForgeConfigSpec.IntValue costOfInteraction = serverBuilder.comment("Cost of blob interaction " + k.getB() + ", -1 to disable, default:" + actualCost)
                    .defineInRange(k.getB(), actualCost, -1, 1000);
            ForgeConfigSpec.DoubleValue chanceOfDrain = serverBuilder.comment("Chance of blob cost deduction " + k.getB() + ", 0 is free, default:" + actualDrainChance)
                    .defineInRange(k.getB() + "_drain_chance", actualDrainChance, 0d, 1d);
            costMap.put(k.getB(), costOfInteraction);
            drainMap.put(k.getB() + "_drain_chance", chanceOfDrain);
        });
        serverBuilder.pop();
        BLOB_RESOLVER_COSTS.put(fluid, costMap);
    }

    public int costOfBlobHitInteraction(Fluid fluid, String key) {
        if (!BLOB_RESOLVER_COSTS.containsKey(fluid)) {
            return -1;
        }
        if (!BLOB_RESOLVER_COSTS.get(fluid).containsKey(key)) {
            return -1;
        }
        return BLOB_RESOLVER_COSTS.get(fluid).get(key).get();
    }

    public int thrownGooAmount(Fluid fluid) {
        if (!THROWN_GOO_AMOUNTS.containsKey(fluid)) {
            return -1;
        }
        return THROWN_GOO_AMOUNTS.get(fluid).get();
    }

    public void setValuesVisibleWithoutBook(boolean f) {
        this.GOO_VALUES_VISIBLE_WITHOUT_BOOK.set(f);
        this.client.save();
    }

	private static class Defaults {
        public static final boolean GOO_VALUES_VISIBLE_ALWAYS = false;
        public static final boolean DAMAGED_ITEMS_CAN_BE_GOOIFIED = true;
        public static final int SOLIDIFIER_PROCESSING_RATE = 5;
        private static final int GOO_TRANSFER_RATE = 30;
        private static final int LOGIC_POWERS_SOLIDIFIER_TICKS = 8;
        private static final int GOOIFIER_PROCESSING_RATE = 15;
        private static final int BULB_CAPACITY = 16000;
        private static final int BULB_CONTAINMENT_MULTIPLIER = 4;
        private static final int CRUCIBLE_CAPACITY = 8000;
        private static final int CRUCIBLE_CONTAINMENT_MULTIPLIER = 4;
        private static final int MIXER_CAPACITY = 16000;
        private static final int DEGRADER_INPUT_CAPACITY = 16000;
        private static final int PUMP_TRANSFER_RATE = 30;
        private static final int VESSEL_CAPACITY = 8000;
        private static final int VESSEL_CONTAINMENT_MULTIPLIER = 4;
        private static final int GAUNTLET_CAPACITY = 400;
        private static final int GAUNTLET_CONTAINMENT_MULTIPLIER = 4;
        private static final int TROUGH_CAPACITY = 1000;
        private static final int PAD_CAPACITY = 1000;
        private static final int ENERGETIC_MINING_BLAST_DISTANCE = 1;
        private static final int PRIMORDIAL_SILK_TOUCH_BLAST_DISTANCE = 2;
        private static final int RADIAL_HELD_THRESHOLD_TICKS = 10;
        private static final int SNAIL_PRODUCTION_AMOUNT = 4;
        private static final int SNAIL_SPAWN_WEIGHT = 1;
        private static final int MIXER_FUEL_RATIO = 8;
    }

    private void setupClientConfig() {
        clientBuilder.comment("Goo Client Configs").push("client_options");
        GOO_VALUES_VISIBLE_WITHOUT_BOOK = clientBuilder.comment("Make goo values visible without having the book in your inventory, default: " + Defaults.GOO_VALUES_VISIBLE_ALWAYS)
                .define("gooValuesVisibleWithoutBook", Defaults.GOO_VALUES_VISIBLE_ALWAYS);
        clientBuilder.pop();
    }

    private void setupGeneralMachineConfig() {
        serverBuilder.comment("Goo Server Configs").push("general");
        DAMAGED_ITEMS_CAN_BE_GOOIFIED = serverBuilder.comment("Damaged items can be gooified for a proportional amount of their worth, default: " + Defaults.DAMAGED_ITEMS_CAN_BE_GOOIFIED)
                .define("damagedItemsCanBeGooified", Defaults.DAMAGED_ITEMS_CAN_BE_GOOIFIED);
        GOO_MAX_TRANSFER_RATE = serverBuilder.comment("Maximum total transfer rate between bulbs, default: " + Defaults.GOO_TRANSFER_RATE)
                .defineInRange("maxTransferRate", Defaults.GOO_TRANSFER_RATE, 0, 10000);
        LOGIC_POWERS_SOLIDIFIER_TICKS = serverBuilder.comment("Ticks of solidifier power from a single unit of logic goo, default: " + Defaults.LOGIC_POWERS_SOLIDIFIER_TICKS)
                .defineInRange("logicPowersSolidifierTicks", Defaults.LOGIC_POWERS_SOLIDIFIER_TICKS, 1, 10000);
        GOOIFIER_PROCESSING_RATE = serverBuilder.comment("Maximum processing rate of gooifiers, default: " + Defaults.GOOIFIER_PROCESSING_RATE)
                .defineInRange("maxProcessingRate", Defaults.GOOIFIER_PROCESSING_RATE, 1, 10000);
        SOLIDIFIER_PROCESSING_RATE = serverBuilder.comment("Max processing rate of solidifiers, default: " + Defaults.SOLIDIFIER_PROCESSING_RATE)
                .defineInRange("solidifierProcessingRate", Defaults.SOLIDIFIER_PROCESSING_RATE, 1, 10000);
        GOO_BULB_TOTAL_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a single bulb, default: " + Defaults.BULB_CAPACITY)
                .defineInRange("maxBulbCapacity", Defaults.BULB_CAPACITY, 0, 100000);
        BULB_CONTAINMENT_MULTIPLIER = serverBuilder.comment("Enchanted capacity of bulbs is multiplied by this, per level, default: " + Defaults.BULB_CONTAINMENT_MULTIPLIER)
                .defineInRange("bulbContainmentMultiplier", Defaults.BULB_CONTAINMENT_MULTIPLIER, 0, 10);
        CRUCIBLE_TOTAL_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a crucible, default: " + Defaults.CRUCIBLE_CAPACITY)
                .defineInRange("maxCrucibleCapacity", Defaults.CRUCIBLE_CAPACITY, 0, 100000);
        CRUCIBLE_CONTAINMENT_MULTIPLIER = serverBuilder.comment("Enchanted capacity of crucibles is multiplied by this, per level, default: " + Defaults.CRUCIBLE_CONTAINMENT_MULTIPLIER)
                .defineInRange("crucibleContainmentMultiplier", Defaults.CRUCIBLE_CONTAINMENT_MULTIPLIER, 0, 10);
        MIXER_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a mixer tank, default: " + Defaults.MIXER_CAPACITY)
                .defineInRange("maxMixerCapacity", Defaults.MIXER_CAPACITY, 0, 100000);
        LOGIC_POWERS_MIXER_TICKS = serverBuilder.comment("Amount of processing ticks from a single unit of Logic Goo: " + Defaults.MIXER_CAPACITY)
                .defineInRange("mixerFuelRatio", Defaults.MIXER_FUEL_RATIO, 1, 100000);
        DEGRADER_INPUT_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a degrader tank, default: " + Defaults.DEGRADER_INPUT_CAPACITY)
                .defineInRange("maxDegraderInputCapacity", Defaults.DEGRADER_INPUT_CAPACITY, 0, 100000);
        TROUGH_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a trough, default: " + Defaults.TROUGH_CAPACITY)
                .defineInRange("maxTroughCapacity", Defaults.TROUGH_CAPACITY, 0, 100000);
        PAD_CAPACITY = serverBuilder.comment("Maximum total amount of goo in a pad, default: " + Defaults.PAD_CAPACITY)
                .defineInRange("maxPadCapacity", Defaults.PAD_CAPACITY, 0, 100000);
        GOO_PUMP_TRANSFER_RATE = serverBuilder.comment("Max quantity of fluid pumped per tick, default: " + Defaults.PUMP_TRANSFER_RATE)
                .defineInRange("pumpTransferRate", Defaults.PUMP_TRANSFER_RATE, 0, 10000);
        VESSEL_CAPACITY = serverBuilder.comment("Max quantity of fluid held in a vessel, default: " + Defaults.VESSEL_CAPACITY)
                .defineInRange("vesselCapacity", Defaults.VESSEL_CAPACITY, 0, 100000);
        VESSEL_CONTAINMENT_MULTIPLIER = serverBuilder.comment("Enchanted capacity of vessels is multiplied by this, per level, default: " + Defaults.VESSEL_CONTAINMENT_MULTIPLIER)
                .defineInRange("vesselContainmentMultiplier", Defaults.VESSEL_CONTAINMENT_MULTIPLIER, 0, 10);
        GAUNTLET_CAPACITY = serverBuilder.comment("Max quantity of fluid held on a gauntlet, default: " + Defaults.GAUNTLET_CAPACITY)
                .defineInRange("gauntletCapacity", Defaults.GAUNTLET_CAPACITY, 0, 1000000);
        GAUNTLET_CONTAINMENT_MULTIPLIER = serverBuilder.comment("Enchanted capacity of gauntlets is multiplied by this, per level, default: " + Defaults.GAUNTLET_CONTAINMENT_MULTIPLIER)
                .defineInRange("gauntletContainmentMultiplier", Defaults.GAUNTLET_CONTAINMENT_MULTIPLIER, 0, 10);
        ENERGETIC_MINING_BLAST_DISTANCE = serverBuilder.comment("Mining blast reach of energetic goo, default: " + Defaults.ENERGETIC_MINING_BLAST_DISTANCE)
                .defineInRange("energeticMiningBlastDistance", Defaults.ENERGETIC_MINING_BLAST_DISTANCE, 1, 10);
        PRIMORDIAL_SILK_TOUCH_BLAST_DISTANCE = serverBuilder.comment("Silk-touch blast reach of primordial goo, default: " + Defaults.PRIMORDIAL_SILK_TOUCH_BLAST_DISTANCE)
                .defineInRange("primordialSilkTouchBlastDistance", Defaults.PRIMORDIAL_SILK_TOUCH_BLAST_DISTANCE, 1, 10);
        RADIAL_MENU_HELD_TICKS_THRESHOLD = serverBuilder.comment("Held ticks threshold for radial menu to open, default: " + Defaults.RADIAL_HELD_THRESHOLD_TICKS)
                .defineInRange("heldTicksRadialMenuThreshold", Defaults.RADIAL_HELD_THRESHOLD_TICKS, 10, 60);
        SNAIL_PRODUCTION_AMOUNT = serverBuilder.comment("Amount of primordial goo from snail per Crystal Comb, default: " + Defaults.SNAIL_PRODUCTION_AMOUNT)
                .defineInRange("snailProductionAmount", Defaults.SNAIL_PRODUCTION_AMOUNT, 1, 1000000);
        SNAIL_SPAWN_WEIGHT = serverBuilder.comment("Weight relative to other mobs for snails spawning, default:" + Defaults.SNAIL_SPAWN_WEIGHT)
                .defineInRange("snailSpawnWeight", Defaults.SNAIL_SPAWN_WEIGHT, 0, 100);
        GooInteractions.blobHitRegistry.forEach(this::registerBlobInteractions);
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