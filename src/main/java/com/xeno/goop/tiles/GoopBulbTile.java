package com.xeno.goop.tiles;

import com.xeno.goop.GoopMod;
import com.xeno.goop.fluids.GoopBase;
import com.xeno.goop.network.FluidUpdatePacket;
import com.xeno.goop.network.Networking;
import com.xeno.goop.setup.Config;
import com.xeno.goop.setup.Registration;
import com.xeno.goop.setup.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class GoopBulbTile extends TileEntity implements ITickableTileEntity, IFluidHandler, FluidUpdatePacket.IFluidPacketReceiver {
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
            fill(new FluidStack(Registration.VOLATILE_GOOP.get(), Config.getTransferRate() / 3), IFluidHandler.FluidAction.EXECUTE);
            fill(new FluidStack(Registration.AQUATIC_GOOP.get(), Config.getTransferRate() / 3), IFluidHandler.FluidAction.EXECUTE);
            fill(new FluidStack(Registration.EARTHEN_GOOP.get(), Config.getTransferRate() / 3), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private boolean hasFluid(Fluid fluid) {
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

    public int getFluidAmount() {
        return getTotalGoop();
    }

    @Override
    public void read(CompoundNBT compound) {
        CompoundNBT fluidsFromCompound = compound.getCompound(Tags.GOOP);
        int indexes = goop.size();
        fluidsFromCompound.putInt("count", indexes);

        super.read(compound);
    }

    @Override
    public void markDirty() {
        super.markDirty();
    }

    protected void onContentsChanged() {
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

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        return super.write(compound);
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
        return new IFluidHandler() {

            @Override
            public int getTanks() {
                return 1;
            }

            @Nonnull
            @Override
            public FluidStack getFluidInTank(int tank) {
                return tank == 0 ? getLeastQuantityGoop() : FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return tank == 0 ? Config.GOOP_BULB_TOTAL_CAPACITY.get() : 0;
            }

            @Override
            public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
                return tank == 0 && stack.getFluid() instanceof GoopBase;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                int spaceRemaining = Config.getGoopBulbCapacity() - getTotalGoop();
                int transferAmount = Math.min(Config.getTransferRate(), spaceRemaining);
                transferAmount = Math.min(transferAmount, resource.getAmount());
                if (action == FluidAction.EXECUTE && transferAmount > 0) {
                    if (hasFluid(resource.getFluid())) {
                        FluidStack existingGoop = getSpecificGoopType((resource.getFluid()));
                        existingGoop.setAmount(existingGoop.getAmount() + transferAmount);
                    } else {
                        goop.add(new FluidStack(resource.getFluid(), transferAmount));
                    }
                    onContentsChanged();
                }

                return transferAmount;
            }

            @Nonnull
            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                // TODO
                return FluidStack.EMPTY;
            }

            @Nonnull
            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                // TODO
                return FluidStack.EMPTY;
            }
        };
    }

    @Override
    public int getTanks() {
        return fluidHandler.getTanks();
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return fluidHandler.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return fluidHandler.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        return fluidHandler.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return fluidHandler.fill(resource, action);
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return fluidHandler.drain(resource, action);
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return fluidHandler.drain(maxDrain, action);
    }
}
