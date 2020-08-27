package com.xeno.goo.items;

import com.google.common.collect.Lists;
import com.xeno.goo.library.Compare;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class GooHolderData
{
    private FluidStack heldGoo;

    public GooHolderData()
    {
        heldGoo = FluidStack.EMPTY;
    }

    public FluidStack drain(ItemStack stack, int maxDrain, IFluidHandler.FluidAction action)
    {
        if (heldGoo.isEmpty()) {
            return FluidStack.EMPTY;
        }

        if (heldGoo.getAmount() > maxDrain) {
            FluidStack result = heldGoo.copy();
            result.setAmount(maxDrain);
            if (action == IFluidHandler.FluidAction.SIMULATE) {
                return result;
            }
            heldGoo.setAmount(heldGoo.getAmount() - maxDrain);
            stack.setTag(serializeNBT());
            return result;
        }

        FluidStack result = heldGoo.copy();
        if (action == IFluidHandler.FluidAction.SIMULATE) {
            return result;
        }
        heldGoo = FluidStack.EMPTY;
        stack.setTag(serializeNBT());
        return result;
    }

    public void deserializeNBT(CompoundNBT tag)
    {
        heldGoo = FluidStack.loadFluidStackFromNBT(tag);
    }

    public CompoundNBT serializeNBT()
    {
        return heldGoo.writeToNBT(new CompoundNBT());
    }

    protected int getCapacity(ItemStack stack, FluidStack resource)
    {
        if (heldGoo.isEmpty()) {
            return capacity(stack);
        }

        if (heldGoo.isFluidEqual(resource)) {
            return capacity(stack) - heldGoo.getAmount();
        }

        return 0;
    }

    private int capacity(ItemStack stack)
    {
        return 1000;
    }

    public int fill(ItemStack stack, FluidStack resource, IFluidHandler.FluidAction action)
    {
        int spaceLeft = this.getCapacity(stack, resource);
        int amountTransferred = Math.min(spaceLeft, resource.getAmount());
        if (amountTransferred <= 0) {
            return 0;
        }

        if (action == IFluidHandler.FluidAction.EXECUTE) {
            if (heldGoo.isFluidEqual(resource)) {
                heldGoo.setAmount(heldGoo.getAmount() + amountTransferred);
                stack.setTag(serializeNBT());
                return amountTransferred;
            }

            if (heldGoo.isEmpty()) {
                heldGoo = new FluidStack(resource.getFluid(), amountTransferred);
                stack.setTag(serializeNBT());
                return amountTransferred;
            }
        }
        return amountTransferred;
    }

    public void addInformation(List<ITextComponent> tooltip)
    {
        int index = 0;
        int displayIndex = 0;
        IFormattableTextComponent fluidAmount = null;
        // struggling with values sorting stupidly. Trying to do fix sort by doing this:
        List<FluidStack> sortedValues = Lists.newArrayList(heldGoo);
        for(FluidStack v : sortedValues) {
            index++;
            if (v.isEmpty()) {
                continue;
            }
            String decimalValue = " " + NumberFormat.getNumberInstance(Locale.ROOT).format(v.getAmount()) + " mB";
            String fluidTranslationKey = v.getTranslationKey();
            if (fluidTranslationKey == null) {
                continue;
            }
            displayIndex++;
            if (displayIndex % 2 == 1) {
                fluidAmount = new TranslationTextComponent(fluidTranslationKey).appendString(decimalValue);
            } else {
                if (fluidAmount != null) {
                    fluidAmount = fluidAmount.appendString(", ").append(new TranslationTextComponent(fluidTranslationKey).appendString(decimalValue));
                }
            }
            if (displayIndex % 2 == 0 || index == sortedValues.size()) {
                tooltip.add(fluidAmount);
            }
        }
    }

    public FluidStack heldGoo()
    {
        return heldGoo;
    }
}
