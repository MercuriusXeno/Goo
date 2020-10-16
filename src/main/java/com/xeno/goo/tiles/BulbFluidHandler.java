package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class BulbFluidHandler implements IFluidHandler
{
    private final GooBulbTile parent;
    public BulbFluidHandler(GooBulbTile t) {
        parent = t;
    }

    public void sendVerticalFillSignalForVisuals(Fluid f, float i) {
        parent.toggleVerticalFillVisuals(f, i);
    }

    public void sendVerticalFillSignalForVisuals(Fluid f) {
        parent.toggleVerticalFillVisuals(f);
    }

    @Override
    public int getTanks() {
        return Math.max(1, parent.goo().size());
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return parent.goo().size() <= tank ? FluidStack.EMPTY : parent.goo().get(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return GooMod.config.bulbCapacity() * parent.storageMultiplier();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && stack.getFluid() instanceof GooFluid;
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        if (!isFluidValid(0, resource)) {
            return 0;
        }
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
}
