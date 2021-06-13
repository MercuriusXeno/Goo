package com.xeno.goo.jei;

import com.xeno.goo.aequivaleo.GooConversionWrapper;
import mezz.jei.api.recipe.IFocus;
import net.minecraft.item.ItemStack;

import java.util.List;

public class SolidifierRecipe {
	private final List<GooIngredient> inputStacks;
	private final ItemStack outputStack;

	public SolidifierRecipe(ItemStack stack, GooConversionWrapper entry) {
		this.inputStacks = entry.goo();
		this.outputStack = stack;
	}

	public static <T, V> int sortByInputFocus(T a, T b, IFocus<V> focus) {
		if (!(focus.getValue() instanceof GooIngredient)) {
			return 0;
		}
		SolidifierRecipe l = (SolidifierRecipe)a;
		SolidifierRecipe r = (SolidifierRecipe)b;
		GooIngredient f = (GooIngredient)focus.getValue();

		int la = l.inputStacks.stream().filter(c -> c.fluidKey().equals(f.fluidKey())).findFirst().get().amount();
		int ra = r.inputStacks.stream().filter(c -> c.fluidKey().equals(f.fluidKey())).findFirst().get().amount();

		return Integer.compare(la, ra);
	}

	public List<GooIngredient> inputs() {
		return inputStacks;
	}

	public ItemStack output() {
		return outputStack;
	}
}
