package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registers one {@link FluidType} per goo type with NeoForge's fluid type registry.
 */
public class GooFluidTypes {

    /** Deferred register for NeoForge fluid types. */
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, Goo.MODID);

    /** One fluid type per goo type, keyed by enum. */
    public static final Map<GooType, DeferredHolder<FluidType, FluidType>> TYPES =
            new EnumMap<>(GooType.class);

    static {
        for (GooType type : GooType.values()) {
            TYPES.put(type, FLUID_TYPES.register(type.getId() + "_goo",
                    () -> new FluidType(buildProperties(type))));
        }
    }

    /** Builds fluid type properties tuned to the goo type's character. */
    private static FluidType.Properties buildProperties(GooType type) {
        return FluidType.Properties.create()
                .density(density(type))
                .viscosity(viscosity(type))
                .temperature(temperature(type))
                .canExtinguish(type == GooType.FROST || type == GooType.TYPHOON);
    }

    /** Returns density for the given goo type (kg/m^3 scale, water = 1000). */
    private static int density(GooType type) {
        return switch (type) {
            case METAL -> 2500;
            case ROCK -> 2000;
            case NETHER, BLAZE -> 1500;
            case CRYSTAL, ENDER -> 1200;
            default -> 1000;
        };
    }

    /** Returns viscosity for the given goo type (higher = thicker, water = 1000). */
    private static int viscosity(GooType type) {
        return switch (type) {
            case AEON, SHROOM -> 3000;
            case METAL, ROCK -> 2000;
            case HEX, NETHER -> 1500;
            case BLAZE, FROST -> 800;
            case TYPHOON -> 500;
            default -> 1000;
        };
    }

    /** Returns temperature for the given goo type (Kelvin, room temp = 300). */
    private static int temperature(GooType type) {
        return switch (type) {
            case BLAZE -> 1300;
            case NETHER -> 900;
            case FROST -> 200;
            case GLOW -> 400;
            default -> 300;
        };
    }
}
