package com.xeno.goo.jei;

import net.minecraft.item.ItemStack;

public class JeiCrucibleRecipe {
	private final ItemStack catalyst;
	private final GooIngredient goo;
	private final ItemStack output;

	public JeiCrucibleRecipe(ItemStack catalyst, GooIngredient goo, ItemStack output) {
		this.catalyst = catalyst;
		this.goo = goo;
		this.output = output;
	}

	public ItemStack itemInput() {
		return catalyst;
	}

	public GooIngredient gooInput() {
		return goo;
	}

	public ItemStack output() {
		return output;
	}
}
