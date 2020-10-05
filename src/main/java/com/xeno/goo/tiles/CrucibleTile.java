package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.CrucibleRecipe;
import com.xeno.goo.library.CrucibleRecipes;
import com.xeno.goo.library.MixerRecipes;
import com.xeno.goo.library.WeakConsumerWrapper;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
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
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;

public class CrucibleTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{
    private final CrucibleFluidHandler fluidHandler = createHandler();
    private final LazyOptional<CrucibleFluidHandler> lazyHandler = LazyOptional.of(() -> fluidHandler);

    public CrucibleTile()
    {
        super(Registry.CRUCIBLE_TILE.get());
        goo.addAll(Collections.singletonList(FluidStack.EMPTY));
    }

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();
        lazyHandler.invalidate();
    }

    public FluidStack onlyGoo() {
        if (goo().size() == 0) {
            goo.addAll(Collections.singletonList(FluidStack.EMPTY));
        }

        return goo.get(0);
    }

    public boolean hasFluid(Fluid fluid)
    {
        return onlyGoo() != FluidStack.EMPTY && onlyGoo().getFluid().equals(fluid);
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

        if (getBlockState().get(BlockStateProperties.POWERED)) {
            return;
        }

        tryPushingRecipeResult();
    }

    private CrucibleRecipe getRecipeFromInputs()
    {
        return CrucibleRecipes.getRecipe(onlyGoo());
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

        LazyOptional<IFluidHandler> cap = fluidHandlerInDirection(Direction.DOWN);
        cap.ifPresent((c) -> pushRecipeResult(recipe, c));
    }

    private void pushRecipeResult(CrucibleRecipe recipe, IFluidHandler cap) {
        int sentResult = cap.fill(recipe.output(), IFluidHandler.FluidAction.SIMULATE);
        if (sentResult == 0 || sentResult < recipe.output().getAmount()) {
            return;
        }

        deductInputQuantity(recipe.input());

        cap.fill(recipe.output(), IFluidHandler.FluidAction.EXECUTE);

        if (cap instanceof BulbFluidHandler) {
            float fillVisual = Math.max(0.3f, recipe.output().getAmount() / (float)GooMod.config.gooTransferRate());
            ((BulbFluidHandler) cap).sendVerticalFillSignalForVisuals(recipe.output().getFluid(), fillVisual);
        }
    }

    private void deductInputQuantity(FluidStack input)
    {
        fluidHandler.drain(input, IFluidHandler.FluidAction.EXECUTE);
    }

    private boolean isRecipeSatisfied(CrucibleRecipe recipe)
    {
        return recipe.input().isFluidEqual(onlyGoo()) && recipe.input().getAmount() <= onlyGoo().getAmount();
    }

    public void setGoo(FluidStack fluidStack)
    {
        if (goo.size() == 0) {
            goo.add(fluidStack);
        } else {
            goo.set(0, fluidStack);
        }
    }

    public void onContentsChanged() {
        if (world == null) {
            return;
        }
        if (!world.isRemote) {
            if (world.getServer() == null) {
                return;
            }
            Networking.sendToClientsAround(new FluidUpdatePacket(world.getDimensionKey(), pos, goo), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.getDimensionKey())), pos);
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
        if (!onlyGoo().isEmpty() && !onlyGoo().getFluid().equals(stack.getFluid())) {
            return 0;
        }

        // one last check; we don't allow "inert" fluids or inherently invalid fluids.
        if (!shouldAllowFluid(stack)) {
            return 0;
        }
        return fluidHandler.getTankCapacity(0) - onlyGoo().getAmount();
    }

    private boolean shouldAllowFluid(FluidStack stack)
    {

        // if we already contain this fluid we've passed this test already.
        if (onlyGoo().isFluidEqual(stack)) {
            return true;
        }

        if (!onlyGoo().isEmpty()) {
            return false;
        }

        return CrucibleRecipes.isAnyRecipe(stack);
    }

    @Override
    public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVector, Direction face, RayTraceTargetSource targetSource)
    {
        return onlyGoo();
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource)
    {
        return fluidHandler;
    }
}
