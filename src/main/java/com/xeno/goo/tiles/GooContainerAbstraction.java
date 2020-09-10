package com.xeno.goo.tiles;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public abstract class GooContainerAbstraction extends TileEntity
{
    protected List<FluidStack> goo = new ArrayList<>();

    public List<FluidStack> goo() {
        return this.goo;
    }

    public GooContainerAbstraction(TileEntityType<?> tileEntityTypeIn)
    {
        super(tileEntityTypeIn);
    }

    public static List<FluidStack> deserializeGooForDisplay(CompoundNBT tag) {
        List<FluidStack> tagGooList = new ArrayList<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
            tagGooList.add(stack);
        }

        return tagGooList;
    }

    protected CompoundNBT serializeGoo()  {
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

    protected void deserializeGoo(CompoundNBT tag) {
        List<FluidStack> tagGooList = new ArrayList<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
            if (stack.isEmpty()) {
                continue;
            }
            tagGooList.add(stack);
        }

        goo = tagGooList;
    }

    public abstract FluidStack getGooFromTargetRayTraceResult(BlockRayTraceResult target);
}
