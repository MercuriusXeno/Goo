package com.xeno.goo.blocks;

import com.xeno.goo.client.render.PumpRenderMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;

import static net.minecraft.state.properties.BlockStateProperties.FACING;

public class RadiantLight extends Block {
    VoxelShape[] shapes;
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return getCollisionShape(state, worldIn, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader reader, BlockPos pos)
    {
        int index = getIndexFromState(state.get(FACING));
        return shapes[index];
    }

    private int getIndexFromState(Direction direction)
    {
        switch(direction) {
            case UP:
                return 0;
            case DOWN:
                return 1;
            case EAST:
                return 2;
            case WEST:
                return 3;
            case SOUTH:
                return 4;
            case NORTH:
                return 5;
        }
        return 0;
    }

    private VoxelShape[] makeShapes()
    {
        // up
        Vector3d us = new Vector3d(3d, 0d, 3d);
        Vector3d ue = new Vector3d(13d, 2d, 13d);
        // down
        Vector3d ds = new Vector3d(3d, 14d, 3d);
        Vector3d de = new Vector3d(13d, 16d, 13d);
        // west
        Vector3d ws = new Vector3d(0d, 3d, 3d);
        Vector3d we = new Vector3d(2d, 13d, 13d);
        // east
        Vector3d es = new Vector3d(14d, 3d, 3d);
        Vector3d ee = new Vector3d(16d, 13d, 13d);
        // south
        Vector3d ss = new Vector3d(3d, 3d, 0d);
        Vector3d se = new Vector3d(13d, 13d, 2d);
        // north
        Vector3d ns = new Vector3d(3d, 3d, 14d);
        Vector3d ne = new Vector3d(13d, 13d, 16d);
        return new VoxelShape[] {
                combo(us, ue),
                combo(ds, de),
                combo(ws, we),
                combo(es, ee),
                combo(ss, se),
                combo(ns, ne)
        };
    }

    private VoxelShape combo(Vector3d s, Vector3d e)
    {
        return Block.makeCuboidShape(s.x, s.y, s.z, e.x, e.y, e.z);
    }

    public RadiantLight() {
        super(Properties.create(Material.GLASS)
                .sound(SoundType.GLASS)
                .hardnessAndResistance(0.5f)
                .notSolid()
                .setLightLevel((bs) -> 15)
        );
        setDefaultState(this.getDefaultState()
                .with(BlockStateProperties.FACING, Direction.NORTH)
        );
        shapes = makeShapes();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(FACING, context.getFace());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
