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

    // ── Density values (kg/m^3, water = 1000) ──
    private static final int DENSITY_METAL = 2500;
    private static final int DENSITY_ROCK = 2000;
    private static final int DENSITY_NETHER_BLAZE = 1500;
    private static final int DENSITY_CRYSTAL_ENDER = 1200;
    private static final int DENSITY_DEFAULT = 1000;

    // ── Viscosity values (higher = thicker, water = 1000) ──
    private static final int VISCOSITY_AEON_SHROOM = 3000;
    private static final int VISCOSITY_METAL_ROCK = 2000;
    private static final int VISCOSITY_HEX_NETHER = 1500;
    private static final int VISCOSITY_DEFAULT = 1000;
    private static final int VISCOSITY_BLAZE_FROST = 800;
    private static final int VISCOSITY_TYPHOON = 500;

    // ── Temperature values (Kelvin, room temp = 300) ──
    private static final int TEMP_BLAZE = 1300;
    private static final int TEMP_NETHER = 900;
    private static final int TEMP_GLOW = 400;
    private static final int TEMP_DEFAULT = 300;
    private static final int TEMP_FROST = 200;

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

    /**
     * Builds fluid type properties tuned to the goo type's character.
     *
     * @param type the goo type
     * @return the configured fluid type properties
     */
    private static FluidType.Properties buildProperties(GooType type) {
        return FluidType.Properties.create()
                .density(density(type))
                .viscosity(viscosity(type))
                .temperature(temperature(type))
                .canExtinguish(type == GooType.FROST || type == GooType.TYPHOON);
    }

    /**
     * Returns density for the given goo type (kg/m^3 scale, water = 1000).
     *
     * @param type the goo type
     * @return the density value
     */
    private static int density(GooType type) {
        return switch (type) {
            case METAL -> DENSITY_METAL;
            case ROCK -> DENSITY_ROCK;
            case NETHER, BLAZE -> DENSITY_NETHER_BLAZE;
            case CRYSTAL, ENDER -> DENSITY_CRYSTAL_ENDER;
            default -> DENSITY_DEFAULT;
        };
    }

    /**
     * Returns viscosity for the given goo type (higher = thicker, water = 1000).
     *
     * @param type the goo type
     * @return the viscosity value
     */
    /** Per-type viscosity overrides; types not present default to VISCOSITY_DEFAULT. */
    private static final Map<GooType, Integer> VISCOSITY_MAP = new EnumMap<>(Map.ofEntries(
            Map.entry(GooType.AEON, VISCOSITY_AEON_SHROOM),
            Map.entry(GooType.SHROOM, VISCOSITY_AEON_SHROOM),
            Map.entry(GooType.METAL, VISCOSITY_METAL_ROCK),
            Map.entry(GooType.ROCK, VISCOSITY_METAL_ROCK),
            Map.entry(GooType.HEX, VISCOSITY_HEX_NETHER),
            Map.entry(GooType.NETHER, VISCOSITY_HEX_NETHER),
            Map.entry(GooType.BLAZE, VISCOSITY_BLAZE_FROST),
            Map.entry(GooType.FROST, VISCOSITY_BLAZE_FROST),
            Map.entry(GooType.TYPHOON, VISCOSITY_TYPHOON)));

    private static int viscosity(GooType type) {
        return VISCOSITY_MAP.getOrDefault(type, VISCOSITY_DEFAULT);
    }

    /**
     * Returns temperature for the given goo type (Kelvin, room temp = 300).
     *
     * @param type the goo type
     * @return the temperature in Kelvin
     */
    private static int temperature(GooType type) {
        return switch (type) {
            case BLAZE -> TEMP_BLAZE;
            case NETHER -> TEMP_NETHER;
            case FROST -> TEMP_FROST;
            case GLOW -> TEMP_GLOW;
            default -> TEMP_DEFAULT;
        };
    }
}
