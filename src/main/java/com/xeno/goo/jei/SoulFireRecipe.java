package com.xeno.goo.jei;

import net.minecraft.item.ItemStack;

public class SoulFireRecipe {
	private final ItemStack inputStack;
	private final ItemStack outputStack;

	public SoulFireRecipe(ItemStack in, ItemStack out) {
		inputStack = in;
		outputStack = out;
	}

	public ItemStack input() {
		return inputStack;
	}

	public ItemStack output() {
		return outputStack;
	}
}
