package com.xeno.goo.fluids;

import com.xeno.goo.GooMod;
import com.xeno.goo.tiles.GoopBulbTile;
import net.minecraft.fluid.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public class BulbFluidHandler implements IFluidHandler {
    private final GoopBulbTile parent;
    public BulbFluidHandler(GoopBulbTile t) {
        parent = t;
    }

    public void sendVerticalFillSignalForVisuals(Fluid f) {
        parent.toggleVerticalFillVisuals(f);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? parent.getLeastQuantityGoop() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? GooMod.mainConfig.bulbGoopCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        return tank == 0 && stack.getFluid() instanceof GooBase;
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        int spaceRemaining = parent.getSpaceRemaining();
        int transferAmount = Math.min(GooMod.mainConfig.goopTransferRate(), spaceRemaining);
        transferAmount = Math.min(transferAmount, resource.getAmount());
        if (action == FluidAction.EXECUTE && transferAmount > 0) {
            if (parent.hasFluid(resource.getFluid())) {
                FluidStack existingGoop = parent.getSpecificGoopType((resource.getFluid()));
                existingGoop.setAmount(existingGoop.getAmount() + transferAmount);
            } else {
                parent.addGoop(new FluidStack(resource.getFluid(), transferAmount));
            }
            parent.onContentsChanged();
        }

        return transferAmount;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack s = parent.getLeastQuantityGoop();
        FluidStack result = new FluidStack(s.getFluid(), Math.min(s.getAmount(), maxDrain));
        if (action == FluidAction.EXECUTE) {
            s.setAmount(s.getAmount() - result.getAmount());
            parent.onContentsChanged();
        }

        return result;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack s, FluidAction action) {
        FluidStack parentStack = parent.getSpecificGoopType(s.getFluid());
        FluidStack result = new FluidStack(s.getFluid(), Math.min(s.getAmount(), parentStack.getAmount()));
        if (action == FluidAction.EXECUTE) {
            parentStack.setAmount(parentStack.getAmount() - result.getAmount());
            parent.onContentsChanged();
        }

        return result;
    }
}
