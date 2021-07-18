package com.xeno.goo.library;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;
import java.util.List;

public class CrucibleRecipe {
	private final FluidStack goo;
	private final ItemStack catalyst;
	private final ItemStack output;

	public CrucibleRecipe(ItemStack catalyst, ItemStack output, FluidStack goo) {
		this.goo = goo;
		this.catalyst = catalyst;
		this.output = output;
	}

	public FluidStack gooInput() {
		return goo;
	}

	public ItemStack substrateItem() {
		return catalyst;
	}

	public ItemStack itemOutput() {
		return output;
	}
}
