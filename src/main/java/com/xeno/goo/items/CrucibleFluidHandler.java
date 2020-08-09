package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.EntryHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CrucibleFluidHandler implements IFluidHandlerItem, ICapabilityProvider
{
    private final LazyOptional<IFluidHandlerItem> holder = LazyOptional.of(() -> this);

    private Fluid current;
    private FluidStack[] fluid;
    private int holding;
    private ItemStack parent;

    public CrucibleFluidHandler(ItemStack stack) {
        this.parent = stack;
        this.fluid = new FluidStack[getTanks()];
        this.holding = getHolding(stack);
        this.current = Fluids.EMPTY;
    }

    private int getHolding(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.HOLDING_ENCHANTMENT.get(), stack);
    }

    public FluidStack[] fluid() {
        return this.fluid;
    }

    @Nonnull
    @Override
    public ItemStack getContainer()
    {
        return parent;
    }

    @Override
    public int getTanks()
    {
        return parent.getItem() instanceof MobiusCrucible ? 8 : 1;
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank)
    {
        return tank < fluid.length ? fluid[tank] : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return GooMod.mainConfig.crucibleBaseCapacity() * (int)Math.ceil(Math.pow(GooMod.mainConfig.crucibleHoldingMultiplier(), holding));
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack)
    {
        return fluid[tank].isEmpty() || fluid[tank].getFluid().isEquivalentTo(stack.getFluid());
    }

    @Override
    public int fill(FluidStack resource, FluidAction action)
    {
        return 0;
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action)
    {
        for(FluidStack stack : fluid) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.getFluid().isEquivalentTo(resource.getFluid())) {
                continue;
            }

            FluidStack result = stack.copy();
            result.setAmount(result.getAmount() - Math.min(result.getAmount(), resource.getAmount()));
            if (action == FluidAction.EXECUTE) {
                stack.setAmount(result.getAmount());
            }

            return result;
        }
        return FluidStack.EMPTY;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action)
    {
        if (current.equals(Fluids.EMPTY)) {
            return FluidStack.EMPTY;
        }
        for(FluidStack stack : fluid) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.getFluid().isEquivalentTo(current)) {
                continue;
            }

            FluidStack result = stack.copy();
            result.setAmount(result.getAmount() - Math.min(result.getAmount(), maxDrain));
            if (action == FluidAction.EXECUTE) {
                stack.setAmount(result.getAmount());
            }

            return result;
        }
        return FluidStack.EMPTY;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
    {
        return CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY.orEmpty(cap, holder);
    }
}
