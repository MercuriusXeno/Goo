package jei;

import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class GooifierRecipe {
	private final List<GooIngredient> outputStacks;
	private final ItemStack inputStack;

	public GooifierRecipe(ItemStack stack, GooConversionWrapper entry) {
		this.inputStack = stack;
		this.outputStacks = entry.goo();
	}

	public ItemStack input() {
		return inputStack;
	}

	public List<GooIngredient> outputs() {
		return outputStacks;
	}

}
