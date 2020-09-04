package com.xeno.goo.blocks;

import com.xeno.goo.tiles.GooBulbTileAbstraction;
import com.xeno.goo.tiles.GooBulbTileMk2;
import com.xeno.goo.tiles.GooBulbTileMk3;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

public class GooBulbMk3 extends GooBulbAbstraction
{
    public GooBulbMk3()
    {
        super();
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new GooBulbTileMk3();
    }
}
