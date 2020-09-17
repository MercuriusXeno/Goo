package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class CrucibleFluidHandler implements IFluidHandler
{
    private final CrucibleTile parent;
    public CrucibleFluidHandler(CrucibleTile t) {
        parent = t;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? parent.onlyGoo() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? GooMod.config.crucibleInputCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && stack.getFluid() instanceof GooFluid && parent.onlyGoo().isFluidEqual(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        int spaceRemaining = parent.getSpaceRemaining(resource);
        int transferAmount = Math.min(resource.getAmount(), spaceRemaining);
        if (action == FluidAction.EXECUTE && transferAmount > 0) {
            if (parent.hasFluid(resource.getFluid())) {
                parent.onlyGoo().setAmount(parent.onlyGoo().getAmount() + transferAmount);
            } else {
                parent.setGoo(new FluidStack(resource.getFluid(), transferAmount));
            }
            parent.onContentsChanged();
        }

        return transferAmount;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack result = new FluidStack(parent.onlyGoo().getFluid(), Math.min(parent.onlyGoo().getAmount(), maxDrain));
        if (action == FluidAction.EXECUTE) {
            parent.onlyGoo().setAmount(parent.onlyGoo().getAmount() - result.getAmount());
            parent.onContentsChanged();
        }

        return result;
    }

    @Override
    public FluidStack drain(FluidStack s, FluidAction action) {
        if (!parent.hasFluid(s.getFluid())) {
            return FluidStack.EMPTY;
        }
        FluidStack result = new FluidStack(s.getFluid(), Math.min(s.getAmount(), parent.onlyGoo().getAmount()));
        if (action == FluidAction.EXECUTE) {
            parent.onlyGoo().setAmount(parent.onlyGoo().getAmount() - result.getAmount());
            parent.onContentsChanged();
        }

        return result;
    }
}
