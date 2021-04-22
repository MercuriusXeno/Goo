package com.xeno.goo.util;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public class FluidHandlerWrapper implements IFluidHandler {

	protected final IFluidHandler handler;

	public FluidHandlerWrapper(IFluidHandler handler) {

		this.handler = handler;
	}

	@Override
	public int getTanks() {

		return handler.getTanks();
	}

	@Nonnull
	@Override
	public FluidStack getFluidInTank(int tank) {

		return handler.getFluidInTank(tank);
	}

	@Override
	public int getTankCapacity(int tank) {

		return handler.getTankCapacity(tank);
	}

	@Override
	public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {

		return handler.isFluidValid(tank, stack);
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {

		return handler.fill(resource, action);
	}

	@Nonnull
	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {

		return handler.drain(resource, action);
	}

	@Nonnull
	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {

		return handler.drain(maxDrain, action);
	}
}
