package com.xeno.goo.util;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.function.IntSupplier;

public class MultiGooTank extends IGooTankMulti {

	protected int tankCount = 0;

	public MultiGooTank(IntSupplier capacity) {

		super(capacity);
		tanks = new FluidStack[4];
	}

	@Override
	protected int getTankCount() {

		return tankCount;
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
	public void readFromPacket(PacketBuffer buf) {

		final int e = buf.readVarInt();

		FluidStack[] tanks = new FluidStack[e];
		HashMap<Fluid, FluidStack> contents = new HashMap<>();

		int count = 0, amt = 0;

		for (int i = 0; i < e; ++i) {
			FluidStack tank = readFluidStack(buf);
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
	public int getTotalCapacity() {

		return capacity.getAsInt();
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
			onChange();
		}
		return accept;
	}
}
