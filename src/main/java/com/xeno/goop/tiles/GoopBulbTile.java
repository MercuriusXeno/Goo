package com.xeno.goop.tiles;

import com.xeno.goop.GoopMod;
import com.xeno.goop.fluids.BulbFluidHandler;
import com.xeno.goop.fluids.GoopBase;
import com.xeno.goop.network.FluidUpdatePacket;
import com.xeno.goop.network.Networking;
import com.xeno.goop.setup.Config;
import com.xeno.goop.setup.Registration;
import com.xeno.goop.setup.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
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
