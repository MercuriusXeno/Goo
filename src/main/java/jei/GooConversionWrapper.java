package jei;

import com.xeno.goo.aequivaleo.GooEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class GooConversionWrapper {
	private final ItemStack item;
	private final List<GooIngredient> goo;
	private final boolean isSolidifiable;
	private final boolean isGooifiable;

	public GooConversionWrapper(ItemStack i, GooEntry g) {
		this.item = i;
		this.goo = g.inputsAsGooIngredient();
		this.isSolidifiable = !g.deniesSolidification();
		this.isGooifiable = !g.isEmpty() && !g.isUnusable();
	}

	public GooConversionWrapper(Item contents, GooEntry g) {
		this.item = new ItemStack(contents);
		this.goo = g.inputsAsGooIngredient();
		this.isSolidifiable = !g.deniesSolidification();
		this.isGooifiable = !g.isEmpty() && !g.isUnusable();
	}

	public ItemStack item() {
		return item;
	}

	public List<GooIngredient> goo() {
		return goo;
	}

	public boolean isSolidifiable() {
		return isSolidifiable;
	}

	public boolean isGooifiable() {
		return isGooifiable;
	}

	public SolidifierRecipe toSolidifierRecipe() {
		return new SolidifierRecipe(this.item, this);
	}

	public GooifierRecipe toGooifierRecipe() {
		return new GooifierRecipe(this.item, this);
	}
}
