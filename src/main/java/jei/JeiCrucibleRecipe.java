package jei;

import mezz.jei.api.recipe.IFocus;
import net.minecraft.item.ItemStack;

import java.util.List;

public class JeiCrucibleRecipe {
	private final GooIngredient outputStack;
	private final GooIngredient inputStack;

	public JeiCrucibleRecipe(GooIngredient a, GooIngredient b) {
		this.inputStack = a;
		this.outputStack = b;
	}

	public GooIngredient input() {
		return inputStack;
	}

	public GooIngredient output() {
		return outputStack;
	}
}
