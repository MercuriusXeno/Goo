package com.xeno.goo.tiles;

import com.xeno.goo.network.BulbVerticalFillPacket;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.setup.Registry;
import net.minecraft.fluid.Fluid;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class GooPumpTile extends TileEntity implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver, BulbVerticalFillPacket.IVerticalFillReceiver
{
    private FluidStack goo;
    private float intensity;

    public GooPumpTile()
    {
        super(Registry.GOO_PUMP_TILE.get());
        goo = FluidStack.EMPTY;
    }

    @Override
    public void updateVerticalFill(Fluid f, float intensity)
    {
        this.intensity = intensity;
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids)
    {
        // we only have one slot, so hopefully this is all there is to this packet or we ignore the rest.
        if (fluids.size() > 0) {
            this.goo = fluids.get(0);
        }
    }

    @Override
    public void tick()
    {

    }

    public float verticalFillIntensity()
    {
        return intensity;
    }

    public FluidStack goo() {
        return this.goo;
    }

    public boolean isVerticallyFilled()
    {
        return this.intensity > 0;
    }

    public FluidStack verticalFillFluid()
    {
        return goo();
    }
}
