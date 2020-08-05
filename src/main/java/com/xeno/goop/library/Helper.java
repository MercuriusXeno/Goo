package com.xeno.goop.library;

import com.xeno.goop.GoopMod;
import com.xeno.goop.setup.Registry;
import net.minecraft.item.*;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.ForgeHooks;

import java.util.Map;
import java.util.Objects;

public class Helper {
    public static final String moltenGoop = Objects.requireNonNull(Registry.MOLTEN_GOOP.get().getRegistryName()).toString();
    public static final String earthenGoop = Objects.requireNonNull(Registry.EARTHEN_GOOP.get().getRegistryName()).toString();
    public static final String aquaticGoop = Objects.requireNonNull(Registry.AQUATIC_GOOP.get().getRegistryName()).toString();
    public static final String esotericGoop = Objects.requireNonNull(Registry.ESOTERIC_GOOP.get().getRegistryName()).toString();
    public static final String floralGoop = Objects.requireNonNull(Registry.FLORAL_GOOP.get().getRegistryName()).toString();
    public static final String faunalGoop = Objects.requireNonNull(Registry.FAUNAL_GOOP.get().getRegistryName()).toString();
    public static final String fungalGoop = Objects.requireNonNull(Registry.FUNGAL_GOOP.get().getRegistryName()).toString();
    public static final String regalGoop = Objects.requireNonNull(Registry.REGAL_GOOP.get().getRegistryName()).toString();
    public static final String vitalGoop = Objects.requireNonNull(Registry.VITAL_GOOP.get().getRegistryName()).toString();
    public static final String metalGoop = Objects.requireNonNull(Registry.METAL_GOOP.get().getRegistryName()).toString();
    public static final String chromaticGoop = Objects.requireNonNull(Registry.CHROMATIC_GOOP.get().getRegistryName()).toString();
//    private static final int TRUNCATE_MAGNITUDE = 10000;
    private static final int BURN_TIME_PER_SMELTED_ITEM = 200;

    public static GoopValue earthen(double d) { return new GoopValue(earthenGoop, d); }

    public static GoopValue molten(double d) {
        return new GoopValue(moltenGoop, d);
    }

    public static GoopValue aquatic(double d) {
        return new GoopValue(aquaticGoop, d);
    }

    public static GoopValue esoteric(double d) {
        return new GoopValue(esotericGoop, d);
    }

    public static GoopValue floral(double d) {
        return new GoopValue(floralGoop, d);
    }

    public static GoopValue faunal(double d) { return new GoopValue(faunalGoop, d); }

    public static GoopValue fungal(double d) {
        return new GoopValue(fungalGoop, d);
    }

    public static GoopValue regal(double d) {
        return new GoopValue(regalGoop, d);
    }

    public static GoopValue vital(double d) {
        return new GoopValue(vitalGoop, d);
    }

    public static GoopValue metal(double i) {
        return new GoopValue(metalGoop, i);
    }

    public static GoopValue chromatic(double i) {
        return new GoopValue(chromaticGoop, i);
    }

    public static String name(IRecipe<?> key) { return name(key.getRecipeOutput()); }

    public static String name(ItemStack stack) {
        return name(stack.getItem());
    }

    public static String name(Item item) {
        return Objects.requireNonNull(item.getRegistryName()).toString();
    }

    public static ProgressState trackedPush(Map<String, GoopMapping> source, Map<String, GoopMapping> target) {

        // improved becomes true if any pushed mapping replaces an unknown, or any pushed mapping has a lower weight.
        boolean improved = false;
        for(Map.Entry<String, GoopMapping> e : source.entrySet()) {
            GoopMapping value = e.getValue();
            // Unknown mappings aren't helpful. Skip it.
            if (value.isUnknown()) {
                continue;
            }
            String key = e.getKey();
            if (!target.containsKey(key) || shouldPush(value, target.get(key))) {
                improved = true;
                target.put(key, value);
            }
        }

        return improved ? ProgressState.IMPROVED : ProgressState.STAGNANT;
    }

    public static boolean shouldPush(GoopMapping source, GoopMapping target) {
        if (target.isFixed()) {
            return false;
        }
        return target.isUnknown() || source.isStrongerThan(target) || (!target.isDenied() && source.isDenied());
    }

    public static boolean anyProgress(ProgressState... progressStates) {
        for(ProgressState state : progressStates) {
            if (state.equals(ProgressState.IMPROVED)) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack getSingleton(Item item) {
        return new ItemStack(item);
    }

    public static GoopValue molten(Item burnableItem)
    {
        ItemStack stack = getSingleton(burnableItem);
        int burnTime = ForgeHooks.getBurnTime(stack);
        if (burnTime == 0) {
            return molten(0d); // ignored on initialization
        }

        return molten((burnTime / BURN_TIME_PER_SMELTED_ITEM) * GoopMod.config.smeltingRatio());
    }

    public static GoopValue vital(Food foodItem)
    {
        // try rounding this a bit; food item weights are deterministic but the math causes some weird floats.
        double foodFormula = GoopMod.config.foodHungerRatio() * (double)foodItem.getHealing() + GoopMod.config.foodSaturationRatio() * (double)foodItem.getSaturation();
        double foodValue = round(foodFormula, 5);
        return vital (foodValue);
    }

    public static double round(double d, int precision) {
        return Math.round(d * Math.pow(10, precision)) / Math.pow(10, precision);
    }
}
