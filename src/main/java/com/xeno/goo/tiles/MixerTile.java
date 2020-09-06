package com.xeno.goo.tiles;

import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.setup.Registry;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class MixerTile extends TileEntity implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{
    NonNullList<FluidStack> goo = NonNullList.withSize(3, FluidStack.EMPTY);

    public MixerTile()
    {
        super(Registry.MIXER_TILE.get());
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids)
    {

    }

    @Override
    public void tick()
    {

    }
}
