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
	private final List<FluidStack> inputStacks;
	private final ItemStack outputStack;

	public SolidifierRecipe(ItemStack stack, GooEntry entry) {
		this.inputStacks = new ArrayList<>();
		for (GooValue value : entry.values()) {
			this.inputStacks.add(new FluidStack(Registry.getFluid(value.getFluidResourceLocation()), (int)Math.floor(value.amount())));
		}
		this.outputStack = stack;
	}

	public List<FluidStack> inputs() {
		return inputStacks;
	}

	public ItemStack output() {
		return outputStack;
	}
}
