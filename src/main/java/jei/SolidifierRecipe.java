package jei;

import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.setup.Registry;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class SolidifierRecipe {
	private final List<GooIngredient> inputStacks;
	private final ItemStack outputStack;

	public SolidifierRecipe(ItemStack stack, GooConversionWrapper entry) {
		this.inputStacks = entry.goo();
		this.outputStack = stack;
	}

	public List<GooIngredient> inputs() {
		return inputStacks;
	}

	public ItemStack output() {
		return outputStack;
	}
}
