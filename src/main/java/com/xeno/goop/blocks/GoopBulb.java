package com.xeno.goop.blocks;

import com.xeno.goop.tiles.GoopBulbTile;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class GoopBulb extends Block {
    public GoopBulb() {
        super(Properties.create(Material.ROCK)
            .sound(SoundType.STONE)
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

    @Override
    public void onBlockHarvested(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof GoopBulbTile) {
            GoopBulbTile goopBulb = (GoopBulbTile)te;
            if (!world.isRemote) {
                ItemStack stack = goopBulb.getBulbStack();
                ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                itemEntity.setDefaultPickupDelay();
                world.addEntity(itemEntity);
            }
        }

        super.onBlockHarvested(world, pos, state, player);
    }
}
