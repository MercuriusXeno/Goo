package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.MixerRecipe;
import com.xeno.goo.library.MixerRecipes;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MixerTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{
    private MixerFluidHandler leftHandler = createHandler(1);
    private MixerFluidHandler rightHandler = createHandler(0);
    private LazyOptional<MixerFluidHandler> leftLazy = LazyOptional.of(() -> leftHandler);
    private LazyOptional<MixerFluidHandler> rightLazy = LazyOptional.of(() -> rightHandler);

    public MixerTile()
    {
        super(Registry.MIXER_TILE.get());
        while(goo.size() < 2) {
            goo.add(FluidStack.EMPTY);
        }
    }

    public FluidStack goo(int side)
    {
        while(goo.size() < 2) {
            goo.add(FluidStack.EMPTY);
        }
        return goo.get(side);
    }

    public FluidStack goo(int side, Fluid fluid)
    {
        FluidStack maybeGoo = goo(side);
        if (maybeGoo.getFluid().equals(fluid)) {
            return maybeGoo;
        }

        return FluidStack.EMPTY;
    }

    public boolean hasFluid(int sideTank, Fluid fluid)
    {
        return goo.get(sideTank).getFluid().isEquivalentTo(fluid);
    }

    private int sideTank(Direction side)
    {
        // get rotated side and then determine east/west (or upward, which doesn't change) orientation.
        if (side == orientedRight()) {
            return 0;
        } else if (side == orientedLeft()) {
            return 1;
        }

        return -1;
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
        return this.getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids)
    {
        this.goo = fluids;
    }

    @Override
    public void tick()
    {
        if (world == null || world.isRemote) {
            return;
        }
        while(goo.size() < 2) {
            goo.add(FluidStack.EMPTY);
        }

        tryPushingRecipeResult();
    }

    private MixerRecipe getRecipeFromInputs()
    {
        return MixerRecipes.getRecipe(goo.get(0), goo.get(1));
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

        IFluidHandler cap = tryGettingFluidCapabilityFromTileBelow();
        if (cap == null) {
            return;
        }

        int sentResult = cap.fill(recipe.output(), IFluidHandler.FluidAction.SIMULATE);
        if (sentResult == 0 || sentResult < recipe.output().getAmount()) {
            return;
        }

        deductInputQuantities(recipe.inputs());

        cap.fill(recipe.output(), IFluidHandler.FluidAction.EXECUTE);

        if (cap instanceof BulbFluidHandler) {
            float fillVisual =  Math.max(0.3f, recipe.output().getAmount() / (float)GooMod.config.gooTransferRate());
            ((BulbFluidHandler) cap).sendVerticalFillSignalForVisuals(recipe.output().getFluid(), fillVisual);
        }
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
            if (goo.stream().noneMatch(g -> g.isFluidEqual(e) && g.getAmount() >= e.getAmount())) {
                return false;
            }
        }
        return true;
    }

    private IFluidHandler tryGettingFluidCapabilityFromTileBelow()
    {
        TileEntity tile = FluidHandlerHelper.tileAtDirection(this, Direction.DOWN);
        if (tile == null) {
            return null;
        }
        return FluidHandlerHelper.capability(tile, Direction.UP);
    }

    public void addGoo(int side, FluidStack fluidStack)
    {
        goo.set(side, fluidStack);
    }

    public void onContentsChanged() {
        if (world == null) {
            return;
        }
        if (!world.isRemote) {
            if (world.getServer() == null) {
                return;
            }
            Networking.sendToClientsAround(new FluidUpdatePacket(world.func_234923_W_(), pos, goo), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.func_234923_W_())), pos);
        }
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        tag.put("goo", serializeGoo());
        return super.write(tag);
    }

    public void read(BlockState state, CompoundNBT tag)
    {
        CompoundNBT gooTag = tag.getCompound("goo");
        deserializeGoo(gooTag);
        super.read(state, tag);
        onContentsChanged();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
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

    private MixerFluidHandler createHandler(int side) {
        return new MixerFluidHandler(this, side);
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

    public int getSpaceRemaining(int sideTank, FluidStack stack)
    {
        // there may be space but this is the wrong kind of goo and you can't mix inside input tanks.
        if (!goo(sideTank).isEmpty() && !goo(sideTank).getFluid().equals(stack.getFluid())) {
            return 0;
        }

        // one last check; we don't allow "inert" fluid combinations or inherently invalid fluids.
        if (!shouldAllowFluid(stack, sideTank)) {
            return 0;
        }

        if (sideTank == 0) {
            return rightHandler.getTankCapacity(0) - goo(sideTank).getAmount();
        } else {
            return leftHandler.getTankCapacity(0) - goo(sideTank).getAmount();
        }
    }

    private boolean shouldAllowFluid(FluidStack stack, int sideTank)
    {
        // if we already contain this fluid we've passed this test already.
        if (goo.get(sideTank).isFluidEqual(stack)) {
            return true;
        }

        if (goo.get(0).isEmpty() && goo.get(1).isEmpty()) {
            return MixerRecipes.isAnyRecipe(stack);
        } else {
            if (goo.get(sideTank).isEmpty()) {
                int otherTank = sideTank == 0 ? 1 : 0;
                return MixerRecipes.getRecipe(goo.get(otherTank), stack) != null;
            }
        }

        return MixerRecipes.getRecipe(goo.get(0), goo.get(1)) != null;
    }

    @Override
    public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVector, Direction face)
    {
        if (isLeftSideMostly(hitVector, face)) {
            return goo.get(1);
        }

        return goo.get(0);
    }

    private boolean isLeftSideMostly(Vector3d hitVec, Direction face)
    {
        boolean isLeft;
        if (face == orientedLeft()) {
            isLeft = true;
        } else {
            if (this.facing().getAxis() == Direction.Axis.Z) {
                isLeft = (facing() == Direction.SOUTH) == (hitVec.getX() % 1 <= 0.5f);
            } else {
                isLeft = (facing() == Direction.WEST) == (hitVec.getZ() % 1 <= 0.5f);
            }
        }
        return isLeft;
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face)
    {
        return isLeftSideMostly(hitVec, face) ? leftHandler : rightHandler;
    }
}
