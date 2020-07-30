package com.xeno.goop.library;

import com.xeno.goop.setup.Registry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.Tuple;

import java.util.Map;
import java.util.Objects;

public class Helper {
    public static final String volatileGoop = Objects.requireNonNull(Registry.VOLATILE_GOOP.get().getRegistryName()).toString();
    public static final String earthenGoop = Objects.requireNonNull(Registry.EARTHEN_GOOP.get().getRegistryName()).toString();
    public static final String aquaticGoop = Objects.requireNonNull(Registry.AQUATIC_GOOP.get().getRegistryName()).toString();
    public static final String esotericGoop = Objects.requireNonNull(Registry.ESOTERIC_GOOP.get().getRegistryName()).toString();
    public static final String floralGoop = Objects.requireNonNull(Registry.FLORAL_GOOP.get().getRegistryName()).toString();
    public static final String faunalGoop = Objects.requireNonNull(Registry.FAUNAL_GOOP.get().getRegistryName()).toString();
    public static final String fungalGoop = Objects.requireNonNull(Registry.FUNGAL_GOOP.get().getRegistryName()).toString();
    public static final String regalGoop = Objects.requireNonNull(Registry.REGAL_GOOP.get().getRegistryName()).toString();

    public static GoopValue earthen(int i) {
        return new GoopValue(earthenGoop, i);

    }
    public static GoopValue burning(int i) {
        return new GoopValue(volatileGoop, i);
    }

    public static GoopValue aquatic(int i) {
        return new GoopValue(aquaticGoop, i);
    }

    public static GoopValue esoteric(int i) {
        return new GoopValue(esotericGoop, i);
    }

    public static GoopValue floral(int i) {
        return new GoopValue(floralGoop, i);
    }

    public static GoopValue faunal(int i) {
        return new GoopValue(faunalGoop, i);
    }

    public static GoopValue fungal(int i) {
        return new GoopValue(fungalGoop, i);
    }

    public static GoopValue regal(int i) {
        return new GoopValue(regalGoop, i);
    }

    public static String name(IRecipe<?> key) {
        return name(key.getRecipeOutput());
    }

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
        return target.isUnknown() || source.isStrongerThan(target) || (!target.isDenied() && source.isDenied());
    }

    public static boolean anyProgress(ProgressState... progressStates) {
        for(ProgressState state : progressStates) {
            if (state == ProgressState.IMPROVED) {
                return true;
            }
        }
        return false;
    }
}
