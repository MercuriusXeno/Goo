package com.xeno.goo.util;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public class FluidHandlerTankWrapper extends FluidHandlerWrapper {

	protected final int tank;

	public FluidHandlerTankWrapper(IFluidHandler t, int sideTank) {

		super(t);
		this.tank = sideTank;
	}

	@Override
	public int getTanks() {

		return 1;
	}

	@Nonnull
	@Override
	public FluidStack getFluidInTank(int tank) {

		return tank == 0 ? handler.getFluidInTank(this.tank) : FluidStack.EMPTY;
	}

	@Override
	public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {

		if (tank != 0)
			return false;

		return super.isFluidValid(tank, stack);
	}

	@Override
	public int getTankCapacity(int tank) {

		if (tank > 0) {
			return 0;
		}
		return super.getTankCapacity(this.tank);
	}

	@Override
	public int fill(FluidStack resource, IFluidHandler.FluidAction action) {

		if (!isFluidValid(0, resource))
			return 0;

		return super.fill(resource, action);
	}

	@Nonnull
	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {

		if (!handler.getFluidInTank(tank).isFluidEqual(resource))
			return FluidStack.EMPTY;

		return handler.drain(resource, action);
	}

	@Nonnull
	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {

		FluidStack s = handler.getFluidInTank(tank);
		FluidStack result = new FluidStack(s.getFluid(), Math.min(s.getAmount(), maxDrain));
		return handler.drain(result, action);
	}
}
