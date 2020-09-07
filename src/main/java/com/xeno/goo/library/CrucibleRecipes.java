package com.xeno.goo.library;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class CrucibleRecipes
{
    private static final List<CrucibleRecipe> recipes = new ArrayList<>();

    private static FluidStack fluid(Supplier<GooFluid> fluidSupplier) {
        return fluid(fluidSupplier, 1);
    }

    public static List<CrucibleRecipe> recipes() {
        return recipes;
    }

    private static FluidStack fluid(Supplier<GooFluid> fluidSupplier, int amount) {
        if (fluidSupplier == null) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluidSupplier.get(), amount);
    }

    public static void init() {
        recipes.clear();
        recipes.add(new CrucibleRecipe(fluid(Registry.AQUATIC_GOO, 1), fluid(Registry.SNOW_GOO)));
        recipes.add(new CrucibleRecipe(fluid(Registry.DECAY_GOO, 1), fluid(Registry.FAUNAL_GOO)));
        recipes.add(new CrucibleRecipe(fluid(Registry.DECAY_GOO, 1), fluid(Registry.FUNGAL_GOO)));
        recipes.add(new CrucibleRecipe(fluid(Registry.DECAY_GOO, 1), fluid(Registry.FLORAL_GOO)));
        recipes.add(new CrucibleRecipe(fluid(Registry.LOGIC_GOO, 1), fluid(Registry.METAL_GOO)));
        recipes.add(new CrucibleRecipe(fluid(Registry.MOLTEN_GOO, 1), fluid(Registry.EARTHEN_GOO)));
        recipes.add(new CrucibleRecipe(fluid(Registry.VITAL_GOO, 1), fluid(Registry.HONEY_GOO)));
    }

    public static CrucibleRecipe getRecipe(FluidStack input)
    {
        if (input == null || input.isEmpty()) {
            return null;
        }
        Optional<CrucibleRecipe> match =  recipes().stream().filter((r) -> r.input().isFluidEqual(input)).findFirst();
        return match.orElse(null);
    }
}
