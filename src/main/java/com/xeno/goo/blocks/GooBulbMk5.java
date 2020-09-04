package com.xeno.goo.blocks;

import com.xeno.goo.tiles.GooBulbTileMk4;
import com.xeno.goo.tiles.GooBulbTileMk5;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

public class GooBulbMk5 extends GooBulbAbstraction
{
    public GooBulbMk5()
    {
        super();
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new GooBulbTileMk5();
    }
}
