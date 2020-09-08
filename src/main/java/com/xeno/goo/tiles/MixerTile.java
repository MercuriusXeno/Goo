package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.MixerRecipe;
import com.xeno.goo.library.MixerRecipes;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MixerTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{
    private MixerFluidHandler eastHandler = createHandler(Direction.EAST);
    private MixerFluidHandler westHandler = createHandler(Direction.WEST);
    private LazyOptional<MixerFluidHandler> eastLazy = LazyOptional.of(() -> eastHandler);
    private LazyOptional<MixerFluidHandler> westLazy = LazyOptional.of(() -> westHandler);
    List<FluidStack> goo = new ArrayList<>();

    public MixerTile()
    {
        super(Registry.MIXER_TILE.get());
        while(goo.size() < 2) {
            goo.add(FluidStack.EMPTY);
        }
    }

    public FluidStack goo(Direction side)
    {
        int sideTank = sideTank(side);
        if (sideTank == -1) {
            return FluidStack.EMPTY;
        }
        return goo.get(sideTank);
    }

    public FluidStack goo(Direction side, Fluid fluid)
    {
        FluidStack maybeGoo = goo(side);
        if (maybeGoo.getFluid().equals(fluid)) {
            return maybeGoo;
        }

        return FluidStack.EMPTY;
    }

    public boolean hasFluid(Direction side, Fluid fluid)
    {
        return goo(side, fluid) != FluidStack.EMPTY;
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
        if (goo.get(0) == null) {
            goo.add(0, FluidStack.EMPTY);
        }
        if (goo.get(1) == null) {
            goo.add(1, FluidStack.EMPTY);
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
            if (eastHandler.drain(input, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
                // east handler doesn't have it.
                westHandler.drain(input, IFluidHandler.FluidAction.EXECUTE);
            } else {
                eastHandler.drain(input, IFluidHandler.FluidAction.EXECUTE);
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

    public void addGoo(Direction side, FluidStack fluidStack)
    {
        int sideTank = sideTank(side);
        if (sideTank == -1) {
            return;
        }
        goo.set(sideTank, fluidStack);
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
                return westLazy.cast();
            }
            if (side == orientedRight()) {
                return eastLazy.cast();
            }
        }
        return super.getCapability(cap, side);
    }

    private MixerFluidHandler createHandler(Direction d) {
        return new MixerFluidHandler(this, d);
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

    public static List<FluidStack> deserializeGooForDisplay(CompoundNBT tag) {
        List<FluidStack> tagGooList = new ArrayList<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
            tagGooList.add(stack);
        }

        return tagGooList;
    }

    public int getSpaceRemaining(Direction side, FluidStack stack)
    {
        int sideTank = sideTank(side);
        if (sideTank == -1) {
            return 0;
        }
        IFluidHandler handler = orientedLeft() == side ? westHandler :
                (orientedRight() == side ? eastHandler : null);
        if (handler == null) {
            return 0;
        }
        // there may be space but this is the wrong kind of goo and you can't mix inside input tanks.
        if (!goo.get(sideTank).isEmpty() && !goo.get(sideTank).getFluid().equals(stack.getFluid())) {
            return 0;
        }

        // one last check; we don't allow "inert" fluid combinations or inherently invalid fluids.
        if (!shouldAllowFluid(stack, side)) {
            return 0;
        }
        return handler.getTankCapacity(0) - goo.get(sideTank).getAmount();
    }

    private boolean shouldAllowFluid(FluidStack stack, Direction side)
    {
        // reject the fluid if both tanks are empty and this stack doesn't have a recipe
        int sideTank = sideTank(side);

        if (sideTank == -1) {
            return false;
        }

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
}
