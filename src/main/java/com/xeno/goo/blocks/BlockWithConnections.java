package com.xeno.goo.blocks;

import com.xeno.goo.tiles.FluidHandlerInteractionAbstraction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class BlockWithConnections extends Block
{
    public BlockWithConnections(Properties properties)
    {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos neighbor, boolean isMoving) {
        for (Direction d : relevantConnectionDirections(state)) {
            if (pos.offset(d).equals(neighbor)) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof FluidHandlerInteractionAbstraction) {
                    ((FluidHandlerInteractionAbstraction) te).clearCachedReference(d);
                }
            }
        }
    }

    protected abstract Direction[] relevantConnectionDirections(BlockState state);
}
