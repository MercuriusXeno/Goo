package com.xeno.goop.fluids;

import com.xeno.goop.GoopMod;
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
        return tank == 0 ? GoopMod.config.bulbGoopCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        return tank == 0 && stack.getFluid() instanceof GoopBase;
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        int spaceRemaining = GoopMod.config.bulbGoopCapacity() - parent.getTotalGoop();
        int transferAmount = Math.min(GoopMod.config.goopTransferRate(), spaceRemaining);
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
        FluidStack s = parent.getLeastQuantityGoop();
        FluidStack result = new FluidStack(s.getFluid(), Math.min(s.getAmount(), maxDrain));
        if (action == FluidAction.EXECUTE) {
            s.setAmount(s.getAmount() - result.getAmount());
            if (s.getAmount() == 0) {
                parent.goop.remove(s);
            }
        }

        return result;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack s, FluidAction action) {
        FluidStack result = new FluidStack(s.getFluid(), s.getAmount());
        if (action == FluidAction.EXECUTE) {
            s.setAmount(s.getAmount() - result.getAmount());
            if (s.getAmount() == 0) {
                parent.goop.remove(parent.getSpecificGoopType(s.getFluid()));
            }
        }

        return result;
    }
}
