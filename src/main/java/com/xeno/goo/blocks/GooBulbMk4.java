package com.xeno.goo.blocks;

import com.xeno.goo.tiles.GooBulbTileMk3;
import com.xeno.goo.tiles.GooBulbTileMk4;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

public class GooBulbMk4 extends GooBulbAbstraction
{
    public GooBulbMk4()
    {
        super();
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new GooBulbTileMk4();
    }
}
