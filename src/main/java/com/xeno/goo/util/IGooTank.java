package com.xeno.goo.util;

import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * This class abuses naming standards. Move along.
 */
public abstract class IGooTank implements IFluidHandler {

	private final Predicate<FluidStack> ALWAYS = p -> true;

	@Nonnull
	protected final IntSupplier capacity;
	@Nonnull
	protected Predicate<FluidStack> filter = ALWAYS;
	@Nullable
	private Runnable changeCallback;

	protected IGooTank(@Nonnull IntSupplier capacity) {

		this.capacity = Objects.requireNonNull(capacity);
	}

	@SuppressWarnings({ "unchecked", "UnusedReturnValue" })
	public final <T extends IGooTank> T readFromNBT(CompoundNBT nbt) {

		readFromNBTInternal(nbt);
		onChange();
		return (T) this;
	}

	public final CompoundNBT writeToNBT(CompoundNBT nbt) {

		writeToNBTInternal(nbt);
		return nbt;
	}

	protected abstract void readFromNBTInternal(CompoundNBT nbt);

	protected abstract void writeToNBTInternal(CompoundNBT nbt);

	/**
	 * Sets a filter to limit the tank to specific fluids.
	 *
	 * @param filter Predicate to restrict what fluids this tank may accept.
	 * @param <T>    The implementation of IGooTank.
	 * @return <code>this</code> tank.
	 */
	@Nonnull
	@SuppressWarnings("unchecked")
	public final <T extends IGooTank> T setFilter(@Nonnull Predicate<FluidStack> filter) {

		this.filter = Objects.requireNonNull(filter);
		return (T) this;
	}

	/**
	 * Sets a filter to limit the tank to specific fluids.
	 *
	 * @param changeCallback Runnable to restrict what fluids this tank may accept.
	 * @param <T>    The implementation of IGooTank.
	 * @return <code>this</code> tank.
	 */
	@Nonnull
	@SuppressWarnings("unchecked")
	public final <T extends IGooTank> T setChangeCallback(@Nullable Runnable changeCallback) {

		this.changeCallback = changeCallback;
		return (T) this;
	}

	protected final void onChange() {

		if (changeCallback != null)
			changeCallback.run();
	}

	/**
	 * @return whether or not this tank contains any fluid(s)
	 */
	public abstract boolean isEmpty();

	/**
	 * @return Current amount of fluid in the tank.
	 */
	public abstract int getTotalContents();

	/**
	 * @return Capacity of this fluid tank.
	 */
	public abstract int getTotalCapacity();

	/**
	 * @return Unutilized capacity of this fluid tank.
	 */
	public final int getRemainingCapacity() {

		return getTotalCapacity() - getTotalContents();
	}

	/**
	 * Returns the FluidStack in a given tank.
	 *
	 * @param index int representing the tank to query.
	 * @return FluidStack in a given tank. FluidStack.EMPTY if the tank is empty.
	 * @throws IndexOutOfBoundsException if <code>index</code> is out of bounds.
	 */
	@Nonnull
	public abstract FluidStack getFluidInTankInternal(int index);

	/**
	 * {@inheritDoc}
	 */
	@Nonnull
	@Override
	public final FluidStack getFluidInTank(int index) {

		final FluidStack fluid = getFluidInTankInternal(index);
		return new FluidStack(fluid.getRawFluid(), fluid.getAmount(), fluid.getTag()) {

			@Override
			public void setAmount(int amount) {

				throw new IllegalStateException("Can't modify the status stacks.");
			}
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	final public boolean isFluidValid(int tank, @Nonnull FluidStack stack) { // this method feels useless?

		final FluidStack fluid = getFluidInTankInternal(tank);
		return (fluid == FluidStack.EMPTY && filter.test(stack)) || (fluid.getRawFluid() == stack.getRawFluid() && FluidStack.areFluidStackTagsEqual(fluid, stack));
	}
}
