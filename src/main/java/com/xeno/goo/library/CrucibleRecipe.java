package com.xeno.goo.library;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class CrucibleRecipe {
	private final FluidStack goo;
	private final ItemStack catalyst;
	private final ItemStack output;

	public CrucibleRecipe(FluidStack goo, ItemStack catalyst, ItemStack output) {
		this.goo = goo;
		this.catalyst = catalyst;
		this.output = output;
	}

	public FluidStack gooInput() {
		return goo;
	}

	public ItemStack catalyst() {
		return catalyst;
	}

	public ItemStack itemOutput() {
		return output;
	}

	public ItemStack resultBasedOnFluidStack(FluidStack amountUsed) {
		int cycles = amountUsed.getAmount() / goo.getAmount();
		if (cycles <= 0) {
			return ItemStack.EMPTY;
		}

		return new ItemStack(output.getItem(), cycles);
	}
}
