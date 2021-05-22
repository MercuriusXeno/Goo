package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class BlobContext
{
    private final World world;
    private final IFluidHandler fluidHandler;
    private final BlockPos blockPos;
    private final BlockState blockState;
    private final GooBlob blob;

    public BlobContext(BlockPos blockHitPos, GooBlob gooBlob)
    {
        this.world = gooBlob.world;
        //noinspection OptionalGetWithoutIsPresent
        this.fluidHandler = gooBlob.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY).resolve().get();
        this.blockPos = blockHitPos;
        this.blockState = world.getBlockState(this.blockPos);
        this.blob = gooBlob;
    }

    public BlockState blockState()
    {
        return this.blockState;
    }

    public World world()
    {
        return this.world;
    }

    public BlockPos blockPos()
    {
        return this.blockPos;
    }

    public Block block()
    {
        return this.blockState.getBlock();
    }

    public boolean isRemote()
    {
        return this.world.isRemote();
    }

    public void setBlockState(BlockState newState)
    {
        this.world.setBlockState(this.blockPos, newState);
    }

    public FluidState fluidState()
    {
        return this.blockState.getFluidState();
    }

    public IFluidHandler fluidHandler()
    {
        return this.fluidHandler;
    }

    public GooBlob blob()
    {
        return this.blob;
    }
}
