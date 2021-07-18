package com.xeno.goo.library;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CrucibleRecipes {
	private static boolean isInitialized = false;
	private static final List<CrucibleRecipe> recipes = new ArrayList<>();

	public static List<CrucibleRecipe> recipes() {
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
		addRecipe(new CrucibleRecipe(
				new ItemStack(ItemsRegistry.NETHERITE_ASH.get()),
				new ItemStack(ItemsRegistry.PASSIVATED_AMALGAM.get()),
				fluid(Registry.METAL_GOO, 72)
		));
	}

	private static void addRecipe(CrucibleRecipe crucibleRecipe)
	{
		recipes().add(crucibleRecipe);
	}
}
