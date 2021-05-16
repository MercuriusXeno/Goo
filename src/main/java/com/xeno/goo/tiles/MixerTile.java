package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.MixerRecipe;
import com.xeno.goo.library.MixerRecipes;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.util.FluidHandlerTankWrapper;
import com.xeno.goo.util.GooMultiTank;
import com.xeno.goo.util.IGooTank;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
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
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.List;

public class MixerTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{
    private final FluidHandlerTankWrapper rightHandler = createHandler(0);
    private final LazyOptional<FluidHandlerTankWrapper> rightLazy = LazyOptional.of(() -> rightHandler);

    private final FluidHandlerTankWrapper leftHandler = createHandler(1);
    private final LazyOptional<FluidHandlerTankWrapper> leftLazy = LazyOptional.of(() -> leftHandler);

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();
        leftLazy.invalidate();
        rightLazy.invalidate();
    }

    public MixerTile()
    {
        super(Registry.MIXER_TILE.get());
    }

    public Direction orientedRight() {
        switch(this.facing()) {
            case NORTH:
                return Direction.EAST;
            case SOUTH:
                return Direction.WEST;
            case EAST:
                return Direction.SOUTH;
            case WEST:
                return Direction.NORTH;
        }
        return Direction.EAST;
    }

    public Direction orientedLeft() {
        switch(this.facing()) {
            case NORTH:
                return Direction.WEST;
            case SOUTH:
                return Direction.EAST;
            case EAST:
                return Direction.NORTH;
            case WEST:
                return Direction.SOUTH;
        }
        return Direction.WEST;
    }

    public Direction facing()
    {
        if (this.world == null) {
            return Direction.NORTH;
        }
        return this.getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public void updateFluidsTo(PacketBuffer fluids)
    {
        this.goo.readFromPacket(fluids);
    }

    @Override
    public void tick()
    {
        if (world == null || world.isRemote) {
            return;
        }

        tryPushingRecipeResult();
    }

    private MixerRecipe getRecipeFromInputs()
    {
        return MixerRecipes.getRecipe(goo.getFluidInTankInternal(0), goo.getFluidInTankInternal(1));
    }

    private void tryPushingRecipeResult() {
        MixerRecipe recipe = getRecipeFromInputs();
        if (recipe == null) {
            return;
        }

        // check to make sure the recipe inputs amounts are satisfied.
        if (!isRecipeSatisfied(recipe)) {
            return;
        }

        LazyOptional<IFluidHandler> cap = fluidHandlerInDirection(Direction.DOWN);
        cap.ifPresent((c) -> pushRecipeResult(c, recipe));
    }

    private void pushRecipeResult(IFluidHandler c, MixerRecipe recipe)
    {
        int sentResult = c.fill(recipe.output(), IFluidHandler.FluidAction.SIMULATE);
        if (sentResult == 0 || sentResult < recipe.output().getAmount()) {
            return;
        }

        deductInputQuantities(recipe.inputs());

        c.fill(recipe.output(), IFluidHandler.FluidAction.EXECUTE);
    }

    private boolean deductInputQuantities(List<FluidStack> inputs)
    {
        for(FluidStack input : inputs) {
            // try deducting from either tank. it doesn't really matter which we check first
            // simulate will tell us that it contains it or doesn't.
            if (leftHandler.drain(input, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
                // east handler doesn't have it.
                rightHandler.drain(input, IFluidHandler.FluidAction.EXECUTE);
            } else {
                leftHandler.drain(input, IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return true;
    }

    private boolean isRecipeSatisfied(MixerRecipe recipe)
    {
        for(FluidStack e : recipe.inputs()) {
            if (!goo.getFluidInTankInternal(0).containsFluid(e) && !goo.getFluidInTankInternal(1).containsFluid(e)) {
                return false;
            }
        }
        return true;
    }

    public void onContentsChanged() {
        if (world == null || world.isRemote) {
            return;
        }
        Networking.sendToClientsAround(new FluidUpdatePacket(world.getDimensionKey(), pos, goo), (ServerWorld) world, pos);
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    @Override
    protected IGooTank createGooTank() {

        return new GooMultiTank(this::getStorageCapacity, 2).setFilter(MixerRecipes::isAnyRecipe).setChangeCallback(this::onContentsChanged);
    }

    @Override
    public int getBaseCapacity() {

        return GooMod.config.mixerInputCapacity();
    }

    @Override
    public int getStorageMultiplier() {

        return 1;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (side == orientedLeft()) {
                return leftLazy.cast();
            }
            if (side == orientedRight()) {
                return rightLazy.cast();
            }
        }
        return super.getCapability(cap, side);
    }

    private FluidHandlerTankWrapper createHandler(int side) {
        return new FluidHandlerTankWrapper(goo, side) {

            @Override
            public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {

                if (super.isFluidValid(this.tank, stack)) {

                    int otherTank = this.tank == 0 ? 1 : 0;
                    if (handler.getFluidInTank(this.tank).isEmpty() && !handler.getFluidInTank(otherTank).isEmpty())
                        return MixerRecipes.getRecipe(handler.getFluidInTank(otherTank), stack) != null;

                    return true;
                }
                return  false;
            }
        };
    }

    public ItemStack mixerStack(Block block) {
        ItemStack stack = new ItemStack(block);

        CompoundNBT bulbTag = new CompoundNBT();
        write(bulbTag);
        bulbTag.remove("x");
        bulbTag.remove("y");
        bulbTag.remove("z");

        CompoundNBT stackTag = new CompoundNBT();
        stackTag.put("BlockEntityTag", bulbTag);
        stack.setTag(stackTag);

        return stack;
    }

    @Override
    public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVector, Direction face, RayTraceTargetSource targetSource)
    {
        if (goo.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return goo.getFluidInTankInternal(isRightSideMostly(hitVector, face) ? 0 : 1);
    }

    private boolean isRightSideMostly(Vector3d hitVec, Direction face)
    {
        boolean isRight;
        if (face == orientedRight()) {
            isRight = true;
        } else {
            if (this.facing().getAxis() == Direction.Axis.Z) {
                isRight = (facing() == Direction.NORTH) == (hitVec.getX() >= this.getPos().getX() + 0.5f);
            } else {
                isRight = (facing() == Direction.EAST) == (hitVec.getZ() >= this.getPos().getZ() + 0.5f);
            }
        }
        return isRight;
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource)
    {
        return isRightSideMostly(hitVec, face) ? rightHandler : leftHandler;
    }
}
