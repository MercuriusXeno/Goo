package com.xeno.goo.library;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.*;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.ForgeHooks;

import java.util.Map;
import java.util.Objects;

public class EntryHelper
{
    public static final String ashenGoo = Objects.requireNonNull(Registry.ASHEN_GOO.get().getRegistryName()).toString();
    public static final String aquaticGoo = Objects.requireNonNull(Registry.AQUATIC_GOO.get().getRegistryName()).toString();
    public static final String chromaticGoo = Objects.requireNonNull(Registry.CHROMATIC_GOO.get().getRegistryName()).toString();
    public static final String crystalGoo = Objects.requireNonNull(Registry.CRYSTAL_GOO.get().getRegistryName()).toString();
    public static final String earthenGoo = Objects.requireNonNull(Registry.EARTHEN_GOO.get().getRegistryName()).toString();
    public static final String esotericGoo = Objects.requireNonNull(Registry.ESOTERIC_GOO.get().getRegistryName()).toString();
    public static final String floralGoo = Objects.requireNonNull(Registry.FLORAL_GOO.get().getRegistryName()).toString();
    public static final String faunalGoo = Objects.requireNonNull(Registry.FAUNAL_GOO.get().getRegistryName()).toString();
    public static final String fungalGoo = Objects.requireNonNull(Registry.FUNGAL_GOO.get().getRegistryName()).toString();
    public static final String metalGoo = Objects.requireNonNull(Registry.METAL_GOO.get().getRegistryName()).toString();
    public static final String moltenGoo = Objects.requireNonNull(Registry.MOLTEN_GOO.get().getRegistryName()).toString();
    public static final String obsidianGoo = Objects.requireNonNull(Registry.OBSIDIAN_GOO.get().getRegistryName()).toString();
    public static final String regalGoo = Objects.requireNonNull(Registry.REGAL_GOO.get().getRegistryName()).toString();
    public static final String snowGoo = Objects.requireNonNull(Registry.SNOW_GOO.get().getRegistryName()).toString();
    public static final String vitalGoo = Objects.requireNonNull(Registry.VITAL_GOO.get().getRegistryName()).toString();
//    private static final int TRUNCATE_MAGNITUDE = 10000;
    private static final double BURN_TIME_PER_SMELTED_ITEM = 200d;

    public static GooValue ashen(double d) { return new GooValue(ashenGoo, d); }

    public static GooValue aquatic(double d) {
        return new GooValue(aquaticGoo, d);
    }

    public static GooValue chromatic(double i) {
        return new GooValue(chromaticGoo, i);
    }

    public static GooValue crystal(double i) {
        return new GooValue(crystalGoo, i);
    }

    public static GooValue earthen(double d) { return new GooValue(earthenGoo, d); }

    public static GooValue esoteric(double d) {
        return new GooValue(esotericGoo, d);
    }

    public static GooValue faunal(double d) { return new GooValue(faunalGoo, d); }

    public static GooValue floral(double d) {
        return new GooValue(floralGoo, d);
    }

    public static GooValue fungal(double d) {
        return new GooValue(fungalGoo, d);
    }

    public static GooValue metal(double i) {
        return new GooValue(metalGoo, i);
    }

    public static GooValue molten(double d) {
        return new GooValue(moltenGoo, d);
    }

    public static GooValue obsidian(double d) { return new GooValue(obsidianGoo, d); }

    public static GooValue regal(double d) { return new GooValue(regalGoo, d); }

    public static GooValue snow(double d) { return new GooValue(snowGoo, d); }

    public static GooValue vital(double d) {
        return new GooValue(vitalGoo, d);
    }

    public static String name(IRecipe<?> key) { return name(key.getRecipeOutput()); }

    public static String name(ItemStack stack) {
        return name(stack.getItem());
    }

    public static String name(Item item) {
        return Objects.requireNonNull(item.getRegistryName()).toString();
    }

    public static ProgressState trackedPush(Map<String, GooEntry> source, Map<String, GooEntry> target) {

        // improved becomes true if any pushed mapping replaces an unknown, or any pushed mapping has a lower weight.
        boolean improved = false;
        for(Map.Entry<String, GooEntry> e : source.entrySet()) {
            GooEntry value = e.getValue();
            String key = e.getKey();
            // Unknown mappings aren't helpful, but they are better than NO mapping.
            // push unknowns ONLY IF the target has nothing.
            if (value.isUnknown() && target.containsKey(key)) {
                continue;
            }
            if (!target.containsKey(key) || shouldPush(value, target.get(key))) {
                improved = true;
                target.put(key, value);
            }
        }

        return improved ? ProgressState.IMPROVED : ProgressState.STAGNANT;
    }

    public static boolean shouldPush(GooEntry source, GooEntry target) {
        if (target.isFixed()) {
            return false;
        }
        return target.isUnknown() || source.isStrongerThan(target) || (!target.isDenied() && source.isDenied());
    }

    public static ItemStack getSingleton(Item item) {
        return new ItemStack(item);
    }

    public static GooValue molten(Item burnableItem)
    {
        ItemStack stack = getSingleton(burnableItem);
        int burnTime = ForgeHooks.getBurnTime(stack);
        if (burnTime == 0) {
            return molten(0d); // ignored on initialization
        }

        return molten(((double)burnTime / BURN_TIME_PER_SMELTED_ITEM) * GooMod.config.smeltingRatio());
    }

    public static GooValue vital(Food foodItem)
    {
        // try rounding this a bit; food item weights are deterministic but the math causes some weird floats.
        double foodFormula = GooMod.config.foodHungerRatio() * (double)foodItem.getHealing() + GooMod.config.foodSaturationRatio() * (double)foodItem.getSaturation();
        double foodValue = round(foodFormula, 5);

        return vital (foodValue);
    }

    public static double round(double d, int precision) {
        return Math.round(d * Math.pow(10, precision)) / Math.pow(10, precision);
    }
}
