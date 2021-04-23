package com.xeno.goo.util;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.IntSupplier;

public class GooMultiTank extends IGooTankMulti {

	protected final int tankCount;

	public GooMultiTank(IntSupplier capacity, int tankCount) {

		super(capacity);
		tanks = new FluidStack[tankCount];
		Arrays.fill(tanks, FluidStack.EMPTY);
		this.tankCount = tankCount;
	}

	@Override
	protected int getTankCount() {

		return tankCount;
	}

	@Override
	protected void readFromNBTInternal(CompoundNBT nbt) {

		final ListNBT tankList = nbt.getList("Tanks", NBT.TAG_COMPOUND);

		final int tankCount = this.tankCount;
		FluidStack[] tanks = new FluidStack[tankCount];
		Arrays.fill(tanks, FluidStack.EMPTY);
		HashMap<Fluid, FluidStack> contents = new HashMap<>();

		int count = 0, amt = 0;

		for (int i = 0, e = tankList.size(); i < e && count < tankCount; ++i) {
			FluidStack tank = FluidStack.loadFluidStackFromNBT(tankList.getCompound(i));
			if (!contents.containsKey(tank.getRawFluid()) && filter.test(tank)) {
				contents.put(tank.getRawFluid(), tanks[count++] = tank);
				amt += tank.getAmount();
			}
		}

		amount = amt;
		this.tanks = tanks;
		this.contents = contents;
	}

	@Override
	public void readFromPacket(PacketBuffer buf) {

		final int tankCount = this.tankCount;
		FluidStack[] tanks = new FluidStack[tankCount];
		Arrays.fill(tanks, FluidStack.EMPTY);
		HashMap<Fluid, FluidStack> contents = new HashMap<>();

		int count = 0, amt = 0;

		for (int i = 0, e = buf.readVarInt(); i < e && count < tankCount; ++i) {
			FluidStack tank = readFluidStack(buf);
			if (!contents.containsKey(tank.getRawFluid()) && filter.test(tank)) {
				contents.put(tank.getRawFluid(), tanks[count++] = tank);
				amt += tank.getAmount();
			}
		}

		amount = amt;
		this.tanks = tanks;
		this.contents = contents;
	}

	@Override
	public int getTotalCapacity() {

		return capacity.getAsInt() * tankCount;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTanks() {

		return tankCount;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Nonnull
	public FluidStack getFluidInTankInternal(int index) {

		if (index < 0 || index >= tankCount)
			throw new IndexOutOfBoundsException("Index (" + index + ") is out of bounds (" + tankCount + ")");
		final FluidStack stack = tanks[index];
		return stack == null ? FluidStack.EMPTY : stack;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTankCapacity(int index) {

		return capacity.getAsInt();
	}

	/**
	 * Adds a new internal tank based on the resource passed in
	 *
	 * @param resource
	 * 		FluidStack representing the Fluid and fluid to be filled.
	 * @param amount
	 * 		int representing the amount of fluid the tank should have when returned.
	 */
	protected void addTank(@Nonnull FluidStack resource, int amount) {

		FluidStack[] tanks = this.tanks;

		FluidStack tank;
		int index = 0;
		loop:
		{
			for (int tankCount = this.tankCount; index < tankCount; ++index) {
				tank = tanks[index];
				if (tank.isEmpty())
					break loop;
			}
			return;
		}
		contents.remove(tank.getRawFluid());

		tank = new FluidStack(resource.getRawFluid(), amount, resource.getTag());

		contents.put(tank.getRawFluid(), tank);
		tanks[index] = tank;
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

		FluidStack tank = contents.get(resource.getRawFluid());

		final int accept = Math.min(resource.getAmount(), capacity.getAsInt() - (tank == null ? 0 : tank.getAmount()));
		if (accept > 0 && action.execute()) {
			if (tank == null)
				addTank(resource, accept);
			else
				tank.grow(accept);

			amount += accept;
			onChange();
		} else if (tank == null && accept > 0) {
			for (int i = 0, tankCount = this.tankCount; i < tankCount; ++i) {
				tank = tanks[i];
				if (tank.isEmpty())
					return accept;
			}
			return 0;
		}
		return accept;
	}
}
