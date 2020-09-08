package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.GooFlowPacket;
import com.xeno.goo.network.Networking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class GooBulbTileAbstraction extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver, GooFlowPacket.IGooFlowReceiver
{
    private BulbFluidHandler fluidHandler = createHandler();
    private LazyOptional<BulbFluidHandler> handler = LazyOptional.of(() -> fluidHandler);
    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;

    public GooBulbTileAbstraction(TileEntityType t) {
        super(t);
    }

    @Override
    public void tick() {
        if (world == null) {
            return;
        }

        if (world.isRemote) {
            // vertical fill visuals are client-sided, for a reason. We get sent activity from server but
            // the decay is local because that's needless packets otherwise. It's deterministic.
            decayVerticalFillVisuals();
            return;
        }

        boolean didStuff = doVerticalDrain() || doLateralShare();

        if (didStuff) {
            pruneEmptyGoo();
            onContentsChanged();
        }
    }

    public List<FluidStack> goo()
    {
        return this.goo;
    }

    @Override
    public void updateVerticalFill(Fluid f, float intensity)
    {
        this.verticalFillIntensity = intensity;
        this.verticalFillFluid = f;
    }

    public void toggleVerticalFillVisuals(Fluid f, float intensity)
    {
        verticalFillFluid = f;
        verticalFillIntensity = intensity; // default fill intensity is just "on", essentially
        if (world == null) {
            return;
        }
        Networking.sendToClientsAround(new GooFlowPacket(world.func_234923_W_(), pos, verticalFillFluid, verticalFillIntensity), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.func_234923_W_())), pos);
    }

    public void toggleVerticalFillVisuals(Fluid f)
    {
        toggleVerticalFillVisuals(f, 1f);
    }

    public float verticalFillIntensity()
    {
        return this.verticalFillIntensity;
    }

    public FluidStack verticalFillFluid()
    {
        return new FluidStack(verticalFillFluid, 1);
    }

    private float verticalFillDecay() {
        // throttle the intensity decay so it doesn't look so jittery. This will cause the first few frames to be slow
        // changing, but later frames will be proportionately somewhat faster.
        float decayRate = 0.2f;
        return Math.min(verticalFillIntensity * decayRate, 0.125f);
    }

    public void decayVerticalFillVisuals() {
        if (!isVerticallyFilled()) {
            return;
        }
        verticalFillIntensity -= verticalFillDecay(); // flow reduces each frame work tick until there's nothing left.
        float cutoffThreshold = 0.05f;
        if (verticalFillIntensity <= cutoffThreshold) {
            disableVerticalFillVisuals();
        }
    }

    public void disableVerticalFillVisuals() {
        verticalFillFluid = Fluids.EMPTY;
        verticalFillIntensity = 0f;
    }

    public boolean isVerticallyFilled() {
        return !verticalFillFluid.equals(Fluids.EMPTY) && verticalFillIntensity > 0f;
    }

    public void pruneEmptyGoo()
    {
        goo.removeIf(FluidStack::isEmpty);
    }

    public void addGoo(FluidStack fluidStack)
    {
        goo.add(fluidStack);
    }

    // if placed above another bulb, the bulb above will drain everything downward.
    private boolean doVerticalDrain() {
        if (this.goo.size() == 0) {
            return false;
        }

        // check the tile below us, if it's not a bulb, bail.
        TileEntity tile = FluidHandlerHelper.tileAtDirection(this, Direction.DOWN);
        if (tile == null) {
            return false;
        }

        // try fetching the bulb capabilities (upward) and throw an exception if it fails. return if null.
        IFluidHandler cap = FluidHandlerHelper.capability(tile, Direction.UP);

        if (cap == null) {
            return false;
        }

        // the maximum amount you can drain in a tick is here.
        int simulatedDrainLeft = transferRate();

        if (simulatedDrainLeft == 0) {
            return false;
        }

        boolean didStuff = false;
        // iterate over the stacks and ensure
        for(FluidStack s : goo) {
            if (simulatedDrainLeft <= 0) {
                break;
            }
            int simulatedDrain = trySendingFluid(simulatedDrainLeft, s, cap, true);
            if (simulatedDrain != simulatedDrainLeft) {
                didStuff = true;
            }
            simulatedDrainLeft -= simulatedDrain;
        }

        return didStuff;
    }

    private int transferRate()
    {
        return GooMod.config.gooTransferRate() * storageMultiplier();
    }

    // bulbs adjacent to one another laterally "equalize" their contents to allow some hotswapping behaviors.
    private boolean doLateralShare() {
        if (this.goo.size() == 0) {
            return false;
        }
        boolean didStuff = false;
        for(Direction d : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST })
        {
            // check the tile in this direction, if it's not another tile, pass;
            TileEntity tile = FluidHandlerHelper.tileAtDirection(this, d);
            if (tile == null) {
                continue;
            }

            // try fetching the bulb capabilities in the opposing direction and throw an exception if it fails. return if null.
            IFluidHandler cap = FluidHandlerHelper.capability(tile, d.getOpposite());

            if (cap == null) {
                continue;
            }

            // the maximum amount you can drain in a tick is here.
            int simulatedDrainLeft =  transferRate();

            // iterate over the stacks and ensure
            for(FluidStack s : goo) {
                if (simulatedDrainLeft <= 0) {
                    break;
                }
                // only "distribute" to the bulb adjacent if it has less than this one of whatever type (equalizing)
                // here there be dragons; simulate trying to remove an absurd amount of the fluid from the handler
                // it will return how much it has, if any.
                FluidStack stackInDestination = cap.drain(new FluidStack(s.getFluid(), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
                int bulbContains = stackInDestination.getAmount();
                int delta = s.getAmount() - bulbContains;
                // don't send it anything to avoid passing back 1 mB repeatedly.
                if (delta <= 1) {
                    continue;
                }
                int splitDelta = (int)Math.floor(delta / 2d);
                int amountToSend = Math.min(splitDelta, simulatedDrainLeft);

                int simulatedDrain = trySendingFluid(amountToSend, s, cap, false);
                if (simulatedDrain != simulatedDrainLeft) {
                    didStuff = true;
                }
                simulatedDrainLeft -= simulatedDrain;
            }
        }
        return didStuff;
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

        // this is purely visual and not vital to the fill operation
        if (cap instanceof BulbFluidHandler && isVerticalDrain) {
            ((BulbFluidHandler)cap).sendVerticalFillSignalForVisuals(s.getFluid());
        }

        // now call our drain, we're the sender.
        fluidHandler.drain(stackBeingSwapped, IFluidHandler.FluidAction.EXECUTE);

        // we can only handle so much work in a tick. Decrement the work limit. If it's zero, this loop breaks.
        // but if it was less than we're allowed to send, we can do more work in this tick, so it will continue.
        simulatedDrainLeft -= amountLeft;

        return simulatedDrainLeft;
    }

    public boolean hasFluid(Fluid fluid) {
        return !getSpecificGooType(fluid).equals(FluidStack.EMPTY);
    }

    public boolean fluidNamesAreEqual(FluidStack fluidStack, String gooType) {
        return Objects.requireNonNull(fluidStack.getFluid().getRegistryName()).getPath().equals(gooType);
    }

    public FluidStack getLeastQuantityGoo() {
        return goo.stream().filter(f -> !f.isEmpty() && f.getAmount() > 0).min(Comparator.comparingInt(FluidStack::getAmount)).orElse(FluidStack.EMPTY);
    }

    public FluidStack getSpecificGooType(Fluid fluid) {
        if (fluid == null) {
            return FluidStack.EMPTY;
        }
        return goo.stream().filter(f -> fluidNamesAreEqual(f, fluid.getRegistryName().getPath())).findFirst().orElse(FluidStack.EMPTY);
    }

    public int getTotalGoo() {
        return goo.stream().mapToInt(FluidStack::getAmount).sum();
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
        // tanks have omnidirectional gaskets so side is irrelevant.
        if (cap.equals(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids) {
        goo = fluids;
    }

    private BulbFluidHandler createHandler() {
        return new BulbFluidHandler(this);
    }

    public ItemStack getBulbStack(Block block) {
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

    public boolean hasSpace() {
        return getSpaceRemaining() > 0;
    }

    public int getSpaceRemaining()
    {
        return fluidHandler.getTankCapacity(0) - getTotalGoo();
    }

    public FluidStack getGooCorrespondingTo(Vector3d hitVec, ServerPlayerEntity player, Direction side)
    {
        Vector3d eyeHeight = Vector3d.ZERO;
        if (player != null) {
            eyeHeight = player.getEyePosition(0f);
        }
        // TODO make this way more awesome
        // int split = (int)goo.stream().filter(g -> !g.isEmpty()).count();
        // double dividend = split / 16d;
        return getLeastQuantityGoo();
    }

    public int storageMultiplier()
    {
        return 1;
    }
}
