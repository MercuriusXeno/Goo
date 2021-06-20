package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class VesselAbstractionCapability extends FluidHandlerItemStack
{
    public VesselAbstractionCapability(ItemStack container)
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
        return getFluids().size();
    }

    public List<FluidStack> getFluids()
    {
        CompoundNBT tagCompound = container.getTag();
        if (tagCompound == null || !tagCompound.contains(FLUID_NBT_KEY))
        {
            return newListWithEmptyFluidStack();
        }

        return deserializeGoo(tagCompound.getCompound(FLUID_NBT_KEY));
    }

    private List<FluidStack> newListWithEmptyFluidStack() {
        List<FluidStack> result = new ArrayList<>();
        result.add(FluidStack.EMPTY);
        return result;
    }

    @Override
    @Nonnull
    public FluidStack getFluid()
    {
        return getFluids().get(0);
    }

    protected void setFluid(FluidStack fluid)
    {
        List<FluidStack> fluids = getFluids();

        for(FluidStack f : fluids) {
            if (f.isFluidEqual(fluid)) {
                f.setAmount(fluid.getAmount());
            }
        }

        setFluids(fluids);
    }

    private void removeFluid(FluidStack fluid, boolean isRemovingPrimary) {
        List<FluidStack> fluids = getFluids();

        fluids.removeIf(f -> f.isFluidEqual(fluid));

        setFluids(fluids);

        if (isRemovingPrimary) {
            shuffleEmptyToPrimary();
        }
    }

    private void shuffleEmptyToPrimary() {
        List<FluidStack> fluids = getFluids();
        if (fluids.size() <= 1 || fluids.get(0).isEmpty()) {
            return;
        }
        int indexOfEmpty = -1;
        for(int i = 1; i < fluids.size(); i++) {
            if (fluids.get(i).isEmpty()) {
                indexOfEmpty = i;
            }
        }

        fluids.add(fluids.get(0).copy());
        fluids.remove(indexOfEmpty);
        fluids.set(0, FluidStack.EMPTY);
        setFluids(fluids);
    }

    protected void setFluids(List<FluidStack> fluids) {
        if (!container.hasTag())
        {
            container.setTag(new CompoundNBT());
        }

        CompoundNBT tag = container.getTag();

        tag.put(FLUID_NBT_KEY, serializeGoo(fluids));
        container.setTag(tag);
    }

    @Override
    public FluidStack getFluidInTank(int tank)
    {
        return getFluid();
    }

    protected CompoundNBT serializeGoo(List<FluidStack> goo)  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", goo.size());
        int index = 0;
        for(FluidStack s : goo) {
            CompoundNBT gooTag = new CompoundNBT();
            s.writeToNBT(gooTag);
            tag.put("goo" + index, gooTag);
            index++;
        }
        return tag;
    }

    protected List<FluidStack> deserializeGoo(CompoundNBT tag) {
        if (!tag.contains(("count"))) {
            return newListWithEmptyFluidStack();
        }
        List<FluidStack> tagGooList = new ArrayList<>();
        int size = tag.getInt("count");
        boolean hasEmpty = false;
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
            if (stack.isEmpty()) {
                if (hasEmpty) {
                    continue;
                }
                hasEmpty = true;
            }
            tagGooList.add(stack);
        }

        if (tagGooList.size() == 0) {
            tagGooList.add(FluidStack.EMPTY);
        }

        return tagGooList;
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return capacity();
    }

    public int capacity() {
        if (GooMod.config == null) {
            return 8000;
        }
        return Math.min(Integer.MAX_VALUE, GooMod.config.vesselCapacity() * containmentMultiplier(Vessel.containment(this.container)));
    }

    public int totalFluid() {
        return getFluids().stream().mapToInt(FluidStack::getAmount).sum();
    }

    public static int storageForDisplay(ItemStack stack)
    {
        IFluidHandlerItem handler = FluidHandlerHelper.capability(stack);
        if (handler == null) {
            return 0;
        }

        if (handler instanceof VesselAbstractionCapability) {
            return handler.getTankCapacity(0);
        }
        return 0;
    }

    public static int containmentMultiplier(int containment) {
        return (int)Math.pow(GooMod.config.vesselContainmentMultiplier(), containment);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack)
    {
        return !stack.isEmpty() && stack.getFluid() instanceof GooFluid;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action)
    {
        // track our "primary". If our primary is empty and we added a fluid, switch to the new fluid
        FluidStack primary = getFluid();
        List<FluidStack> fluids = getFluids();

        // probably we can hold it, assuming we have space.
        int possibleFill = this.capacity() - totalFluid();
        if (possibleFill <= 0) {
            return 0;
        }

        if (action == FluidAction.EXECUTE) {
            FluidStack result = resource.copy();
            if (result.getAmount() > possibleFill) {
                result.setAmount(possibleFill);
            }
            for(FluidStack f : fluids) {
                if (f.isFluidEqual(result)) {
                    f.setAmount(f.getAmount() + result.getAmount());
                    setFluids(fluids);
                    return possibleFill;
                }
            }
            fluids.add(result);
            setFluids(fluids);
            if (primary.isEmpty()) {
                swapToFluid(result);
            }
        }
        return possibleFill;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action)
    {
        // track our "primary". If our primary gets removed, shuffle empty to it instead of the next one.
        FluidStack primary = getFluid();
        List<FluidStack> fluids = getFluids();
        for(FluidStack f : fluids) {
            if (!f.isFluidEqual(resource)) {
                continue;
            }

            int maxDrain = Math.min(f.getAmount(), resource.getAmount());
            FluidStack result = resource.copy();
            result.setAmount(maxDrain);
            if (action == FluidAction.EXECUTE) {
                FluidStack drainedRemainder = result.copy();
                // if this was all of it, remove it.
                if (f.getAmount() == maxDrain) {
                    boolean isRemovingPrimary = f.isFluidEqual(primary);
                    removeFluid(f, isRemovingPrimary);
                } else {
                    // otherwise, just change the amount to the amount remaining
                    drainedRemainder.setAmount(f.getAmount() - maxDrain);
                    setFluid(drainedRemainder);
                }
            }
            return result;
        }
        return FluidStack.EMPTY;
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
            if (getFluid().getAmount() == maxDrain) {
                removeFluid(result, true);
            } else {
                drainedRemainder.setAmount(getFluid().getAmount() - maxDrain);
                setFluid(drainedRemainder);
            }
        }
        return result;
    }

    public void swapToFluid(FluidStack target) {
        // switch to empty from a fluid
        if (!getFluid().isEmpty()) {
            shuffleEmptyToPrimary();
        }
        List<FluidStack> fluids = getFluids();
        if(!target.isEmpty()){
            int index = -1;
            for(int i = 0; i < fluids.size(); i++) {
                if (fluids.get(i).isFluidEqual(target)) {
                    index = i;
                    break;
                }
            }

            if (index == -1) {
                return;
            }

            fluids.set(0, fluids.get(index).copy());
            fluids.set(index, FluidStack.EMPTY);
        }
        setFluids(fluids);
    }
}
