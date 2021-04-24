package com.xeno.goo.tiles;

import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.util.IGooTank;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public abstract class GooContainerAbstraction extends FluidHandlerInteractionAbstraction {

	private int enchantContainment = 0;

	protected final IGooTank goo = createGooTank();
	private final LazyOptional<IFluidHandler> lazyHandler = LazyOptional.of(() -> goo);

	public GooContainerAbstraction(TileEntityType<?> tileEntityTypeIn) {

		super(tileEntityTypeIn);
	}

	protected abstract IGooTank createGooTank();

	public void setContainmentLevel(int containment) {

		this.enchantContainment = containment;
	}

	public int getContainmentLevel() {

		return this.enchantContainment;
	}

	public final int getStorageCapacity() {

		return (int) MathHelper.clamp(getBaseCapacity() * (long) getStorageMultiplier(), 0, Integer.MAX_VALUE);
	}

	public abstract int getBaseCapacity();

	public abstract int getStorageMultiplier();

	public static List<FluidStack> deserializeGooForDisplay(CompoundNBT tag) {

		List<FluidStack> tagGooList = new ArrayList<>();
		if (tag.contains("count", NBT.TAG_ANY_NUMERIC)) {
			int size = tag.getInt("count");
			for (int i = 0; i < size; i++) {
				CompoundNBT gooTag = tag.getCompound("goo" + i);
				FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
				tagGooList.add(stack);
			}
		} else {
			for (INBT data : tag.getList("Tanks", NBT.TAG_COMPOUND))
				tagGooList.add(FluidStack.loadFluidStackFromNBT((CompoundNBT) data));
		}

		return tagGooList;
	}

	@Nonnull
	@Override
	public CompoundNBT write(CompoundNBT tag) {

		tag.put("goo", goo.writeToNBT(new CompoundNBT()));
		if (enchantContainment > 0)
			tag.putInt(Containment.id(), enchantContainment);
		return super.write(tag);
	}

	private static CompoundNBT convertOldData(CompoundNBT nbt) {

		if (nbt.contains("count", NBT.TAG_ANY_NUMERIC)) {
			ListNBT data = new ListNBT();
			for (int i = 0, e = nbt.getInt("count"); i < e; ++i)
				data.add(nbt.getCompound("goo" + i));
			nbt.put("Tanks", data);
		}
		return nbt;
	}

	public void read(BlockState state, CompoundNBT tag) {

		goo.readFromNBT(convertOldData(tag.getCompound("goo")));
		if (tag.contains(Containment.id(), NBT.TAG_ANY_NUMERIC)) {
			setContainmentLevel(tag.getInt(Containment.id()));
		}
		super.read(state, tag);
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {

		if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
			return lazyHandler.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	protected void invalidateCaps() {

		super.invalidateCaps();
		lazyHandler.invalidate();
	}

	public FluidStack getGooFromTargetRayTraceResult(BlockRayTraceResult target, RayTraceTargetSource targetSource) {

		return getGooFromTargetRayTraceResult(target.getHitVec(), target.getFace(), targetSource);
	}

	public abstract FluidStack getGooFromTargetRayTraceResult(Vector3d hitVector, Direction face, RayTraceTargetSource targetSource);

	public abstract IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource);
}
