package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class MixerFluidHandler implements IFluidHandler
{
    private final MixerTile parent;
    private final Direction side;
    public MixerFluidHandler(MixerTile t, Direction side) {
        this.parent = t;
        this.side = side;
    }

    public IFluidHandler getMixerCapabilityInDirection(Direction d)
    {
        // check the tile below us, if it's not a bulb, bail.
        MixerTile mixer = getMixerInDirection(d);
        if (mixer == null) {
            return null;
        }

        // try fetching the bulb capabilities (upward) and throw an exception if it fails. return if null.
        return FluidHandlerHelper.capability(mixer, d.getOpposite());
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? parent.goo(this.side) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank > 0) {
            return 0;
        }
        return GooMod.config.mixerInputCapacity();
    }

    public static MixerTile getMixerInDirection(TileEntity tile, Direction d)
    {
        if (tile.getWorld() == null) {
            return null;
        }
        BlockPos posInDirection = tile.getPos().offset(d);
        TileEntity result = tile.getWorld().getTileEntity(posInDirection);
        if (result == null) {
            return null;
        }
        if (!(result instanceof MixerTile)) {
            return null;
        }
        return (MixerTile)result;
    }

    public MixerTile getMixerInDirection(Direction dir) {
        return getMixerInDirection(parent, dir);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && stack.getFluid() instanceof GooFluid;
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        int spaceRemaining = parent.getSpaceRemaining(this.side, resource);
        int transferAmount = Math.min(resource.getAmount(), spaceRemaining);
        if (action == FluidAction.EXECUTE && transferAmount > 0) {
            if (parent.hasFluid(this.side, resource.getFluid())) {
                FluidStack existingGoo = parent.goo(this.side, resource.getFluid());
                existingGoo.setAmount(existingGoo.getAmount() + transferAmount);
            } else {
                parent.addGoo(this.side, new FluidStack(resource.getFluid(), transferAmount));
            }
            parent.onContentsChanged();
        }

        return transferAmount;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack s = parent.goo(this.side);
        FluidStack result = new FluidStack(s.getFluid(), Math.min(s.getAmount(), maxDrain));
        if (action == FluidAction.EXECUTE) {
            s.setAmount(s.getAmount() - result.getAmount());
            parent.onContentsChanged();
        }

        return result;
    }

    @Override
    public FluidStack drain(FluidStack s, FluidAction action) {
        FluidStack parentStack = parent.goo(this.side, s.getFluid());
        FluidStack result = new FluidStack(s.getFluid(), Math.min(s.getAmount(), parentStack.getAmount()));
        if (action == FluidAction.EXECUTE) {
            parentStack.setAmount(parentStack.getAmount() - result.getAmount());
            parent.onContentsChanged();
        }

        return result;
    }
}
