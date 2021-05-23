package jei;

import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class GooifierRecipe {
	private final List<FluidStack> outputStacks;
	private final ItemStack inputStack;

	public GooifierRecipe(ItemStack stack, GooEntry entry) {
		this.inputStack = stack;
		this.outputStacks = new ArrayList<>();
		for (GooValue value : entry.values()) {
			this.outputStacks.add(new FluidStack(Registry.getFluid(value.getFluidResourceLocation()), (int)Math.floor(value.amount())));
		}
	}

	public ItemStack input() {
		return inputStack;
	}

	public List<FluidStack> outputs() {
		return outputStacks;
	}


}
