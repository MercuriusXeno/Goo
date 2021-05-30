package jei;

public class JeiMixerRecipe {
	private final GooIngredient outputStack;
	private final GooIngredient[] inputStacks;

	public JeiMixerRecipe(GooIngredient a, GooIngredient b, GooIngredient c) {
		this.inputStacks = new GooIngredient[] {a, b};
		this.outputStack = c;
	}

	public GooIngredient[] inputs() {
		return inputStacks;
	}

	public GooIngredient output() {
		return outputStack;
	}
}
