package com.xeno.goo.jei;

import net.minecraft.item.ItemStack;

public class JeiCrucibleRecipe {
	private final ItemStack inputStack;
	private final GooIngredient inputGoo;
	private final ItemStack outputStack;

	public JeiCrucibleRecipe(ItemStack in, GooIngredient goo, ItemStack out) {
		inputStack = in;
		inputGoo = goo;
		outputStack = out;
	}

	public ItemStack itemInput() {
		return inputStack;
	}
	public GooIngredient gooInput() {
		return inputGoo;
	}

	public ItemStack output() {
		return outputStack;
	}
}
