package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.DegraderRecipe;
import com.xeno.goo.library.DegraderRecipes;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.util.GooTank;
import com.xeno.goo.util.IGooTank;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import javax.annotation.Nonnull;

public class DegraderTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver {

	public DegraderTile() {

		super(Registry.DEGRADER_TILE.get());
	}

	@Override
	public void updateFluidsTo(PacketBuffer fluids) {

		this.goo.readFromPacket(fluids);
	}

	@Override
	public void tick() {

		if (world == null || world.isRemote) {
			return;
		}

		if (getBlockState().get(BlockStateProperties.POWERED)) {
			return;
		}

		tryPushingRecipeResult();
	}

	private DegraderRecipe getRecipeFromInputs() {

		return DegraderRecipes.getRecipe(goo.getFluidInTankInternal(0));
	}

	private void tryPushingRecipeResult() {

		DegraderRecipe recipe = getRecipeFromInputs();
		if (recipe == null) {
			return;
		}

		// check to make sure the recipe inputs amounts are satisfied.
		if (!isRecipeSatisfied(recipe)) {
			return;
		}

		LazyOptional<IFluidHandler> cap = fluidHandlerInDirection(Direction.DOWN);
		cap.ifPresent((c) -> pushRecipeResult(recipe, c));
	}

	private void pushRecipeResult(DegraderRecipe recipe, IFluidHandler cap) {

		int sentResult = cap.fill(recipe.output(), IFluidHandler.FluidAction.SIMULATE);
		if (sentResult == 0 || sentResult < recipe.output().getAmount()) {
			return;
		}

		deductInputQuantity(recipe.input());

		cap.fill(recipe.output(), IFluidHandler.FluidAction.EXECUTE);
	}

	private void deductInputQuantity(FluidStack input) {

		goo.drain(input, IFluidHandler.FluidAction.EXECUTE);
	}

	private boolean isRecipeSatisfied(DegraderRecipe recipe) {

		return goo.getFluidInTankInternal(0).containsFluid(recipe.input());
	}

	public void setGoo(FluidStack fluidStack) {

		if (!goo.isEmpty()) {
			goo.drain(goo.getTotalContents(), FluidAction.EXECUTE);
		}
		goo.fill(fluidStack, FluidAction.EXECUTE);
	}

	public void onContentsChanged() {

		if (world == null || world.isRemote) {
			return;
		}
		Networking.sendToClientsAround(new FluidUpdatePacket(world.getDimensionKey(), pos, goo), (ServerWorld) world, pos);
	}

	@Nonnull
	@Override
	public CompoundNBT getUpdateTag() {

		return this.write(new CompoundNBT());
	}

	@Override
	protected IGooTank createGooTank() {

		return new GooTank(this::getStorageCapacity).setUniversalFilter(DegraderRecipes::isAnyRecipe).setChangeCallback(this::onContentsChanged);
	}

	@Override
	public int getBaseCapacity() {

		return GooMod.config.degraderInputCapacity();
	}

	@Override
	public int getStorageMultiplier() {

		return 1;
	}

	@Nonnull
	@Override
	public CompoundNBT write(CompoundNBT tag) {

		return super.write(tag);
	}

	public void read(BlockState state, CompoundNBT tag) {

		super.read(state, tag);
		onContentsChanged();
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {

		if (side == Direction.UP) {
			return LazyOptional.empty();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVector, Direction face, RayTraceTargetSource targetSource) {

		return goo.getFluidInTankInternal(0);
	}

	@Override
	public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource) {

		return goo;
	}

	public int getTotalGoo() {

		return goo.getTotalContents();
	}
}
