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
    private final GooBulbTileAbstraction parent;
    public BulbFluidHandler(GooBulbTileAbstraction t) {
        parent = t;
    }

    public IFluidHandler getBulbCapabilityInDirection(Direction d)
    {
        // check the tile below us, if it's not a bulb, bail.
        GooBulbTileAbstraction bulb = getBulbInDirection(d);
        if (bulb == null) {
            return null;
        }

        // try fetching the bulb capabilities (upward) and throw an exception if it fails. return if null.
        return FluidHandlerHelper.capability(bulb, d.getOpposite());
    }

    public void sendVerticalFillSignalForVisuals(Fluid f) {
        parent.toggleVerticalFillVisuals(f);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? parent.getLeastQuantityGoo() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? GooMod.config.bulbCapacity() * parent.storageMultiplier() : 0;
    }

    public static GooBulbTileAbstraction getBulbInDirection(TileEntity tile, Direction d)
    {
        if (tile.getWorld() == null) {
            return null;
        }
        BlockPos posInDirection = tile.getPos().offset(d);
        TileEntity result = tile.getWorld().getTileEntity(posInDirection);
        if (result == null) {
            return null;
        }
        if (!(result instanceof GooBulbTileAbstraction)) {
            return null;
        }
        return (GooBulbTileAbstraction)result;
    }

    public GooBulbTileAbstraction getBulbInDirection(Direction dir) {
        return getBulbInDirection(parent, dir);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
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
