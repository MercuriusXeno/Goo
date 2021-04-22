package com.xeno.goo.util;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.function.IntSupplier;

public class GooTanks extends IGooTank {

	protected int amount = 0;

	protected FluidStack[] tanks;
	protected HashMap<Fluid, FluidStack> contents = new HashMap<>();
	protected int tankCount = 0;

	public GooTanks(IntSupplier capacity) {

		super(capacity);
		tanks = new FluidStack[4];
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

		tankCount = count;
		amount = amt;
		this.tanks = tanks;
		this.contents = contents;
	}

	@Override
	protected void writeToNBTInternal(CompoundNBT nbt) {

		FluidStack[] tanks = this.tanks;

		ListNBT out = new ListNBT();
		for (int i = 0, e = tankCount; i < e; ++i) {
			out.add(tanks[i].writeToNBT(new CompoundNBT()));
		}
		nbt.put("Tanks", out);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTanks() {

		return tankCount < Integer.MAX_VALUE ? tankCount + 1 : tankCount;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEmpty() {

		return amount <= 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Nonnull
	public FluidStack getFluidInTankInternal(int index) {

		if (index < 0)
			throw new IndexOutOfBoundsException("Index (" + index + ") is negative");
		if (index >= tankCount)
			return FluidStack.EMPTY;
		final FluidStack stack = tanks[index];
		return stack == null ? FluidStack.EMPTY : stack;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTankCapacity(int index) {

		return (capacity.getAsInt() - amount) + getFluidInTankInternal(index).getAmount();
	}

	/**
	 * Adds a new internal tank based on the resource passed in
	 *
	 * @param resource FluidStack representing the Fluid and fluid to be filled.
	 * @param amount int representing the amount of fluid the tank should have when returned.
	 */
	protected void addTank(@Nonnull FluidStack resource, int amount) {

		final int tankCount = this.tankCount;
		if (tankCount == Integer.MAX_VALUE)
			throw new IllegalStateException("Exceeded integer limits for tank count.");

		FluidStack tank = new FluidStack(resource.getRawFluid(), amount, resource.getTag());

		contents.put(tank.getRawFluid(), tank);

		FluidStack[] tanks = this.tanks;
		if (tanks.length == tankCount) {
			this.tanks = new FluidStack[tankCount + 4 < 0 ? Integer.MAX_VALUE : tankCount + 4];
			System.arraycopy(tanks, 0, this.tanks, 0, this.tankCount++);
			tanks = this.tanks;
		}

		tanks[tankCount] = tank;
	}

	/**
	 * <b>This implementation ignores fluid tags.</b>
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public int fill(FluidStack resource, FluidAction action) {

		if (resource == null || resource.isEmpty() || !filter.test(resource))
			return 0;

		final int accept = Math.min(resource.getAmount(), capacity.getAsInt() - amount);
		if (accept > 0 && action.execute()) {
			amount += accept;

			FluidStack tank = contents.get(resource.getRawFluid());
			if (tank == null)
				addTank(resource, accept);
			else
				tank.grow(accept);
		}
		return accept;
	}

	/**
	 * <b>This implementation ignores fluid tags.</b>
	 * <p>
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {

		if (resource == null || resource.isEmpty() || tankCount == 0)
			return FluidStack.EMPTY;

		final FluidStack tank = contents.get(resource.getRawFluid());
		if (tank == null || tank.getAmount() <= 0)
			return FluidStack.EMPTY;

		final int accept = Math.min(resource.getAmount(), tank.getAmount());
		if (accept > 0 && action.execute())
			tank.shrink(accept);

		return new FluidStack(tank.getRawFluid(), accept, tank.getTag());
	}

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {

		int tankAmt;
		FluidStack tank;
		loop: {
			for (int i = 0, e = tankCount; i < e; ++i) {
				tank = getFluidInTankInternal(i);
				tankAmt = tank.getAmount();
				if (tankAmt > 0)
					break loop;
			}
			return FluidStack.EMPTY;
		}

		final int accept = Math.min(maxDrain, tankAmt);
		if (accept > 0 && action.execute())
			tank.shrink(accept);

		return new FluidStack(tank.getRawFluid(), accept, tank.getTag());
	}
}
