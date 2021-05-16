package com.xeno.goo.util;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public abstract class IGooTankMulti extends IGooTank {

	/**
	 * Helper method to handle saving the true fluid, rather than associating random data with the Empty fluid when amount <= 0
	 */
	protected static CompoundNBT writeTankToNBT(FluidStack tank) {

		// TODO validate logic matches forge in newer versions (last check 1.16.3)
		CompoundNBT nbt = new CompoundNBT();
		//noinspection ConstantConditions
		nbt.putString("FluidName", tank.getRawFluid().getRegistryName().toString());
		nbt.putInt("Amount", tank.getAmount());

		if (tank.hasTag()) {
			nbt.put("Tag", tank.getTag());
		}
		return nbt;
	}

	protected int amount = 0;

	protected FluidStack[] tanks;
	protected IdentityHashMap<Fluid, FluidStack> contents = new IdentityHashMap<>();

	protected IGooTankMulti(@Nonnull IntSupplier capacity, boolean lockedTanks) {

		super(capacity, lockedTanks);
	}

	protected abstract int getTankCount();

	protected int setTanks(int size, IntFunction<FluidStack> tankReader) {

		return setTanks(size, Integer.MAX_VALUE, 0, tankReader);
	}

	protected int setTanks(int size, int maxSize, IntFunction<FluidStack> tankReader) {

		return setTanks(size, maxSize, 0, tankReader);
	}

	protected int setTanks(int inSize, int maxSize, int minSize, IntFunction<FluidStack> tankReader) {

		LinkedList<FluidStack> tanks = new LinkedList<>();
		IdentityHashMap<Fluid, FluidStack> contents = new IdentityHashMap<>();

		int count = 0, amt = 0;

		for (int i = 0; (i < inSize) & (count < maxSize); ++i) {
			FluidStack tank = tankReader.apply(i);
			if (!contents.containsKey(tank.getRawFluid()) && filter.test(tank)) {
				contents.put(tank.getRawFluid(), tank);
				tanks.add(tank);
				amt += tank.getAmount();
				++count;
			}
		}

		amount = amt;
		this.tanks = tanks.toArray(new FluidStack[Math.max(count, minSize)]);
		this.contents = contents;
		return count;
	}

	@Override
	protected void writeToNBTInternal(CompoundNBT nbt) {

		FluidStack[] tanks = this.tanks;

		ListNBT out = new ListNBT();
		for (int i = 0, tankCount = getTankCount(); i < tankCount; ++i) {
			out.add(writeTankToNBT(tanks[i]));
		}
		nbt.put("Tanks", out);
	}

	@Override
	public void writeToPacket(PacketBuffer buf) {

		FluidStack[] tanks = this.tanks;

		buf.writeVarInt(getTankCount());
		for (int i = 0, e = getTankCount(); i < e; ++i)
			writeFluidStack(buf, tanks[i]);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEmpty() {

		return amount <= 0;
	}

	@Override
	public int getTotalContents() {

		return amount;
	}

	/**
	 * <b>This implementation ignores fluid tags.</b>
	 * <p>
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {

		if (resource == null || resource.isEmpty() || getTankCount() == 0)
			return FluidStack.EMPTY;

		final FluidStack tank = contents.get(resource.getRawFluid());
		if (tank == null || tank.getAmount() <= 0)
			return FluidStack.EMPTY;

		final int accept = Math.min(resource.getAmount(), tank.getAmount());
		if (accept > 0 && action.execute()) {
			amount -= accept;
			tank.shrink(accept);
			onChange();
		}

		return new FluidStack(tank.getRawFluid(), accept, tank.getTag());
	}

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {

		int tankAmt = 0;
		FluidStack tank = FluidStack.EMPTY;
		{
			long min = Long.MAX_VALUE;
			FluidStack cur;
			int amt;
			for (int i = 0, tankCount = getTankCount(); i < tankCount; ++i) {
				cur = getFluidInTankInternal(i);
				amt = cur.getAmount();
				if (amt > 0 && amt < min) {
					tank = cur;
					min = tankAmt = amt;
				}
			}
			if (min == Long.MAX_VALUE)
				return FluidStack.EMPTY;
		}

		final int accept = Math.min(maxDrain, tankAmt);
		if (accept > 0 && action.execute()) {
			amount -= accept;
			tank.shrink(accept);
			onChange();
		}

		return new FluidStack(tank.getRawFluid(), accept, tank.getTag());
	}
}
