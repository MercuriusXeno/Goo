package com.xeno.goo.tiles;

import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;

public class LobberTile extends TileEntity implements ITickableTileEntity
{
    public LobberTile()
    {
        super(Registry.LOBBER_TILE.get());
    }

    @Override
    public void tick()
    {
        if (world == null) {
            return;
        }

        if (world.isRemote) {
            return;
        }

        tryPushingFluid();
    }

    private void tryPushingFluid()
    {
        // TODO
    }

    public Direction facing()
    {
        return this.getBlockState().get(BlockStateProperties.FACING);
    }

    @Override
    public CompoundNBT write(CompoundNBT tag)
    {
        return super.write(tag);
    }

    @Override
    public void read(BlockState state, CompoundNBT tag)
    {
        super.read(state, tag);
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

}
