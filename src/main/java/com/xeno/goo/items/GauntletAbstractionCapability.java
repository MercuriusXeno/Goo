package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

import javax.annotation.Nonnull;

public class GauntletAbstractionCapability extends FluidHandlerItemStack
{
    /**
     * @param container The container itemStack, data is stored on it directly as NBT.
     */
    public GauntletAbstractionCapability(@Nonnull ItemStack container)
    {
        super(container, 0);
    }

    @Override
    public boolean canDrainFluidType(FluidStack fluid)
    {
        return isFluidValid(0, fluid);
    }

    @Override
    public boolean canFillFluidType(FluidStack fluid)
    {
        return isFluidValid(0, fluid);
    }

    @Override
    public ItemStack getContainer()
    {
        return this.container;
    }

    @Override
    public int getTanks()
    {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank)
    {
        return getFluid();
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return GooMod.config.gauntletCapacity() * holdingMultiplier(Gauntlet.containment(this.container));
    }

    public static int storageForDisplay(ItemStack stack)
    {
        IFluidHandlerItem handler = FluidHandlerHelper.capability(stack);
        if (handler == null) {
            return 0;
        }

        if (handler instanceof GauntletAbstractionCapability) {
            return handler.getTankCapacity(0);
        }
        return 0;
    }

    public static int holdingMultiplier(int holding) {
        return (int)Math.pow(GooMod.config.gauntletHoldingMultiplier(), holding);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack)
    {
        return !stack.isEmpty() && stack.getFluid() instanceof GooFluid;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action)
    {
        // can't hold it, already holding something else.
        if (!getFluid().isEmpty() && !getFluid().isFluidEqual(resource)) {
            return 0;
        }

        // probably we can hold it, assuming we have space.
        int possibleFill = this.getTankCapacity(0) - getFluid().getAmount();
        if (possibleFill <= 0) {
            return 0;
        }

        if (action == FluidAction.EXECUTE) {
            FluidStack result = resource.copy();
            if (result.getAmount() > possibleFill) {
                result.setAmount(possibleFill);
            }
            if (!getFluid().isEmpty()) {
                result.setAmount(result.getAmount() + getFluid().getAmount());
            }
            setFluid(result);
        }
        return possibleFill;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action)
    {
        // can't hold it, already holding something else.
        if (getFluid().isEmpty() || !getFluid().isFluidEqual(resource)) {
            return FluidStack.EMPTY;
        }

        int maxDrain = Math.min(getFluid().getAmount(), resource.getAmount());
        FluidStack result = resource.copy();
        result.setAmount(maxDrain);
        if (action == FluidAction.EXECUTE) {
            FluidStack drainedRemainder = result.copy();
            drainedRemainder.setAmount(getFluid().getAmount() - maxDrain);
            setFluid(drainedRemainder);
        }
        return result;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action)
    {
        // can't hold it, already holding something else.
        if (getFluid().isEmpty()) {
            return FluidStack.EMPTY;
        }

        maxDrain = Math.min(getFluid().getAmount(), maxDrain);
        FluidStack result = getFluid().copy();
        result.setAmount(maxDrain);
        if (action == FluidAction.EXECUTE) {
            FluidStack drainedRemainder = result.copy();
            drainedRemainder.setAmount(getFluid().getAmount() - maxDrain);
            setFluid(drainedRemainder);
        }
        return result;
    }
}
