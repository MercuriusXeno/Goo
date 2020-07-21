package com.xeno.goop.tiles;

import com.xeno.goop.GoopMod;
import com.xeno.goop.blocks.GoopBulb;
import com.xeno.goop.fluids.BulbFluidHandler;
import com.xeno.goop.fluids.GoopBase;
import com.xeno.goop.network.FluidUpdatePacket;
import com.xeno.goop.network.Networking;
import com.xeno.goop.setup.Config;
import com.xeno.goop.setup.Registration;
import com.xeno.goop.setup.Tags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class GoopBulbTile extends TileEntity implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver {
    private IFluidHandler fluidHandler = createHandler();

    private LazyOptional<IFluidHandler> handler = LazyOptional.of(() -> fluidHandler);
    public List<FluidStack> goop = new ArrayList<>();

    public GoopBulbTile() {
        super(Registration.GOOP_BULB_TILE.get());
    }

    @Override
    public void tick() {
        if (world == null || world.isRemote) {
            return;
        }

        if (GoopMod.DEBUG) {
            fluidHandler.fill(new FluidStack(Registration.VOLATILE_GOOP.get(), Config.getTransferRate() / 3), IFluidHandler.FluidAction.EXECUTE);
            fluidHandler.fill(new FluidStack(Registration.AQUATIC_GOOP.get(), Config.getTransferRate() / 3), IFluidHandler.FluidAction.EXECUTE);
            fluidHandler.fill(new FluidStack(Registration.EARTHEN_GOOP.get(), Config.getTransferRate() / 3), IFluidHandler.FluidAction.EXECUTE);
        }

        doVerticalDrain();
        doLateralShare();
    }


    // if placed above another bulb, the bulb above will drain everything downward.
    private void doVerticalDrain() {
        // check the tile below us, if it's not a bulb, bail.
        GoopBulbTile bulb = getBulbInDirection(Direction.DOWN);
        if (bulb == null) {
            return;
        }

        // try fetching the bulb capabilities (upward) and throw an exception if it fails. return if null.
        IFluidHandler cap = tryGettingBulbCapabilities(bulb, Direction.UP);
        if (cap == null) {
            return;
        }

        // the maximum amount you can drain in a tick is here.
        int simulatedDrainLeft =  Config.getTransferRate();

        // iterate over the stacks and ensure
        for(FluidStack s : goop) {
            if (simulatedDrainLeft <= 0) {
                break;
            }
            simulatedDrainLeft = trySendingFluidToBulb(simulatedDrainLeft, s, cap, bulb);
        }
    }

    // bulbs adjacent to one another laterally "equalize" their contents to allow some hotswapping behaviors.
    private void doLateralShare() {
        for(Direction d : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST })
        {
            // check the tile in this direction, if it's not another bulb, pass;
            GoopBulbTile bulb = getBulbInDirection(d);
            if (bulb == null) {
                continue;
            }

            // try fetching the bulb capabilities in the opposing direction and throw an exception if it fails. return if null.
            IFluidHandler cap = tryGettingBulbCapabilities(bulb, d.getOpposite());
            if (cap == null) {
                return;
            }

            // the maximum amount you can drain in a tick is here.
            int simulatedDrainLeft =  Config.getTransferRate();

            // iterate over the stacks and ensure
            for(FluidStack s : goop) {
                if (simulatedDrainLeft <= 0) {
                    break;
                }
                // only "distribute" to the bulb adjacent if it has less than this one of whatever type (equalizing)
                int bulbContains = bulb.getSpecificGoopType(s.getFluid()).getAmount();
                int delta = s.getAmount() - bulbContains;
                // don't send it anything to avoid passing back 1 mB repeatedly.
                if (delta <= 1) {
                    continue;
                }
                int splitDelta = (int)Math.floor(delta / 2d);
                int amountToSend = Math.min(splitDelta, simulatedDrainLeft);
                simulatedDrainLeft = trySendingFluidToBulb(amountToSend, s, cap, bulb);
            }

        }
    }

    private IFluidHandler tryGettingBulbCapabilities(GoopBulbTile bulb, Direction dir)
    {
        LazyOptional<IFluidHandler> lazyCap = bulb.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, dir);
        IFluidHandler cap = null;
        try {
            cap = lazyCap.orElseThrow(() -> new Exception("Fluid handler expected from a tile entity that didn't contain one!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cap;
    }

    private int trySendingFluidToBulb(int simulatedDrainLeft, FluidStack s, IFluidHandler cap, GoopBulbTile bulb) {
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
        fluidHandler.drain(stackBeingSwapped, IFluidHandler.FluidAction.EXECUTE);

        // we can only handle so much work in a tick. Decrement the work limit. If it's zero, this loop breaks.
        // but if it was less than we're allowed to send, we can do more work in this tick, so it will continue.
        simulatedDrainLeft -= amountLeft;

        return simulatedDrainLeft;
    }

    private GoopBulbTile getBulbInDirection(Direction dir) {
        BlockPos below = this.pos.offset(dir);
        TileEntity tile = world.getTileEntity(below);
        if (tile == null) {
            return null;
        }
        if (!(tile instanceof GoopBulbTile)) {
            return null;
        }
        return (GoopBulbTile)tile;
    }

    public boolean hasFluid(Fluid fluid) {
        return !getSpecificGoopType(fluid).equals(FluidStack.EMPTY);
    }

    public boolean fluidNamesAreEqual(FluidStack fluidStack, String goopType) {
        return Objects.requireNonNull(fluidStack.getFluid().getRegistryName()).getPath().equals(goopType);
    }

    @Nonnull
    public FluidStack getLeastQuantityGoop() {
        return goop.stream().min(Comparator.comparingInt(FluidStack::getAmount)).orElse(FluidStack.EMPTY);
    }

    @Nonnull
    public FluidStack getSpecificGoopType(Fluid fluid) {
        return goop.stream().filter(f -> fluidNamesAreEqual(f, fluid.getRegistryName().getPath())).findFirst().orElse(FluidStack.EMPTY);
    }

    public int getTotalGoop() {
        return goop.stream().mapToInt(FluidStack::getAmount).sum();
    }

    public boolean isEmpty() { return goop.size() == 0; }

    @Override
    public void markDirty() {
        super.markDirty();
    }

    public void onContentsChanged() {
        markDirty();
        if (world == null) {
            return;
        }

        if (!world.isRemote) {
            if (world.getServer() == null) {
                return;
            }

            Networking.sendToClientsAround(new FluidUpdatePacket(world.dimension.getType(), pos, goop), world.getServer().getWorld(world.dimension.getType()), pos);
        }
    }

    private CompoundNBT serializeGoop()  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", goop.size());
        int index = 0;
        for(FluidStack s : goop) {
            CompoundNBT goopTag = new CompoundNBT();
            s.writeToNBT(goopTag);
            tag.put("goop" + index, goopTag);
            index++;
        }
        return tag;
    }

    private void deserializeGoop(CompoundNBT tag) {
        List<FluidStack> tagGoopList = new ArrayList<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT goopTag = tag.getCompound("goop" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(goopTag);
            tagGoopList.add(stack);
        }

        goop = tagGoopList;
        onContentsChanged();
    }


    @Override
    public CompoundNBT write(CompoundNBT tag) {
        tag.put("goop", serializeGoop());
        return super.write(tag);
    }

    @Override
    public void read(CompoundNBT compound) {
        CompoundNBT goopTag = compound.getCompound("goop");
        deserializeGoop(goopTag);
        super.read(compound);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        // tanks have omnidirectional gaskets so side is irrelevant.
        if (cap.equals(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids) {
        if (hasChanges(fluids)) {
            goop = fluids;

            // update the block model
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft.getInstance().worldRenderer.notifyBlockUpdate(null, pos, null, null, 3);
            });
            onContentsChanged();
        }
    }

    private boolean hasChanges(List<FluidStack> fluids) {
        boolean hasChanges = false;
        for(FluidStack f : fluids) {
            if (goop.stream().noneMatch(g -> g.isFluidStackIdentical(f))) {
                hasChanges = true;
            }
        }
        return hasChanges;
    }

    private IFluidHandler createHandler() {
        return new BulbFluidHandler(this);
    }

    public ItemStack getBulbStack() {
        ItemStack stack = new ItemStack(Registration.GOOP_BULB.get());

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
}
