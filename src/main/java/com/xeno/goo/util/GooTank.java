package com.xeno.goo.util;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.function.IntSupplier;

public class GooTank extends IGooTank {

	@Nonnull
	protected FluidStack tank = FluidStack.EMPTY;

	public GooTank(IntSupplier capacity) {

		super(capacity);
	}

	@Override
	protected void readFromNBTInternal(CompoundNBT nbt) {

		tank = FluidStack.loadFluidStackFromNBT(nbt);
	}

	@Override
	protected void writeToNBTInternal(CompoundNBT nbt) {

		tank.writeToNBT(nbt);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTanks() {

		return 1;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEmpty() {

		return tank.isEmpty();
	}

	@Override
	public int getTotalContents() {

		return tank.getAmount();
	}

	@Override
	public int getTotalCapacity() {

		return capacity.getAsInt();
	}

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack getFluidInTankInternal(int index) {

		if (index != 0)
			throw new IndexOutOfBoundsException("Index (" + index + ") is out of bounds (1)");
		return tank;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTankCapacity(int tank) {

		getFluidInTankInternal(tank);
		return capacity.getAsInt();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int fill(FluidStack resource, FluidAction action) {

		final int tankAmt;
		{
			final FluidStack tank = getFluidInTankInternal(0);
			tankAmt = tank.getAmount();
			if (resource == null || resource.isEmpty() || (tankAmt != 0 && !resource.isFluidEqual(tank)) || !filter.test(resource))
				return 0;
		}

		final int accept = Math.min(resource.getAmount(), capacity.getAsInt() - tankAmt);
		if (accept > 0 && action.execute()) {

			final FluidStack tank = this.tank;
			if (tank.isEmpty())
				this.tank = new FluidStack(resource.getRawFluid(), accept, resource.getTag());
			else
				tank.grow(accept);
		}
		return accept;
	}

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {

		if (resource == null || resource.isEmpty() || !resource.isFluidEqual(tank))
			return FluidStack.EMPTY;

		return drain(resource.getAmount(), action);
	}

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {

		final int tankAmt;
		{
			final FluidStack tank = getFluidInTankInternal(0);
			tankAmt = tank.getAmount();
			if (tankAmt <= 0)
				return FluidStack.EMPTY;
		}

		final int accept = Math.min(maxDrain, tankAmt);
		if (accept > 0 && action.execute())
			tank.shrink(accept);

		return new FluidStack(tank.getRawFluid(), accept, tank.getTag());
	}
}
