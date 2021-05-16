package com.xeno.goo.util;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public interface ISidedFluidHandler {

	/**
	 * Fills fluid into internal tanks, distribution is left entirely to the IFluidHandler.
	 *
	 * @param tank
	 * 		Tank to fill
	 * @param resource
	 * 		FluidStack representing the Fluid and maximum amount of fluid to be filled.
	 * @param action
	 * 		If SIMULATE, fill will only be simulated.
	 *
	 * @return Amount of resource that was (or would have been, if simulated) filled.
	 */
	int fill(int tank, FluidStack resource, FluidAction action);
}
