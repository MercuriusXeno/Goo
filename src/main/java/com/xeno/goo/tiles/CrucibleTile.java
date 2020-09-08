package com.xeno.goo.tiles;

import com.xeno.goo.library.CrucibleRecipe;
import com.xeno.goo.library.CrucibleRecipes;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;

public class CrucibleTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{
    private CrucibleFluidHandler fluidHandler = createHandler();
    private LazyOptional<CrucibleFluidHandler> lazyHandler = LazyOptional.of(() -> fluidHandler);

    public CrucibleTile()
    {
        super(Registry.CRUCIBLE_TILE.get());
        goo = Collections.singletonList(FluidStack.EMPTY);
    }

    public FluidStack goo()
    {
        return goo.get(0);
    }

    public boolean hasFluid(Fluid fluid)
    {
        return goo() != FluidStack.EMPTY && goo().getFluid().equals(fluid);
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids)
    {
        this.setGoo(fluids.get(0));
    }

    @Override
    public void tick()
    {
        if (world == null || world.isRemote) {
            return;
        }

        tryPushingRecipeResult();
    }

    private CrucibleRecipe getRecipeFromInputs()
    {
        return CrucibleRecipes.getRecipe(goo());
    }

    private void tryPushingRecipeResult() {
        CrucibleRecipe recipe = getRecipeFromInputs();
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

        deductInputQuantity(recipe.input());

        cap.fill(recipe.output(), IFluidHandler.FluidAction.EXECUTE);
    }

    private void deductInputQuantity(FluidStack input)
    {
        fluidHandler.drain(input, IFluidHandler.FluidAction.EXECUTE);
    }

    private boolean isRecipeSatisfied(CrucibleRecipe recipe)
    {
        return recipe.input().isFluidEqual(goo()) && recipe.input().getAmount() <= goo().getAmount();
    }

    private IFluidHandler tryGettingFluidCapabilityFromTileBelow()
    {
        TileEntity tile = FluidHandlerHelper.tileAtDirection(this, Direction.DOWN);
        return FluidHandlerHelper.capability(tile, Direction.UP);
    }

    public void setGoo(FluidStack fluidStack)
    {
        goo.clear();
        goo.add(0, fluidStack);
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

    private List<FluidStack> asList(FluidStack goo)
    {
        return Collections.singletonList(goo);
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
            return lazyHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    private CrucibleFluidHandler createHandler() {
        return new CrucibleFluidHandler(this);
    }

    public ItemStack getCrucibleStack(Block block) {
        ItemStack stack = new ItemStack(block);

        CompoundNBT crucibleTag = new CompoundNBT();
        write(crucibleTag);
        crucibleTag.remove("x");
        crucibleTag.remove("y");
        crucibleTag.remove("z");

        CompoundNBT stackTag = new CompoundNBT();
        stackTag.put("BlockEntityTag", crucibleTag);
        stack.setTag(stackTag);

        return stack;
    }

    public int getSpaceRemaining(FluidStack stack)
    {
        if (!goo().isEmpty() && !goo().getFluid().equals(stack.getFluid())) {
            return 0;
        }
        return fluidHandler.getTankCapacity(0) - goo().getAmount();
    }
}
