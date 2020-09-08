package com.xeno.goo.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public abstract class FluidHandlerHelper
{
    public static IFluidHandler capability(TileEntity tile, Direction dir)
    {
        if (tile == null) {
            return null;
        }
        LazyOptional<IFluidHandler> lazyCap = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, dir);
        if (lazyCap.isPresent()) {
            return lazyCap.orElseThrow(() -> new RuntimeException("Tried to get a fluid capability that wasn't there, oh no."));
        }

        return null;
    }

    public static TileEntity tileAtDirection(TileEntity e, Direction d)
    {
        return tileAtDirection(e.getWorld(), e.getPos(), d);
    }

    private static TileEntity tileAtDirection(World world, BlockPos pos, Direction d)
    {
        if (world == null) {
            return null;
        }
        TileEntity t = world.getTileEntity(pos.offset(d));
        return t;
    }
}
