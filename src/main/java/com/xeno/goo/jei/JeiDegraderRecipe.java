package com.xeno.goo.jei;

public class JeiDegraderRecipe {
	private final GooIngredient outputStack;
	private final GooIngredient inputStack;

	public JeiDegraderRecipe(GooIngredient a, GooIngredient b) {
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
