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
    private static boolean isInitialized = false;
    private static final List<MixerRecipe> recipes = new ArrayList<>();

    private static FluidStack fluid(Supplier<GooFluid> fluidSupplier) {
        return fluid(fluidSupplier, 1);
    }

    public static List<MixerRecipe> recipes() {
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
        addRecipe(new MixerRecipe(fluid(Registry.CHROMATIC_GOO), fluid(Registry.FLORAL_GOO), fluid(Registry.RADIANT_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.ENERGETIC_GOO), fluid(Registry.MOLTEN_GOO, 8), fluid(Registry.RADIANT_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.FAUNAL_GOO), fluid(Registry.LOGIC_GOO), fluid(Registry.VITAL_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.METAL_GOO), fluid(Registry.MOLTEN_GOO, 8), fluid(Registry.CRYSTAL_GOO, 1)));
        addRecipe(new MixerRecipe(fluid(Registry.METAL_GOO), fluid(Registry.REGAL_GOO), fluid(Registry.DECAY_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.REGAL_GOO), fluid(Registry.METAL_GOO), fluid(Registry.RADIANT_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.RADIANT_GOO), fluid(Registry.LOGIC_GOO), fluid(Registry.DECAY_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.SLIME_GOO), fluid(Registry.FLORAL_GOO, 8), fluid(Registry.DECAY_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.VITAL_GOO), fluid(Registry.RADIANT_GOO), fluid(Registry.LOGIC_GOO)));
        addRecipe(new MixerRecipe(fluid(Registry.WEIRD_GOO), fluid(Registry.SLIME_GOO, 8), fluid(Registry.ENERGETIC_GOO)));
    }

    private static void addRecipe(MixerRecipe mixerRecipe)
    {
        if (getRecipe(mixerRecipe.inputs().get(0), mixerRecipe.inputs().get(1)) == null) {
            recipes().add(mixerRecipe);
        }
    }

    public static MixerRecipe getRecipe(FluidStack input1, FluidStack input2)
    {
        if (!isInitialized) {
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
