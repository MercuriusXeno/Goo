package com.xeno.goo.library;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;
import java.util.List;

public class CrucibleRecipe {
	private final List<FluidStack> goo;
	private final ItemStack catalyst;
	private final ItemStack output;

	public CrucibleRecipe(ItemStack catalyst, ItemStack output, List<FluidStack> goo) {
		this.goo = goo;
		this.catalyst = catalyst;
		this.output = output;
	}

	public CrucibleRecipe(ItemStack catalyst, ItemStack output, FluidStack... goo) {
		this.goo = Arrays.asList(goo);
		this.catalyst = catalyst;
		this.output = output;
	}

	public List<FluidStack> gooInput() {
		return goo;
	}

	public ItemStack catalyst() {
		return catalyst;
	}

	public ItemStack itemOutput() {
		return output;
	}
}
