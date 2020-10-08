package com.xeno.goo.blocks;

import com.xeno.goo.items.ItemsRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import static net.minecraft.state.properties.BlockStateProperties.FACING;

public class Drain extends Block
{
    public Drain()
    {
        super(Properties.create(Material.IRON)
                .sound(SoundType.METAL)
                .hardnessAndResistance(1.0f)
        );
    }

    private static VoxelShape CACHED_SHAPE = VoxelShapes.create(0d, 12d, 0d, 16d, 16d, 16d);

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader reader, BlockPos pos)
    {
        return CACHED_SHAPE;
    }
//
//    @Override
//    public boolean hasTileEntity(BlockState state)
//    {
//        return true;
//    }
//
//    @Override
//    public TileEntity createTileEntity(BlockState state, IBlockReader world)
//    {
//        return new DrainTile();
//    }

    @Override
    public void onBlockHarvested(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isRemote) {
            if (player.isCreative()) {
                return;
            }
            ItemStack stack = new ItemStack(ItemsRegistry.Drain.get());
            ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            itemEntity.setDefaultPickupDelay();
            world.addEntity(itemEntity);
        }
        super.onBlockHarvested(world, pos, state, player);
    }
}
