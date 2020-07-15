package com.xeno.goop.fluids;

import com.xeno.goop.setup.Config;
import com.xeno.goop.tiles.GoopBulbTile;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public class BulbFluidHandler implements IFluidHandler {
    private final GoopBulbTile parent;
    public BulbFluidHandler(GoopBulbTile t) {
        parent = t;

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
        return tank == 0 ? Config.GOOP_BULB_TOTAL_CAPACITY.get() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        return tank == 0 && stack.getFluid() instanceof GoopBase;
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        int spaceRemaining = Config.getGoopBulbCapacity() - parent.getTotalGoop();
        int transferAmount = Math.min(Config.getTransferRate(), spaceRemaining);
        transferAmount = Math.min(transferAmount, resource.getAmount());
        if (action == FluidAction.EXECUTE && transferAmount > 0) {
            if (parent.hasFluid(resource.getFluid())) {
                FluidStack existingGoop = parent.getSpecificGoopType((resource.getFluid()));
                existingGoop.setAmount(existingGoop.getAmount() + transferAmount);
            } else {
                parent.goop.add(new FluidStack(resource.getFluid(), transferAmount));
            }
            parent.onContentsChanged();
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
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
        // TODO
        return FluidStack.EMPTY;
    }
}
