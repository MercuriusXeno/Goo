package jei;

import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.setup.Registry;
import mezz.jei.api.recipe.IFocus;
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

	public static <T, V> int sortByOutputFocus(T a, T b, IFocus<V> focus) {
		if (!(focus.getValue() instanceof GooIngredient)) {
			return 0;
		}
		GooifierRecipe l = (GooifierRecipe)a;
		GooifierRecipe r = (GooifierRecipe)b;
		GooIngredient f = (GooIngredient)focus.getValue();

		int la = l.outputStacks.stream().filter(c -> c.fluidKey().equals(f.fluidKey())).findFirst().get().amount();
		int ra = r.outputStacks.stream().filter(c -> c.fluidKey().equals(f.fluidKey())).findFirst().get().amount();

		return Integer.compare(la, ra);
	}

	public ItemStack input() {
		return inputStack;
	}

	public List<GooIngredient> outputs() {
		return outputStacks;
	}

}
