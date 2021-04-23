package com.xeno.goo.util;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.function.IntSupplier;

public abstract class IGooTankMulti extends IGooTank {

	protected int amount = 0;

	protected FluidStack[] tanks;
	protected HashMap<Fluid, FluidStack> contents = new HashMap<>();

	protected IGooTankMulti(@Nonnull IntSupplier capacity) {

		super(capacity);
	}

	protected abstract int getTankCount();

	@Override
	protected void writeToNBTInternal(CompoundNBT nbt) {

		FluidStack[] tanks = this.tanks;

		ListNBT out = new ListNBT();
		for (int i = 0, tankCount = getTankCount(); i < tankCount; ++i) {
			out.add(tanks[i].writeToNBT(new CompoundNBT()));
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
			tank.shrink(accept);
			onChange();
		}

		return new FluidStack(tank.getRawFluid(), accept, tank.getTag());
	}
}
