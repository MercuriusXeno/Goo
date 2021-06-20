package com.xeno.goo.util;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.function.IntSupplier;

public class GooMultiTank extends IGooTankMulti implements ISidedFluidHandler {

	protected final int tankCount;

	public GooMultiTank(IntSupplier capacity, int tankCount) {

		super(capacity, false);
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
		int j = setTanks(tankList.size(), tankCount, tankCount, i -> FluidStack.loadFluidStackFromNBT(tankList.getCompound(i)));
		for (; j < tankCount; ++j)
			this.tanks[j] = FluidStack.EMPTY;
	}

	@Override
	public void readFromPacket(PacketBuffer buf) {

		final int tankCount = this.tankCount;
		int j = setTanks(buf.readVarInt(), tankCount, tankCount, i -> readFluidStack(buf));
		for (; j < tankCount; ++j)
			this.tanks[j] = FluidStack.EMPTY;
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

	@Override
	public void empty() {
		tanks = new FluidStack[tankCount];
		Arrays.fill(tanks, FluidStack.EMPTY);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getTankCapacity(int index) {

		return capacity.getAsInt();
	}

	protected FluidStack setTank(int index, FluidStack tank, FluidStack resource, int amount) {

		contents.remove(tank.getRawFluid());

		tank = new FluidStack(resource.getRawFluid(), amount, resource.getTag());

		contents.put(tank.getRawFluid(), tank);
		tanks[index] = tank;
		return tank;
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
		setTank(index, tank, resource, amount);
	}

	/**
	 * Fills fluid into internal tanks, distribution is left entirely to the IFluidHandler.
	 *
	 * @param tank
	 * 		FluidStack representing the internal tank that {@code resource} will be added to
	 * @param resource
	 * 		FluidStack representing the Fluid and maximum amount of fluid to be filled.
	 * @param action
	 * 		If SIMULATE, fill will only be simulated.
	 *
	 * @return Amount of resource that was (or would have been, if simulated) filled.
	 */
	protected int fill(FluidStack tank, FluidStack resource, FluidAction action) {

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

	@Override
	protected void shrinkTank(FluidStack tank, int amount) {

		tank.shrink(amount);
		if (tank.isEmpty()) {
			contents.remove(tank.getRawFluid());
			final FluidStack[] tanks = this.tanks;
			for (int i = 0, e = tanks.length; i < e; ++i) {
				FluidStack o = tanks[i];
				if (o == tank) {
					tanks[i] = FluidStack.EMPTY;
					break;
				}
			}
		}
		onChange();
	}

	/**
	 * <b>This implementation ignores fluid tags.</b>
	 * <p>
	 * {@inheritDoc}
	 */
	public int fill(FluidStack resource, FluidAction action) {

		if (resource == null || resource.isEmpty() || !filterCheck(0, resource))
			return 0;

		return fill(contents.get(resource.getRawFluid()), resource, action);
	}

	/**
	 * <b>This implementation ignores fluid tags.</b>
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public int fill(int tankIn, FluidStack resource, FluidAction action) {

		if (resource == null || resource.isEmpty() || !filterCheck(tankIn, resource))
			return 0;

		FluidStack fluidInTank = this.getFluidInTankInternal(tankIn);
		// tank fluid isn't empty and isn't a match
		if (!fluidInTank.isEmpty() && fluidInTank.getRawFluid() != resource.getRawFluid()) {
			return 0;
		}

		// set the tank fluid resource to an initialized stack, otherwise it's the matching type and we just want to grow the stack.
		if (fluidInTank.isEmpty()) {
			fluidInTank = setTank(tankIn, fluidInTank, resource, 0);
		}

		return fill(fluidInTank, resource, action);
	}
}
