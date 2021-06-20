package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
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
import java.util.function.Predicate;

import static net.minecraft.util.Direction.*;

public class MixerTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{
    private static Predicate<FluidStack> RECIPE_FILTER = MixerRecipes::isAnyRecipe;

    private final FluidHandlerTankWrapper rightHandler = createHandler(0);
    private final LazyOptional<FluidHandlerTankWrapper> rightLazy = LazyOptional.of(() -> rightHandler);

    private final FluidHandlerTankWrapper leftHandler = createHandler(1);
    private final LazyOptional<FluidHandlerTankWrapper> leftLazy = LazyOptional.of(() -> leftHandler);

    private final FluidHandlerTankWrapper bottomHandler = createHandler(2);
    private final LazyOptional<FluidHandlerTankWrapper> bottomLazy = LazyOptional.of(() -> bottomHandler);

    private GooMultiTank gooMultiTank;

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();
        leftLazy.invalidate();
        rightLazy.invalidate();
        bottomLazy.invalidate();
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
                return SOUTH;
            case WEST:
                return NORTH;
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
                return NORTH;
            case WEST:
                return SOUTH;
        }
        return Direction.WEST;
    }

    public Direction facing()
    {
        if (this.world == null) {
            return NORTH;
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
        tryVerticalDrain();
    }

    // if placed above another bulb, the bulb above will drain everything downward.
    private boolean tryVerticalDrain() {
        if (this.goo.getFluidInTankInternal(2).isEmpty()) {
            return false;
        }

        // try fetching the bulb capabilities (below) and throw an exception if it fails. return if null.
        LazyOptional<IFluidHandler> cap = fluidHandlerInDirection(Direction.DOWN);

        final boolean[] verticalDrained = {false};
        cap.ifPresent((c) -> {
            verticalDrained[0] = doVerticalDrain(c);
        });
        return verticalDrained[0];
    }

    private boolean doVerticalDrain(IFluidHandler c)
    {
        // the maximum amount you can drain in a tick is here.
        int simulatedDrainLeft = transferRate();

        if (simulatedDrainLeft <= 0) {
            return false;
        }

        FluidStack s = goo.getFluidInTankInternal(2);
        if (s.isEmpty()) {
            return false;
        }
        int simulatedDrain = trySendingFluid(simulatedDrainLeft, s, c, true);
        if (simulatedDrain != simulatedDrainLeft) {
            return true;
        }

        return false;
    }

    private int trySendingFluid(int simulatedDrainLeft, FluidStack s, IFluidHandler cap, boolean isVerticalDrain) {
        // simulated drain left represents how much "suction" is left in the interaction
        // s is the maximum amount in the stack. the lesser of these is how much you can drain in one tick.
        int amountLeft = Math.min(simulatedDrainLeft, s.getAmount());

        // do it again, only this time, testing the amount the receptacle can tolerate.
        amountLeft = Math.min(amountLeft, cap.fill(s, IFluidHandler.FluidAction.SIMULATE));

        // now here, the number can be zero. If it is, it means we don't have space left in the receptacle. Break.
        if (amountLeft == 0) {
            return 0;
        }

        // at this point we know we're able to move a nonzero amount of fluid. Prep a new stack
        FluidStack stackBeingSwapped = new FluidStack(s.getFluid(), amountLeft);

        // fill the receptacle.
        cap.fill(stackBeingSwapped, IFluidHandler.FluidAction.EXECUTE);

        // now call our drain, we're the sender.
        goo.drain(stackBeingSwapped, IFluidHandler.FluidAction.EXECUTE);

        // we can only handle so much work in a tick. Decrement the work limit. If it's zero, this loop breaks.
        // but if it was less than we're allowed to send, we can do more work in this tick, so it will continue.
        simulatedDrainLeft -= amountLeft;

        return simulatedDrainLeft;
    }

    private int transferRate()
    {
        return GooMod.config.gooTransferRate() * getStorageMultiplier();
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

        pushRecipeResult(recipe);
    }

    private void pushRecipeResult(MixerRecipe recipe)
    {
        int sentResult = gooMultiTank.fill(2, recipe.output(), IFluidHandler.FluidAction.SIMULATE);
        if (sentResult == 0 || sentResult < recipe.output().getAmount()) {
            return;
        }

        deductInputQuantities(recipe.inputs());

        gooMultiTank.fill(2, recipe.output(), IFluidHandler.FluidAction.EXECUTE);
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
        return gooMultiTank = new GooMultiTank(this::getStorageCapacity, 3)
                .setFilter(GooFluid.IS_GOO_FLUID)
                .setChangeCallback(this::onContentsChanged);
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
            if (side == DOWN) {
                return bottomLazy.cast();
            }
        }
        return super.getCapability(cap, side);
    }

    private FluidHandlerTankWrapper createHandler(int tankIn) {
        // bottom tank
        if (tankIn == 2)
            return new FluidHandlerTankWrapper(goo, tankIn) {

                @Override // block fills to the bottom tank
                public int fill(FluidStack resource, FluidAction action) {

                    return 0;
                }
            };
        // left and right sides
        return new FluidHandlerTankWrapper(goo, tankIn) {

            @Override
            public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {

                if (tank == 0 && RECIPE_FILTER.test(stack)) {
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
        // try to handle the bottom tank
        if (hitVector.y <= this.getPos().getY() + 0.375f) {
            if (face == facing() || isOverMechanism(hitVector)) {
                return FluidStack.EMPTY;
            }
            return goo.getFluidInTankInternal(2);
        }
        return goo.getFluidInTankInternal(isRightSideMostly(hitVector, face) ? 0 : 1);
    }

    private boolean isOverMechanism(Vector3d hitVector) {
        switch (facing()) {
            case SOUTH:
                return hitVector.z >= this.getPos().getZ() + 0.625f;
            case NORTH:
                return hitVector.z <= this.getPos().getZ() + 0.375f;
            case EAST:
                return hitVector.x >= this.getPos().getX() + 0.625f;
            case WEST:
                return hitVector.x <= this.getPos().getX() + 0.375f;
        }
        return false;
    }

    private boolean isRightSideMostly(Vector3d hitVec, Direction face)
    {
        boolean isRight;
        if (this.facing().getAxis() == Direction.Axis.Z) {
            isRight = (facing() == NORTH) == (hitVec.getX() >= this.getPos().getX() + 0.5f);
        } else {
            isRight = (facing() == Direction.EAST) == (hitVec.getZ() >= this.getPos().getZ() + 0.5f);
        }
        return isRight;
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource)
    {
        // try to handle the bottom tank
        if (hitVec.y <= this.getPos().getY() + 0.375f) {
            if (face == facing() || isOverMechanism(hitVec)) {
                return null;
            }
            return bottomHandler;
        }
        return isRightSideMostly(hitVec, face) ? rightHandler : leftHandler;
    }
}
