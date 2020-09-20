package com.xeno.goo.entities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public abstract class GooEntity extends Entity implements IFluidHandler
{
    private static final DataParameter<Integer> GOO_SIZE = EntityDataManager.createKey(GooEntity.class, DataSerializers.VARINT);
    public FluidStack goo;

    public GooEntity(EntityType<?> entityTypeIn, World worldIn)
    {
        super(entityTypeIn, worldIn);
    }

    @Override
    protected void registerData() {
        this.dataManager.register(GOO_SIZE, 1);
    }

    public void notifyDataManagerChange(DataParameter<?> key) {
        if (GOO_SIZE.equals(key)) {
            this.recalculateSize();
        }

        super.notifyDataManagerChange(key);
    }

    protected void setSize() {
        this.dataManager.set(GOO_SIZE, goo.getAmount());
        this.recalculateSize();
    }

    @Override
    public int getTanks()
    {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank)
    {
        return goo;
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return 1000;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack)
    {
        return stack.isFluidEqual(this.goo);
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action)
    {
        int spaceRemaining = getTankCapacity(1) - goo.getAmount();
        int transferAmount = Math.min(resource.getAmount(), spaceRemaining);
        if (action == IFluidHandler.FluidAction.EXECUTE && transferAmount > 0) {
            goo.setAmount(goo.getAmount() + transferAmount);
            setSize();
        }

        return transferAmount;
    }

    @Override
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action)
    {
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), resource.getAmount()));
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
            setSize();
        }

        return result;
    }

    @Override
    public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action)
    {
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), maxDrain));
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
            setSize();
        }

        return result;
    }
}
