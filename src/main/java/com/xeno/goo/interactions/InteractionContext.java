package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEventData;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class InteractionContext
{
    private final BlockRayTraceResult hitResult;
    private final World world;
    private final IFluidHandler fluidHandler;
    private final BlockPos blockPos;
    private final Vector3d hitVec;
    private final BlockState blockState;
    private final Direction sideHit;
    private final Vector3d blockCenterVec;
    private final Fluid fluid;
    private final GooSplat splat;
    private String interactionKey;

    public InteractionContext(BlockRayTraceResult trace, World world, GooSplat entity,
            Fluid fluid) {
        this.hitResult = trace;
        this.world = world;
        this.fluidHandler = entity;
        this.blockPos = trace.getPos();
        this.sideHit = trace.getFace();
        this.blockState = world.getBlockState(this.blockPos);
        this.hitVec = trace.getHitVec();
        this.blockCenterVec = new Vector3d(this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ())
            .add(0.5d, 0.5d, 0.5d);
        this.fluid = fluid;
        this.splat = entity;
    }

    public InteractionContext(GooSplat gooSplat, Fluid fluid)
    {
        this(rayTraceResultFrom(gooSplat), gooSplat.getEntityWorld(), gooSplat, fluid);
    }

    public InteractionContext withKey(String interactionKey) {
        this.interactionKey = interactionKey;
        return this;
    }

    private static BlockRayTraceResult rayTraceResultFrom(GooSplat gooSplat)
    {
        return new BlockRayTraceResult(gooSplat.getPositionVec(), gooSplat.sideWeLiveOn(), gooSplat.blockAttached(), false);
    }

    public BlockState blockState()
    {
        return this.blockState;
    }

    public BlockRayTraceResult hitResult()
    {
        return this.hitResult;
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

    public Direction sideHit()
    {
        return this.sideHit;
    }

    public IFluidHandler fluidHandler()
    {
        return this.fluidHandler;
    }

    public Fluid fluid() { return this.fluid; }

    public String interactionKey() { return this.interactionKey; }

    public GooSplat splat()
    {
        return this.splat;
    }
}
