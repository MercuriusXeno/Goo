package com.xeno.goop.tiles;

import com.xeno.goop.GoopMod;
import com.xeno.goop.setup.Config;
import com.xeno.goop.setup.Registration;
import net.minecraft.fluid.Fluid;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.*;

public class GoopBulbTile extends TileEntity implements ITickableTileEntity, IFluidTank, IFluidHandler {
    public List<FluidStack> Goop = new ArrayList<>();
    public GoopBulbTile() {
        super(Registration.GOOP_BULB_TILE.get());
    }

    @Override
    public void tick() {
        if (GoopMod.DEBUG) {
            fill(new FluidStack(Registration.VOLATILE_GOOP.get(), 15), FluidAction.EXECUTE);
            System.out.println("goop bulb is ticking, fluid: " + getFluidAmount());
        }
    }

    private boolean hasFluid(Fluid fluid) {
        return !getSpecificGoopType(fluid).equals(FluidStack.EMPTY);
    }

    @Nonnull
    public boolean fluidNamesAreEqual(FluidStack fluidStack, String goopType) {
        return Objects.requireNonNull(fluidStack.getFluid().getRegistryName()).getPath().equals(goopType);
    }

    @Nonnull
    public FluidStack getLeastQuantityGoop() {
        return Goop.stream().min(Comparator.comparingInt(FluidStack::getAmount)).orElse(FluidStack.EMPTY);
    }

    @Nonnull
    public FluidStack getSpecificGoopType(Fluid fluid) {
        return Goop.stream().filter(f -> fluidNamesAreEqual(f, fluid.getRegistryName().getPath())).findFirst().orElse(FluidStack.EMPTY);
    }

    @Nonnull
    public FluidStack getSpecificGoopType(String goopType) {
        return Goop.stream().filter(f -> fluidNamesAreEqual(f, goopType)).findFirst().orElse(FluidStack.EMPTY);
    }

    @Nonnull
    public int getTotalGoop() {
        return Goop.stream().mapToInt(FluidStack::getAmount).sum();
    }

    @Nonnull
    @Override
    public FluidStack getFluid() {
        return getLeastQuantityGoop();
    }

    @Override
    public int getFluidAmount() {
        return getTotalGoop();
    }

    @Override
    public int getCapacity() {
        return Config.GOOP_BULB_CAPACITY;
    }

    @Override
    public boolean isFluidValid(FluidStack stack) {
        return false;
    }

    @Override
    public int getTanks() {
        return 0;
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return null;
    }

    @Override
    public int getTankCapacity(int tank) {
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (action == FluidAction.EXECUTE) {
            if (hasFluid(resource.getFluid())) {
                FluidStack existingGoop = getSpecificGoopType((resource.getFluid()));
                existingGoop.setAmount(existingGoop.getAmount() + resource.getAmount());
            } else {
                Goop.add(resource);
            }
        }
        return 0;
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
}
