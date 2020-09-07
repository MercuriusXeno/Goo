package com.xeno.goo.library;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.fluid.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class MixerRecipes
{
    private static final List<MixerRecipe> recipes = new ArrayList<>();

    private static FluidStack fluid(Supplier<GooFluid> fluidSupplier) {
        return fluid(fluidSupplier, 1);
    }

    public static List<MixerRecipe> recipes() {
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
        recipes.add(new MixerRecipe(fluid(Registry.CRYSTAL_GOO, 1), fluid(Registry.OBSIDIAN_GOO, 60), fluid(Registry.WEIRD_GOO)));
        recipes.add(new MixerRecipe(fluid(Registry.EARTHEN_GOO, 1), fluid(Registry.AQUATIC_GOO, 1), fluid(Registry.DECAY_GOO)));
        recipes.add(new MixerRecipe(fluid(Registry.ENERGETIC_GOO, 1), fluid(Registry.MOLTEN_GOO, 60), fluid(Registry.DECAY_GOO)));
        recipes.add(new MixerRecipe(fluid(Registry.METAL_GOO, 1), fluid(Registry.REGAL_GOO), fluid(Registry.DECAY_GOO)));
        recipes.add(new MixerRecipe(fluid(Registry.OBSIDIAN_GOO, 1), fluid(Registry.AQUATIC_GOO), fluid(Registry.MOLTEN_GOO)));
        recipes.add(new MixerRecipe(fluid(Registry.REGAL_GOO, 1), fluid(Registry.CRYSTAL_GOO), fluid(Registry.DECAY_GOO)));
        recipes.add(new MixerRecipe(fluid(Registry.SNOW_GOO, 1), fluid(Registry.AQUATIC_GOO), fluid(Registry.LOGIC_GOO)));
        recipes.add(new MixerRecipe(fluid(Registry.SLIME_GOO, 1), fluid(Registry.VITAL_GOO), fluid(Registry.DECAY_GOO)));
        recipes.add(new MixerRecipe(fluid(Registry.WEIRD_GOO, 1), fluid(Registry.SLIME_GOO), fluid(Registry.DECAY_GOO)));
    }

    public static MixerRecipe getRecipe(FluidStack input1, FluidStack input2)
    {
        Optional<MixerRecipe> match =  recipes().stream().filter((r) -> r.inputs().containsKey(input1.getFluid()) && r.inputs().containsKey(input2.getFluid())).findFirst();
        return match.orElse(null);
    }
}
