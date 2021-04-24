package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class BlobContext
{
    private final World world;
    private final IFluidHandler fluidHandler;
    private final BlockPos blockPos;
    private final BlockState blockState;
    private final Vector3d blockCenterVec;
    private final Fluid fluid;
    private final GooBlob blob;
    private String interactionKey;

    public BlobContext(BlockRayTraceResult blockResult, BlockPos blockPos, World world, GooBlob entity,
                       Fluid fluid) {
        this.world = world;
        //noinspection OptionalGetWithoutIsPresent
        this.fluidHandler = entity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY).resolve().get();
        this.blockPos = blockResult.getPos();
        this.blockState = world.getBlockState(this.blockPos);
        this.blockCenterVec = Vector3d.copy(blockPos).add(0.5d, 0.5d, 0.5d);
        this.fluid = fluid;
        this.blob = entity;
    }

    public BlobContext(BlockRayTraceResult blockResult, GooBlob gooBlob, Fluid fluid)
    {
        this(blockResult, gooBlob.getPosition(), gooBlob.getEntityWorld(), gooBlob, fluid);
    }

    public BlobContext withKey(String interactionKey) {
        this.interactionKey = interactionKey;
        return this;
    }

    private static BlockRayTraceResult rayTraceResultFrom(GooBlob gooBlob)
    {
        return new BlockRayTraceResult(gooBlob.getPositionVec(), gooBlob.sideWeLiveOn(), gooBlob.blockAttached(), false);
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

    public Vector3d blockCenterVec()
    {
        return this.blockCenterVec;
    }

    public IFluidHandler fluidHandler()
    {
        return this.fluidHandler;
    }

    public Fluid fluid() { return this.fluid; }

    public String interactionKey() { return this.interactionKey; }

    public GooBlob blob()
    {
        return this.blob;
    }
}
