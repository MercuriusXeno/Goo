package com.xeno.goo.util;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.IntSupplier;

public class MultiGooTankClient extends MultiGooTank {

	public MultiGooTankClient(IntSupplier capacity) {

		super(capacity);
	}

	@Override
	protected void readFromNBTInternal(CompoundNBT nbt) {

		final ListNBT tankList = nbt.getList("Tanks", NBT.TAG_COMPOUND);

		FluidStack[] tanks = new FluidStack[tankList.size()];
		HashMap<Fluid, FluidStack> contents = new HashMap<>();

		int count = 0, amt = 0;

		for (int i = 0, e = tankList.size(); i < e; ++i) {
			FluidStack tank = FluidStack.loadFluidStackFromNBT(tankList.getCompound(i));
			if (!contents.containsKey(tank.getRawFluid()) && filter.test(tank)) {
				contents.put(tank.getRawFluid(), tanks[count++] = tank);
				amt += tank.getAmount();
			}
		}

		Arrays.sort(tanks, Comparator.comparingInt(FluidStack::getAmount));

		tankCount = count;
		amount = amt;
		this.tanks = tanks;
		this.contents = contents;
	}
}
