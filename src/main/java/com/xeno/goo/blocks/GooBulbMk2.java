package com.xeno.goo.blocks;

import com.xeno.goo.tiles.GooBulbTileMk2;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

public class GooBulbMk2 extends GooBulbAbstraction
{
    public GooBulbMk2()
    {
        super();
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new GooBulbTileMk2();
    }
}
