package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.Direction;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public class BulbFluidHandler implements IFluidHandler {
    private final GooBulbTile parent;
    public BulbFluidHandler(GooBulbTile t) {
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
        return tank == 0 ? parent.getLeastQuantityGoo() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? GooMod.config.bulbCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        return tank == 0 && stack.getFluid() instanceof GooFluid;
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        int spaceRemaining = parent.getSpaceRemaining();
        int transferAmount = Math.min(resource.getAmount(), spaceRemaining);
        if (action == FluidAction.EXECUTE && transferAmount > 0) {
            if (parent.hasFluid(resource.getFluid())) {
                FluidStack existingGoo = parent.getSpecificGooType((resource.getFluid()));
                existingGoo.setAmount(existingGoo.getAmount() + transferAmount);
            } else {
                parent.addGoo(new FluidStack(resource.getFluid(), transferAmount));
            }
            parent.onContentsChanged();
        }

        return transferAmount;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack s = parent.getLeastQuantityGoo();
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
        FluidStack parentStack = parent.getSpecificGooType(s.getFluid());
        FluidStack result = new FluidStack(s.getFluid(), Math.min(s.getAmount(), parentStack.getAmount()));
        if (action == FluidAction.EXECUTE) {
            parentStack.setAmount(parentStack.getAmount() - result.getAmount());
            parent.onContentsChanged();
        }

        return result;
    }

    public static IFluidHandler bulbCapability(GooBulbTile bulb, Direction dir)
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
}
