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
        addRecipe(new MixerRecipe(fluid(Registry.CRYSTAL_GOO, 1), fluid(Registry.OBSIDIAN_GOO, 8), fluid(Registry.WEIRD_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.EARTHEN_GOO, 1), fluid(Registry.AQUATIC_GOO, 1), fluid(Registry.DECAY_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.ENERGETIC_GOO, 1), fluid(Registry.MOLTEN_GOO, 8), fluid(Registry.DECAY_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.METAL_GOO, 1), fluid(Registry.REGAL_GOO), fluid(Registry.DECAY_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.OBSIDIAN_GOO, 1), fluid(Registry.AQUATIC_GOO), fluid(Registry.MOLTEN_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.REGAL_GOO, 1), fluid(Registry.CRYSTAL_GOO), fluid(Registry.DECAY_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.SNOW_GOO, 1), fluid(Registry.AQUATIC_GOO), fluid(Registry.LOGIC_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.SLIME_GOO, 1), fluid(Registry.VITAL_GOO), fluid(Registry.DECAY_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.WEIRD_GOO, 1), fluid(Registry.SLIME_GOO), fluid(Registry.DECAY_GOO)));
    }

    private static void addRecipe(MixerRecipe mixerRecipe)
    {
        if (getRecipe(mixerRecipe.inputs().get(0), mixerRecipe.inputs().get(1)) == null) {
            recipes().add(mixerRecipe);
        }
    }

    public static MixerRecipe getRecipe(FluidStack input1, FluidStack input2)
    {
        if (recipes().size() == 0) {
            init();
        }
        if (input1 == null || input2 == null || input1.isEmpty() || input2.isEmpty()) {
            return null;
        }
        Optional<MixerRecipe> match =  recipes().stream().filter((r) -> r != null && r.inputs() != null && r.inputs().stream().allMatch(i -> i.isFluidEqual(input1) || i.isFluidEqual(input2))).findFirst();
        return match.orElse(null);
    }

    public static boolean isAnyRecipe(FluidStack stack)
    {
        if (recipes().size() == 0) {
            init();
        }
        for(MixerRecipe r : recipes()) {
            for (FluidStack f : r.inputs()) {
                if (f.isFluidEqual(stack)) {
                    return true;
                }
            }
        }
        return false;
    }
}
