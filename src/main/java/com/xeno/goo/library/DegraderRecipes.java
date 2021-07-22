package com.xeno.goo.library;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class DegraderRecipes
{
    private static boolean isInitialized = false;

    private static final List<DegraderRecipe> recipes = new ArrayList<>();

    private static FluidStack fluid(Supplier<GooFluid> fluidSupplier) {
        return fluid(fluidSupplier, 1);
    }

    public static List<DegraderRecipe> recipes() {

        if (!isInitialized) {
            init();
        }
        return recipes;
    }

    private static FluidStack fluid(Supplier<GooFluid> fluidSupplier, int amount) {
        if (fluidSupplier == null) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluidSupplier.get(), amount);
    }

    public static void init() {
        isInitialized = true;
        recipes.clear();
        recipes.add(new DegraderRecipe(
                fluid(Registry.AQUATIC_GOO, 8),
                fluid(Registry.SNOW_GOO),
                fluid(Registry.MOLTEN_GOO)));
        recipes.add(new DegraderRecipe(
                fluid(Registry.DECAY_GOO, 2),
                fluid(Registry.CHROMATIC_GOO),
                fluid(Registry.DECAY_GOO)));
        recipes.add(new DegraderRecipe(
                fluid(Registry.DECAY_GOO, 2),
                fluid(Registry.FAUNAL_GOO),
                fluid(Registry.DECAY_GOO)));
        recipes.add(new DegraderRecipe(
                fluid(Registry.DECAY_GOO, 2),
                fluid(Registry.FUNGAL_GOO),
                fluid(Registry.DECAY_GOO)));
        recipes.add(new DegraderRecipe(
                fluid(Registry.DECAY_GOO, 2),
                fluid(Registry.FLORAL_GOO),
                fluid(Registry.DECAY_GOO)));
        recipes.add(new DegraderRecipe(
                fluid(Registry.LOGIC_GOO, 2),
                fluid(Registry.METAL_GOO),
                fluid(Registry.DECAY_GOO)));
        recipes.add(new DegraderRecipe(
                fluid(Registry.MOLTEN_GOO, 2),
                fluid(Registry.EARTHEN_GOO, 8),
                fluid(Registry.MOLTEN_GOO)));
        recipes.add(new DegraderRecipe(
                fluid(Registry.VITAL_GOO, 2),
                fluid(Registry.HONEY_GOO),
                fluid(Registry.DECAY_GOO)));
    }

    public static DegraderRecipe getRecipe(FluidStack input)
    {
        if (!isInitialized) {
            init();
        }
        if (input == null || input.isEmpty()) {
            return null;
        }
        Optional<DegraderRecipe> match =  recipes().stream().filter((r) -> r != null && r.input() != null && !r.input().isEmpty() && r.input().isFluidEqual(input)).findFirst();
        return match.orElse(null);
    }

    public static boolean isAnyRecipe(FluidStack stack)
    {
        if (recipes().size() == 0) {
            init();
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for(DegraderRecipe r : recipes()) {
            if (r.input().isFluidEqual(stack)) {
                return true;
            }
        }
        return false;
    }
}
