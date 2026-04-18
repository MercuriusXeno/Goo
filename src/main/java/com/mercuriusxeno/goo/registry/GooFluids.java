package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.fluid.GooFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registers source and flowing {@link Fluid} instances for each goo type.
 * Uses non-flowing {@link GooFluid} variants so goo stays where placed.
 */
public final class GooFluids {
    /** Deferred register for vanilla fluids. */
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Goo.MODID);

    /** Source fluid per goo type. */
    public static final Map<GooType, DeferredHolder<Fluid, GooFluid.Source>> SOURCES =
            new EnumMap<>(GooType.class);

    /** Flowing fluid per goo type. */
    public static final Map<GooType, DeferredHolder<Fluid, GooFluid.Flowing>> FLOWING =
            new EnumMap<>(GooType.class);

    private GooFluids() {}

    static {
        for (GooType type : GooType.values()) {
            String id = type.getId();
            SOURCES.put(type, FLUIDS.register(id + "_goo",
                    () -> new GooFluid.Source(fluidProperties(type))));
            FLOWING.put(type, FLUIDS.register(id + "_goo_flowing",
                    () -> new GooFluid.Flowing(fluidProperties(type))));
        }
    }

    /**
     * Looks up the GooType for a given fluid instance.
     * Checks both source and flowing maps.
     *
     * @param fluid the fluid instance to look up
     * @return the matching GooType, or null if the fluid is not a goo fluid
     */
    @Nullable
    public static GooType getTypeFromFluid(Fluid fluid) {
        for (Map.Entry<GooType, DeferredHolder<Fluid, GooFluid.Source>> entry : SOURCES.entrySet()) {
            if (entry.getValue().get() == fluid) {
                return entry.getKey();
            }
        }
        return getTypeFromFlowing(fluid);
    }

    /**
     * Checks the flowing fluid map for a match.
     *
     * @param fluid the fluid instance to look up
     * @return the matching GooType, or null if not found
     */
    @Nullable
    private static GooType getTypeFromFlowing(Fluid fluid) {
        for (Map.Entry<GooType, DeferredHolder<Fluid, GooFluid.Flowing>> entry : FLOWING.entrySet()) {
            if (entry.getValue().get() == fluid) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Builds the shared fluid properties linking source, flowing, and fluid type.
     * Block association is omitted here to avoid circular static init with GooBlocks.
     * The LiquidBlock constructor receives the fluid directly instead.
     *
     * @param type the goo type
     * @return the configured fluid properties
     */
    private static BaseFlowingFluid.Properties fluidProperties(GooType type) {
        return new BaseFlowingFluid.Properties(
                GooFluidTypes.TYPES.get(type),
                SOURCES.get(type),
                FLOWING.get(type)
        ).bucket(GooItems.BUCKETS.get(type))
         .block(GooBlocks.FLUID_BLOCKS.get(type));
    }
}
