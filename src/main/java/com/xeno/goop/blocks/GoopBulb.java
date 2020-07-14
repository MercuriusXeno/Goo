package com.xeno.goop.blocks;

import com.xeno.goop.tiles.GoopBulbTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

import javax.annotation.Nullable;

public class GoopBulb extends Block {
    public GoopBulb() {
        super(Properties.create(Material.GLASS)
            .sound(SoundType.GLASS)
            .hardnessAndResistance(1.0f)
            .notSolid()
        );
    }


    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }


    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new GoopBulbTile();
    }
}
