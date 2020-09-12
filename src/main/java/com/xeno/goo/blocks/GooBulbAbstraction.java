package com.xeno.goo.blocks;

import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTile;
import com.xeno.goo.tiles.GooBulbTileAbstraction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public abstract class GooBulbAbstraction extends Block
{
    public GooBulbAbstraction()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        int holding = EnchantmentHelper.getEnchantmentLevel(Registry.HOLDING.get(), stack);
        GooBulbTile tile = (GooBulbTile)worldIn.getTileEntity(pos);
        tile.enchantHolding(holding);
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @Override
    public abstract TileEntity createTileEntity(BlockState state, IBlockReader world);

    @Override
    public void onBlockHarvested(World world, BlockPos pos, BlockState state, PlayerEntity player)
    {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof GooBulbTileAbstraction) {
            GooBulbTileAbstraction gooBulb = (GooBulbTileAbstraction) te;
            if (!world.isRemote) {
                ItemStack stack = gooBulb.getBulbStack(this);
                ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                itemEntity.setDefaultPickupDelay();
                world.addEntity(itemEntity);
            }
        }

        super.onBlockHarvested(world, pos, state, player);
    }
}
